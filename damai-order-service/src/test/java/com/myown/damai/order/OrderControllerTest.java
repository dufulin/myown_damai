package com.myown.damai.order;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.order.client.ProgramInventoryClient;
import com.myown.damai.order.client.ProgramOrderSnapshot;
import com.myown.damai.order.client.ProgramTicketPriceSnapshot;
import com.myown.damai.order.client.TicketUserClient;
import com.myown.damai.order.dao.OrderAsyncMessageDao;
import com.myown.damai.order.entity.OrderAsyncMessage;
import com.myown.damai.order.entity.OrderAsyncMessageStatus;
import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Verifies order creation, query, cancellation, and timeout cancellation APIs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "damai.order.timeout-minutes=0",
        "damai.order.timeout-scan-enabled=false"
})
class OrderControllerTest {

    private static final String USER_ID_HEADER = "X-Damai-User-Id";
    private static final String USER_ROLE_HEADER = "X-Damai-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderAsyncMessageDao orderAsyncMessageDao;

    @MockBean
    private ProgramInventoryClient programInventoryClient;

    @MockBean
    private TicketUserClient ticketUserClient;

    /**
     * Returns a stable database-authoritative snapshot from the mocked program service.
     */
    @BeforeEach
    void setUpProgramSnapshot() {
        when(programInventoryClient.getOrderSnapshot(anyLong(), anyLong(), anyList()))
                .thenReturn(new ProgramOrderSnapshot(
                        20001L,
                        60001L,
                        "Database Concert",
                        "Database Arena",
                        "https://database.example/poster.png",
                        Instant.parse("2026-08-01T12:00:00Z"),
                        1,
                        List.of(new ProgramTicketPriceSnapshot(50001L, new BigDecimal("680")))
                ));
    }

