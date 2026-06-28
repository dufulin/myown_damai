package com.myown.damai.pay;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myown.damai.pay.client.OrderClient;
import com.myown.damai.pay.dto.OrderPayRequest;
import com.myown.damai.pay.dto.OrderSnapshot;
import com.myown.damai.pay.entity.PayBillStatus;
import com.myown.damai.pay.mapper.PayBillMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies Alipay payment creation and notification workflows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PayControllerTest {

    private static final String USER_ID_HEADER = "X-Damai-User-Id";
    private static final String USER_ROLE_HEADER = "X-Damai-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayBillMapper payBillMapper;

    @MockBean
    private OrderClient orderClient;

    /**
     * Verifies page-pay creation records a local event and manual compensation marks the order paid.
     */
    @Test
    void createPagePayAndHandleNotify() throws Exception {
        Long orderNumber = 90010001L;
        String eventKey = "pay-success:" + orderNumber;
        when(orderClient.getOrder(orderNumber, 70001L))
                .thenReturn(new OrderSnapshot(orderNumber, 70001L, "Demo Concert", new BigDecimal("680.00"), 1, null));
        when(orderClient.markOrderPaid(eq(orderNumber), any(OrderPayRequest.class)))
                .thenReturn(new OrderSnapshot(orderNumber, 70001L, "Demo Concert", new BigDecimal("680.00"), 3, Instant.now()));

        mockMvc.perform(post("/api/pay/alipay/page-pay")
                        .header(USER_ID_HEADER, "70001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNumber": 90010001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payBillId").exists())
                .andExpect(jsonPath("$.data.outOrderNo").value("90010001"))
                .andExpect(jsonPath("$.data.payBillStatus").value(2));

        mockMvc.perform(get("/api/pay/events/{eventKey}", eventKey)
                        .header(USER_ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventKey").value(eventKey))
                .andExpect(jsonPath("$.data.eventStatus").value(1))
                .andExpect(jsonPath("$.data.eventStatusName").value("INIT"));

        mockMvc.perform(post("/api/pay/events/compensate")
                        .header(USER_ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processedCount").value(1));

        verify(orderClient).markOrderPaid(eq(orderNumber), any(OrderPayRequest.class));

        mockMvc.perform(get("/api/pay/events/{eventKey}", eventKey)
                        .header(USER_ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventStatus").value(3))
                .andExpect(jsonPath("$.data.eventStatusName").value("SUCCEEDED"));

        mockMvc.perform(post("/api/pay/alipay/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("out_trade_no=90010001&trade_no=2026061622001000000000000001&trade_status=TRADE_SUCCESS&total_amount=680.00&gmt_payment=2026-06-16%2012:00:00"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));

        org.assertj.core.api.Assertions.assertThat(payBillMapper.selectByOutOrderNo("90010001").payBillStatus)
                .isEqualTo(PayBillStatus.PAID.code);
    }
}
