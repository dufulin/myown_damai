package com.myown.damai.order.state;

import com.myown.damai.order.entity.OrderStatus;
import java.time.Instant;

/**
 * Carries one validated order status transition and its timestamp metadata.
 */
public record OrderStateTransition(
        Long orderNumber,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        Instant eventTime,
        boolean writeCancelTime,
        boolean writePayTime,
        boolean copyPayPrice
) {

    /**
     * Builds a transition object and derives which order time columns should be written.
     */
    public static OrderStateTransition of(Long orderNumber, OrderStatus fromStatus, OrderStatus toStatus, Instant eventTime) {
        boolean cancelLike = OrderStatus.CANCELED.equals(toStatus) || OrderStatus.TIMEOUT.equals(toStatus);
        boolean paid = OrderStatus.PAID.equals(toStatus);
        return new OrderStateTransition(orderNumber, fromStatus, toStatus, eventTime, cancelLike, paid, paid);
    }

    /**
     * Returns the database code of the source status.
     */
    public int fromStatusCode() {
        return fromStatus.code();
    }

    /**
     * Returns the database code of the target status.
     */
    public int toStatusCode() {
        return toStatus.code();
    }
}
