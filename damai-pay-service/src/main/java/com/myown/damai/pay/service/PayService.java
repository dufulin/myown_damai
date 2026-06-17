package com.myown.damai.pay.service;

import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.pay.client.OrderClient;
import com.myown.damai.pay.dao.PayBillDao;
import com.myown.damai.pay.dto.OrderPayRequest;
import com.myown.damai.pay.dto.OrderSnapshot;
import com.myown.damai.pay.dto.PagePayRequest;
import com.myown.damai.pay.dto.PagePayResponse;
import com.myown.damai.pay.entity.PayBill;
import com.myown.damai.pay.entity.PayBillStatus;
import com.myown.damai.pay.integration.AlipayPagePayClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Handles Alipay page payment creation and asynchronous payment confirmation.
 */
@Service
public class PayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayService.class);
    private static final DateTimeFormatter ALIPAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final AtomicInteger PAY_SEQUENCE = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));

    private final PayBillDao payBillDao;
    private final OrderClient orderClient;
    private final AlipayPagePayClient alipayPagePayClient;

    /**
     * Creates the payment service with persistence, order, and Alipay integrations.
     */
    public PayService(PayBillDao payBillDao, OrderClient orderClient, AlipayPagePayClient alipayPagePayClient) {
        this.payBillDao = payBillDao;
        this.orderClient = orderClient;
        this.alipayPagePayClient = alipayPagePayClient;
    }

    /**
     * Creates or reuses a payment bill and directly marks the order as paid for local development.
     */
    @Transactional
    public PagePayResponse createAlipayPagePay(PagePayRequest request) {
        OrderSnapshot order = orderClient.getOrder(request.orderNumber());
        validateOrderPayable(order, request.userId());
        PayBill bill = payBillDao.findByOutOrderNo(String.valueOf(order.orderNumber()))
                .orElseGet(() -> payBillDao.save(buildPayBill(order)));
        String tradeNumber = "MOCK" + bill.payNumber;
        Instant payTime = Instant.now();
        payBillDao.markPaid(bill.outOrderNo, tradeNumber, bill.payAmount, payTime, PayBillStatus.PAID.code);
        orderClient.markOrderPaid(order.orderNumber(), new OrderPayRequest(tradeNumber, bill.payAmount, payTime));
        bill.tradeNumber = tradeNumber;
        bill.payTime = payTime;
        bill.payBillStatus = PayBillStatus.PAID.code;
        bill.updatedAt = payTime;
        String payForm = "<div>模拟支付成功，订单已更新为已支付。</div>";
        LOGGER.info("mock alipay page pay succeeded, orderNumber={}, payBillId={}", order.orderNumber(), bill.id);
        return PagePayResponse.of(bill, payForm);
    }

    /**
     * Handles an Alipay asynchronous notify and updates payment and order statuses.
     */
    @Transactional
    public String handleAlipayNotify(Map<String, String> notifyParams) {
        if (!alipayPagePayClient.verifyNotify(notifyParams)) {
            LOGGER.warn("alipay notify rejected because signature verification failed");
            return "failure";
        }
        String tradeStatus = notifyParams.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            LOGGER.info("alipay notify ignored because trade status is not paid, tradeStatus={}", tradeStatus);
            return "success";
        }

        String outTradeNo = requiredNotifyValue(notifyParams, "out_trade_no");
        String tradeNo = requiredNotifyValue(notifyParams, "trade_no");
        BigDecimal totalAmount = new BigDecimal(requiredNotifyValue(notifyParams, "total_amount"));
        Instant payTime = parseAlipayPayTime(notifyParams.get("gmt_payment"));
        PayBill bill = payBillDao.findByOutOrderNo(outTradeNo)
                .orElseThrow(() -> new BusinessException("PAY_BILL_NOT_FOUND", "pay bill not found", HttpStatus.NOT_FOUND));
        payBillDao.markPaid(outTradeNo, tradeNo, totalAmount, payTime, PayBillStatus.PAID.code);
        orderClient.markOrderPaid(Long.valueOf(outTradeNo), new OrderPayRequest(tradeNo, totalAmount, payTime));
        LOGGER.info("alipay notify handled, outTradeNo={}, tradeNo={}, payBillId={}", outTradeNo, tradeNo, bill.id);
        return "success";
    }

    /**
     * Validates that the caller is paying its own unpaid order.
     */
    private void validateOrderPayable(OrderSnapshot order, Long userId) {
        if (!order.userId().equals(userId)) {
            throw new BusinessException("ORDER_USER_MISMATCH", "order does not belong to current user", HttpStatus.FORBIDDEN);
        }
        if (Integer.valueOf(3).equals(order.orderStatus())) {
            return;
        }
        if (!Integer.valueOf(1).equals(order.orderStatus())) {
            throw new BusinessException("ORDER_STATUS_NOT_PAYABLE", "only unpaid orders can be paid", HttpStatus.CONFLICT);
        }
    }

    /**
     * Builds a new payment bill from order data.
     */
    private PayBill buildPayBill(OrderSnapshot order) {
        Instant now = Instant.now();
        PayBill bill = new PayBill();
        bill.payNumber = nextPayNumber();
        bill.outOrderNo = String.valueOf(order.orderNumber());
        bill.payChannel = "ALIPAY";
        bill.payScene = "PAGE_PC";
        bill.subject = StringUtils.hasText(order.programTitle()) ? order.programTitle() : "Damai order " + order.orderNumber();
        bill.payAmount = order.orderPrice();
        bill.payBillType = 1;
        bill.payBillStatus = PayBillStatus.UNPAID.code;
        bill.createdAt = now;
        bill.updatedAt = now;
        bill.status = 1;
        return bill;
    }

    /**
     * Generates a time-sortable payment number.
     */
    private String nextPayNumber() {
        int sequence = PAY_SEQUENCE.updateAndGet(value -> value >= 999 ? 0 : value + 1);
        return "PAY" + Instant.now().toEpochMilli() + String.format("%03d", sequence);
    }

    /**
     * Reads a required value from Alipay notify parameters.
     */
    private String requiredNotifyValue(Map<String, String> notifyParams, String key) {
        String value = notifyParams.get(key);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("ALIPAY_NOTIFY_INVALID", key + " is required", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    /**
     * Parses Alipay payment time using China timezone.
     */
    private Instant parseAlipayPayTime(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.now();
        }
        return LocalDateTime.parse(value, ALIPAY_TIME_FORMATTER).atZone(CHINA_ZONE).toInstant();
    }
}
