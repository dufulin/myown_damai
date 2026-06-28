package com.myown.damai.order.dao;

import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderTicketUser;
import com.myown.damai.order.state.OrderStateTransition;
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
     * Lists orders for one user after a create-time and id cursor.
     */
    List<Order> listOrdersByUserIdWithCursor(Long userId, Instant cursorCreateTime, Long cursorId, int limit);

    /**
     * Finds an existing payable or paid order for one user and program.
     */
    Optional<Order> findExistingUserProgramOrder(Long userId, Long programId, List<Integer> orderStatuses);

    /**
     * Lists ticket-user rows for one order.
     */
    List<OrderTicketUser> listTicketUsers(Long orderNumber);

    /**
     * Atomically transitions one order from the expected source status to the target status.
     */
    boolean transitionOrderStatus(OrderStateTransition transition);

    /**
     * Transitions ticket-user rows together with their master order.
     */
    void transitionTicketUsersStatus(OrderStateTransition transition);

    /**
     * Lists expired unpaid order numbers.
     */
    List<Long> listExpiredUnpaidOrderNumbers(Instant now, int unpaidStatus, int limit);
}
