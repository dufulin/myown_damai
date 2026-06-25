package com.myown.damai.order.state;

import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.order.dao.OrderDao;
import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderStatus;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Centralizes allowed order status transitions and performs atomic state updates.
 */
@Component
public class OrderStateMachine {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderStateMachine.class);
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = buildAllowedTransitions();

    private final OrderDao orderDao;

    /**
     * Creates the state machine with order persistence operations.
     */
    public OrderStateMachine(OrderDao orderDao) {
        this.orderDao = orderDao;
    }

    /**
     * Transitions an order to canceled status after validating the current lifecycle state.
     */
    public void cancel(Order order, Instant cancelTime) {
        transition(order, OrderStatus.CANCELED, cancelTime, "ORDER_STATUS_NOT_CANCELABLE", "only pending payment orders can be canceled");
    }

    /**
     * Transitions an order to paid status after validating the current lifecycle state.
     */
    public void pay(Order order, Instant payTime) {
        transition(order, OrderStatus.PAID, payTime, "ORDER_STATUS_NOT_PAYABLE", "only pending payment orders can be paid");
    }

    /**
     * Transitions an order to timeout status after validating the current lifecycle state.
     */
    public boolean timeout(Order order, Instant timeoutTime) {
        return transitionIfPossible(order, OrderStatus.TIMEOUT, timeoutTime);
    }

    /**
     * Transitions an order to refunded status after validating the current lifecycle state.
     */
    public void refund(Order order, Instant refundTime) {
        transition(order, OrderStatus.REFUNDED, refundTime, "ORDER_STATUS_NOT_REFUNDABLE", "only paid orders can be refunded");
    }

    /**
     * Returns whether a given source status can move to the target status.
     */
    public boolean canTransit(OrderStatus sourceStatus, OrderStatus targetStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(sourceStatus, Set.of()).contains(targetStatus);
    }

    /**
     * Checks if an order is already in the target status.
     */
    public boolean alreadyInStatus(Order order, OrderStatus targetStatus) {
        return targetStatus.equals(OrderStatus.fromCode(order.orderStatus));
    }

    /**
     * Performs one strict transition and raises a business error when it cannot be applied.
     */
    private void transition(Order order, OrderStatus targetStatus, Instant eventTime, String errorCode, String errorMessage) {
        OrderStatus currentStatus = OrderStatus.fromCode(order.orderStatus);
        if (!canTransit(currentStatus, targetStatus)) {
            LOGGER.warn(
                    "order status transition rejected, orderNumber={}, fromStatus={}, toStatus={}",
                    order.orderNumber,
                    currentStatus,
                    targetStatus
            );
            throw new BusinessException(errorCode, errorMessage, HttpStatus.CONFLICT);
        }
        OrderStateTransition transition = OrderStateTransition.of(order.orderNumber, currentStatus, targetStatus, eventTime);
        if (!orderDao.transitionOrderStatus(transition)) {
            throw new BusinessException(errorCode, errorMessage, HttpStatus.CONFLICT);
        }
        orderDao.transitionTicketUsersStatus(transition);
        LOGGER.info("order status transitioned, orderNumber={}, fromStatus={}, toStatus={}", order.orderNumber, currentStatus, targetStatus);
    }

    /**
     * Attempts a transition and returns false when a concurrent state change has already won.
     */
    private boolean transitionIfPossible(Order order, OrderStatus targetStatus, Instant eventTime) {
        OrderStatus currentStatus = OrderStatus.fromCode(order.orderStatus);
        if (!canTransit(currentStatus, targetStatus)) {
            LOGGER.info(
                    "order status transition skipped, orderNumber={}, fromStatus={}, toStatus={}",
                    order.orderNumber,
                    currentStatus,
                    targetStatus
            );
            return false;
        }
        OrderStateTransition transition = OrderStateTransition.of(order.orderNumber, currentStatus, targetStatus, eventTime);
        boolean transitioned = orderDao.transitionOrderStatus(transition);
        if (transitioned) {
            orderDao.transitionTicketUsersStatus(transition);
            LOGGER.info("order status transitioned, orderNumber={}, fromStatus={}, toStatus={}", order.orderNumber, currentStatus, targetStatus);
        }
        return transitioned;
    }

    /**
     * Builds the allowed lifecycle transition graph.
     */
    private static Map<OrderStatus, Set<OrderStatus>> buildAllowedTransitions() {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(OrderStatus.PENDING_CREATE, EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELED));
        transitions.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELED, OrderStatus.TIMEOUT));
        transitions.put(OrderStatus.PAID, EnumSet.of(OrderStatus.REFUNDED));
        transitions.put(OrderStatus.CANCELED, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.TIMEOUT, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
        return transitions;
    }
}