    /**
     * Verifies creating, querying, listing, and manually canceling one unpaid order.
     */
    @Test
    void createQueryListAndCancelOrder() throws Exception {
        Long orderNumber = createOrder(10001L);

        mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber)
                        .header(USER_ID_HEADER, "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.data.orderStatus").value(1))
                .andExpect(jsonPath("$.data.programTitle").value("Database Concert"))
                .andExpect(jsonPath("$.data.programPlace").value("Database Arena"))
                .andExpect(jsonPath("$.data.programItemPicture").value("https://database.example/poster.png"))
                .andExpect(jsonPath("$.data.orderPrice").value(1360))
                .andExpect(jsonPath("$.data.ticketUsers[0].orderPrice").value(680))
                .andExpect(jsonPath("$.data.ticketUsers", hasSize(2)));

        mockMvc.perform(get("/api/orders")
                        .header(USER_ID_HEADER, "10001")
                        .param("pageNumber", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderNumber").value(orderNumber));

        mockMvc.perform(get("/api/orders/cursor")
                        .header(USER_ID_HEADER, "10001")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders", hasSize(1)))
                .andExpect(jsonPath("$.data.orders[0].orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.data.hasMore").value(false));

        mockMvc.perform(get("/api/orders")
                        .header(USER_ID_HEADER, "10001")
                        .param("pageNumber", "101")
                        .param("pageSize", "100"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders/{orderNumber}/cancel", orderNumber)
                        .header(USER_ID_HEADER, "10001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "user canceled"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(2))
                .andExpect(jsonPath("$.data.orderStatusName").value("CANCELED"));
    }

    /**
     * Verifies expired unpaid orders are canceled by the timeout cancel API.
     */
    @Test
    void timeoutCancelOrder() throws Exception {
        Long orderNumber = createOrder(10002L);

        mockMvc.perform(post("/api/orders/timeout-cancel")
                        .header(USER_ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canceledCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber)
                        .header(USER_ID_HEADER, "10002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(5))
                .andExpect(jsonPath("$.data.orderStatusName").value("TIMEOUT"))
                .andExpect(jsonPath("$.data.ticketUsers[0].orderStatus").value(5));
    }

    /**
     * Verifies an unpaid order can be marked as paid after payment confirmation.
     */
    @Test
    void markOrderPaid() throws Exception {
        Long orderNumber = createOrder(10003L);

        mockMvc.perform(post("/api/orders/{orderNumber}/paid", orderNumber)
                        .header(USER_ROLE_HEADER, "SYSTEM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tradeNumber": "2026061622001000000000000001",
                                  "payAmount": 1360,
                                  "payTime": "2026-06-16T12:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(3))
                .andExpect(jsonPath("$.data.orderStatusName").value("PAID"))
                .andExpect(jsonPath("$.data.ticketUsers[0].orderStatus").value(3));
    }

    /**
     * Verifies a timeout order cannot be overwritten by a later payment confirmation.
     */
    @Test
    void timeoutOrderCannotBePaid() throws Exception {
        Long orderNumber = createOrder(10006L);

        mockMvc.perform(post("/api/orders/timeout-cancel")
                        .header(USER_ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{orderNumber}/paid", orderNumber)
                        .header(USER_ROLE_HEADER, "SYSTEM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tradeNumber": "2026061622001000000000000002",
                                  "payAmount": 1360,
                                  "payTime": "2026-06-16T12:00:00Z"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    /**
     * Verifies repeated user-program order creation returns the existing order number.
     */
    @Test
    void createOrderIsIdempotentByUserAndProgram() throws Exception {
        Long firstOrderNumber = createOrder(10004L);
        Long secondOrderNumber = createOrder(10004L);

        org.assertj.core.api.Assertions.assertThat(secondOrderNumber).isEqualTo(firstOrderNumber);

        mockMvc.perform(get("/api/orders")
                        .header(USER_ID_HEADER, "10004")
                        .param("pageNumber", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderNumber").value(firstOrderNumber));
    }

    /**
     * Verifies async message status can be tracked by order number.
     */
    @Test
    void getAsyncMessageStatus() throws Exception {
        Long orderNumber = 99001001L;
        OrderAsyncMessage message = buildAsyncMessage(orderNumber);
        orderAsyncMessageDao.saveMessage(message);
        orderAsyncMessageDao.markSent(message.messageKey, message.topic);

        mockMvc.perform(get("/api/orders/{orderNumber}/async-message", orderNumber)
                        .header(USER_ID_HEADER, "10005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageKey").value(message.messageKey))
                .andExpect(jsonPath("$.data.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.data.messageStatus").value(OrderAsyncMessageStatus.SENT.code()))
                .andExpect(jsonPath("$.data.messageStatusName").value("SENT"));
    }

    /**
     * Creates one test order and returns its order number.
     */
    private Long createOrder(Long userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header(USER_ID_HEADER, String.valueOf(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "programId": 20001,
                                  "showTimeId": 60001,
                                  "ticketCategoryId": 50001,
                                  "ticketUserIds": [30001, 30002],
                                  "programTitle": "Tampered Title",
                                  "programPlace": "Tampered Arena",
                                  "programItemPicture": "https://attacker.example/poster.png",
                                  "orderPrice": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderNumber", notNullValue()))
                .andExpect(jsonPath("$.data.orderPrice").value(1360))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("data").path("orderNumber").asLong();
    }

    /**
     * Builds one async message entity for status tracking tests.
     */
    private OrderAsyncMessage buildAsyncMessage(Long orderNumber) {
        Instant now = Instant.now();
        OrderAsyncMessage message = new OrderAsyncMessage();
        message.messageKey = "order-create:" + orderNumber;
        message.orderNumber = orderNumber;
        message.userId = 10005L;
        message.programId = 20001L;
        message.topic = "damai-order-create-test";
        message.retryCount = 0;
        message.maxRetryCount = 3;
        message.messageStatus = OrderAsyncMessageStatus.INIT.code();
        message.payload = "{}";
        message.createdAt = now;
        message.updatedAt = now;
        message.status = 1;
        return message;
    }
}
