package com.myown.damai.order.mapper;

import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderTicketUser;
import com.myown.damai.order.state.OrderStateTransition;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Provides MyBatis operations for order master and ticket-user detail tables.
 */
@Mapper
public interface OrderMapper {

    /**
     * Inserts one order master row.
     */
    int insertOrder(Order order);

    /**
     * Inserts ticket-user detail rows in batch.
     */
    int insertTicketUsers(@Param("ticketUsers") List<OrderTicketUser> ticketUsers);

    /**
     * Selects one normal order by order number.
     */
    Order selectOrderByOrderNumber(@Param("orderNumber") Long orderNumber);

    /**
     * Lists normal orders for one user.
     */
    List<Order> selectOrdersByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * Lists normal orders for one user after a create-time and id cursor.
     */
    List<Order> selectOrdersByUserIdWithCursor(
            @Param("userId") Long userId,
            @Param("cursorCreateTime") Instant cursorCreateTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );

    /**
     * Selects the latest non-terminal order for one user and one program.
     */
    Order selectLatestOrderByUserIdAndProgramId(
            @Param("userId") Long userId,
            @Param("programId") Long programId,
            @Param("orderStatuses") List<Integer> orderStatuses
    );

    /**
     * Lists ticket-user rows for one order.
     */
    List<OrderTicketUser> selectTicketUsersByOrderNumber(@Param("orderNumber") Long orderNumber);

    /**
     * Atomically transitions an order only when it still has the expected source status.
     */
    int transitionOrderStatus(@Param("transition") OrderStateTransition transition);

    /**
     * Transitions ticket-user rows after their master order transition succeeds.
     */
    int transitionTicketUsersStatus(@Param("transition") OrderStateTransition transition);

    /**
     * Lists expired unpaid order numbers.
     */
    List<Long> selectExpiredUnpaidOrderNumbers(
            @Param("now") Instant now,
            @Param("unpaidStatus") int unpaidStatus,
            @Param("limit") int limit
    );
}
