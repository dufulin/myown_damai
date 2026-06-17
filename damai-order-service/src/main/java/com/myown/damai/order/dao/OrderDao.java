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
     * Finds an existing payable or paid order for one user and program.
     */
    Optional<Order> findExistingUserProgramOrder(Long userId, Long programId, List<Integer> orderStatuses);

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
     * Marks one unpaid order as paid.
     */
    boolean payUnpaidOrder(Long orderNumber, Instant payTime, int unpaidStatus, int paidStatus);

    /**
     * Marks ticket-user rows as paid for one order.
     */
    void payTicketUsers(Long orderNumber, Instant payTime, int paidStatus);

    /**
     * Lists expired unpaid order numbers.
     */
    List<Long> listExpiredUnpaidOrderNumbers(Instant now, int unpaidStatus, int limit);
}
