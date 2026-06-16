package com.myown.damai.order.dao;

import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderTicketUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Defines persistence operations for order business logic.
 */
public interface OrderDao {

    /**
     * Saves one order master row.
     */
    Order saveOrder(Order order);

    /**
     * Saves order ticket-user rows.
     */
    void saveTicketUsers(List<OrderTicketUser> ticketUsers);

    /**
     * Finds one order by order number.
     */
    Optional<Order> findOrderByOrderNumber(Long orderNumber);

    /**
     * Lists orders for one user.
     */
    List<Order> listOrdersByUserId(Long userId, int limit, int offset);

    /**
     * Lists ticket-user rows for one order.
     */
    List<OrderTicketUser> listTicketUsers(Long orderNumber);

    /**
     * Cancels one unpaid order.
     */
    boolean cancelUnpaidOrder(Long orderNumber, Instant now, int unpaidStatus, int canceledStatus);

    /**
     * Cancels ticket-user rows for one order.
     */
    void cancelTicketUsers(Long orderNumber, Instant now, int canceledStatus);

    /**
     * Lists expired unpaid order numbers.
     */
    List<Long> listExpiredUnpaidOrderNumbers(Instant now, int unpaidStatus, int limit);
}
