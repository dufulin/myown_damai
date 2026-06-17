package com.myown.damai.pay;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PayBillMapper payBillMapper;

    @MockBean
    private OrderClient orderClient;

    /**
     * Verifies page-pay creation directly marks the order as paid and notify handling remains compatible.
     */
    @Test
    void createPagePayAndHandleNotify() throws Exception {
        Long orderNumber = 90010001L;
        when(orderClient.getOrder(orderNumber))
                .thenReturn(new OrderSnapshot(orderNumber, 70001L, "Demo Concert", new BigDecimal("680.00"), 1, null));
        when(orderClient.markOrderPaid(eq(orderNumber), any(OrderPayRequest.class)))
                .thenReturn(new OrderSnapshot(orderNumber, 70001L, "Demo Concert", new BigDecimal("680.00"), 3, Instant.now()));

        mockMvc.perform(post("/api/pay/alipay/page-pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNumber": 90010001,
                                  "userId": 70001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payBillId").exists())
                .andExpect(jsonPath("$.data.outOrderNo").value("90010001"))
                .andExpect(jsonPath("$.data.payBillStatus").value(2));

        mockMvc.perform(post("/api/pay/alipay/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("out_trade_no=90010001&trade_no=2026061622001000000000000001&trade_status=TRADE_SUCCESS&total_amount=680.00&gmt_payment=2026-06-16%2012:00:00"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));

        org.assertj.core.api.Assertions.assertThat(payBillMapper.selectByOutOrderNo("90010001").payBillStatus)
                .isEqualTo(PayBillStatus.PAID.code);
    }
}
