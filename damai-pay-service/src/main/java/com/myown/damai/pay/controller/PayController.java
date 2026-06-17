package com.myown.damai.pay.controller;

import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.pay.dto.PagePayRequest;
import com.myown.damai.pay.dto.PagePayResponse;
import com.myown.damai.pay.service.PayService;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides Alipay payment creation and asynchronous notify APIs.
 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayController.class);

    private final PayService payService;

    /**
     * Creates the controller with payment business operations.
     */
    public PayController(PayService payService) {
        this.payService = payService;
    }

    /**
     * Creates a mock Alipay payment and marks one order as paid.
     */
    @PostMapping("/alipay/page-pay")
    public ApiResponse<PagePayResponse> createAlipayPagePay(@Valid @RequestBody PagePayRequest request) {
        LOGGER.info("mock alipay pay request received, orderNumber={}, userId={}", request.orderNumber(), request.userId());
        PagePayResponse response = payService.createAlipayPagePay(request);
        LOGGER.info("mock alipay pay request succeeded, orderNumber={}, payBillId={}", request.orderNumber(), response.payBillId());
        return ApiResponse.success(response);
    }

    /**
     * Handles Alipay asynchronous payment notifications.
     */
    @PostMapping(value = "/alipay/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String handleAlipayNotify(@RequestParam Map<String, String> notifyParams) {
        LOGGER.info("alipay notify request received, outTradeNo={}, tradeStatus={}", notifyParams.get("out_trade_no"), notifyParams.get("trade_status"));
        try {
            String result = payService.handleAlipayNotify(notifyParams);
            LOGGER.info("alipay notify request handled, result={}", result);
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warn("alipay notify request failed", exception);
            return "failure";
        }
    }
}
