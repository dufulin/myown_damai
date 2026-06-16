package com.myown.damai.order.dto;

import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Exposes order master data with its ticket-user detail rows.
 */
public record OrderResponse(
        Long id,
        Long orderNumber,
        Long programId,
        String programItemPicture,
        Long userId,
        String programTitle,
        String programPlace,
        Instant programShowTime,
        Integer programPermitChooseSeat,
        String distributionMode,
        String takeTicketMode,
        BigDecimal orderPrice,
        Integer payOrderType,
        Integer orderStatus,
        String orderStatusName,
        Instant expireTime,
        Instant createOrderTime,
        Instant cancelOrderTime,
        Instant payOrderTime,
        List<OrderTicketUserResponse> ticketUsers
) {

    /**
     * Builds an order response from entity data.
     */
    public static OrderResponse of(Order order, List<OrderTicketUserResponse> ticketUsers) {
        return new OrderResponse(
                order.id,
                order.orderNumber,
                order.programId,
                order.programItemPicture,
                order.userId,
                order.programTitle,
                order.programPlace,
                order.programShowTime,
                order.programPermitChooseSeat,
                order.distributionMode,
                order.takeTicketMode,
                order.orderPrice,
                order.payOrderType,
                order.orderStatus,
                OrderStatus.fromCode(order.orderStatus).name(),
                order.expireTime,
                order.createOrderTime,
                order.cancelOrderTime,
                order.payOrderTime,
                ticketUsers
        );
    }
}
