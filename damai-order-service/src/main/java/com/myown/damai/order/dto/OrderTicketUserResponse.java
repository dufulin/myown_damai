package com.myown.damai.order.dto;

import com.myown.damai.order.entity.OrderStatus;
import com.myown.damai.order.entity.OrderTicketUser;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Exposes one ticket-user item in an order response.
 */
public record OrderTicketUserResponse(
        Long id,
        Long ticketUserId,
        Long seatId,
        String seatInfo,
        Long ticketCategoryId,
        BigDecimal orderPrice,
        BigDecimal payOrderPrice,
        Integer payOrderType,
        Integer orderStatus,
        String orderStatusName,
        Instant createOrderTime,
        Instant cancelOrderTime,
        Instant payOrderTime
) {

    /**
     * Builds a ticket-user response from an entity.
     */
    public static OrderTicketUserResponse from(OrderTicketUser item) {
        return new OrderTicketUserResponse(
                item.id,
                item.ticketUserId,
                item.seatId,
                item.seatInfo,
                item.ticketCategoryId,
                item.orderPrice,
                item.payOrderPrice,
                item.payOrderType,
                item.orderStatus,
                OrderStatus.fromCode(item.orderStatus).name(),
                item.createOrderTime,
                item.cancelOrderTime,
                item.payOrderTime
        );
    }
}
