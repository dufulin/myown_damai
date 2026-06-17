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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Verifies creating, querying, listing, and manually canceling one unpaid order.
     */
    @Test
    void createQueryListAndCancelOrder() throws Exception {
        Long orderNumber = createOrder(10001L);

        mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.data.orderStatus").value(1))
                .andExpect(jsonPath("$.data.ticketUsers", hasSize(2)));

        mockMvc.perform(get("/api/orders")
                        .param("userId", "10001")
                        .param("pageNumber", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderNumber").value(orderNumber));

        mockMvc.perform(post("/api/orders/{orderNumber}/cancel", orderNumber)
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

        mockMvc.perform(post("/api/orders/timeout-cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canceledCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/orders/{orderNumber}", orderNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value(2))
                .andExpect(jsonPath("$.data.ticketUsers[0].orderStatus").value(2));
    }

    /**
     * Verifies an unpaid order can be marked as paid after payment confirmation.
     */
    @Test
    void markOrderPaid() throws Exception {
        Long orderNumber = createOrder(10003L);

        mockMvc.perform(post("/api/orders/{orderNumber}/paid", orderNumber)
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
     * Verifies repeated user-program order creation returns the existing order number.
     */
    @Test
    void createOrderIsIdempotentByUserAndProgram() throws Exception {
        Long firstOrderNumber = createOrder(10004L);
        Long secondOrderNumber = createOrder(10004L);

        org.assertj.core.api.Assertions.assertThat(secondOrderNumber).isEqualTo(firstOrderNumber);

        mockMvc.perform(get("/api/orders")
                        .param("userId", "10004")
                        .param("pageNumber", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].orderNumber").value(firstOrderNumber));
    }

    /**
     * Creates one test order and returns its order number.
     */
    private Long createOrder(Long userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "programId": 20001,
                                  "programItemPicture": "https://example.com/poster.png",
                                  "userId": %d,
                                  "programTitle": "Demo Concert",
                                  "programPlace": "Demo Arena",
                                  "programShowTime": "2026-08-01T12:00:00Z",
                                  "programPermitChooseSeat": 1,
                                  "distributionMode": "E-ticket",
                                  "takeTicketMode": "Mobile",
                                  "payOrderType": 1,
                                  "ticketUsers": [
                                    {
                                      "ticketUserId": 30001,
                                      "seatId": 40001,
                                      "seatInfo": "A-1",
                                      "ticketCategoryId": 50001,
                                      "orderPrice": 680
                                    },
                                    {
                                      "ticketUserId": 30002,
                                      "seatId": 40002,
                                      "seatInfo": "A-2",
                                      "ticketCategoryId": 50001,
                                      "orderPrice": 680
                                    }
                                  ]
                                }
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderNumber", notNullValue()))
                .andExpect(jsonPath("$.data.orderPrice").value(1360))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("data").path("orderNumber").asLong();
    }
}
