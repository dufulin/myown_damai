package com.myown.damai.pay.controller;

import com.myown.damai.common.auth.UserRole;
import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.observability.TraceContext;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import com.myown.damai.pay.dto.PagePayRequest;
import com.myown.damai.pay.dto.PagePayResponse;
import com.myown.damai.pay.dto.PayEventCompensateResponse;
import com.myown.damai.pay.dto.PayOrderEventResponse;
import com.myown.damai.pay.service.PayOrderEventService;
import com.myown.damai.pay.service.PayService;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final PayOrderEventService payOrderEventService;

    /**
     * Creates the controller with payment business operations.
     */
    public PayController(PayService payService, PayOrderEventService payOrderEventService) {
        this.payService = payService;
        this.payOrderEventService = payOrderEventService;
    }

    /**
     * Creates a mock Alipay payment and records an asynchronous order notification event.
     */
    @PostMapping("/alipay/page-pay")
    public ApiResponse<PagePayResponse> createAlipayPagePay(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @Valid @RequestBody PagePayRequest request
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        TraceContext.putUserId(authenticatedUserId);
        TraceContext.putOrderNumber(request.orderNumber());
        LOGGER.info("mock alipay pay request received, orderNumber={}, userId={}", request.orderNumber(), authenticatedUserId);
        PagePayResponse response = payService.createAlipayPagePay(request, authenticatedUserId);
        LOGGER.info("mock alipay pay request succeeded, orderNumber={}, payBillId={}", request.orderNumber(), response.payBillId());
        return ApiResponse.success(response);
    }

    /**
     * Handles Alipay asynchronous payment notifications.
     */
    @PostMapping(value = "/alipay/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String handleAlipayNotify(@RequestParam Map<String, String> notifyParams) {
        TraceContext.putOrderNumber(notifyParams.get("out_trade_no"));
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

    /**
     * Manually compensates due payment events.
     */
    @PostMapping("/events/compensate")
    public ApiResponse<PayEventCompensateResponse> compensatePayEvents(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        LOGGER.info("pay event compensate request received");
        int processedCount = payOrderEventService.compensateDueEvents();
        LOGGER.info("pay event compensate request succeeded, processedCount={}", processedCount);
        return ApiResponse.success(new PayEventCompensateResponse(processedCount));
    }

    /**
     * Manually retries one payment event by event key.
     */
    @PostMapping("/events/{eventKey}/retry")
    public ApiResponse<PayEventCompensateResponse> retryPayEvent(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable String eventKey
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        LOGGER.info("pay event retry request received, eventKey={}", eventKey);
        int processedCount = payOrderEventService.manualRetry(eventKey);
        LOGGER.info("pay event retry request succeeded, eventKey={}, processedCount={}", eventKey, processedCount);
        return ApiResponse.success(new PayEventCompensateResponse(processedCount));
    }

    /**
     * Gets one payment event status by event key.
     */
    @GetMapping("/events/{eventKey}")
    public ApiResponse<PayOrderEventResponse> getPayEvent(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable String eventKey
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        LOGGER.info("pay event detail request received, eventKey={}", eventKey);
        PayOrderEventResponse response = payOrderEventService.getEvent(eventKey);
        LOGGER.info("pay event detail request succeeded, eventKey={}, status={}", eventKey, response.eventStatusName());
        return ApiResponse.success(response);
    }
}
