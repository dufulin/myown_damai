package com.myown.damai.order.mapper;

import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderTicketUser;
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
     * Cancels an unpaid order.
     */
    int cancelUnpaidOrder(
            @Param("orderNumber") Long orderNumber,
            @Param("now") Instant now,
            @Param("unpaidStatus") int unpaidStatus,
            @Param("canceledStatus") int canceledStatus
    );

    /**
     * Updates ticket-user rows to canceled for one order.
     */
    int cancelTicketUsersByOrderNumber(
            @Param("orderNumber") Long orderNumber,
            @Param("now") Instant now,
            @Param("canceledStatus") int canceledStatus
    );

    /**
     * Marks one unpaid order as paid.
     */
    int payUnpaidOrder(
            @Param("orderNumber") Long orderNumber,
            @Param("payTime") Instant payTime,
            @Param("unpaidStatus") int unpaidStatus,
            @Param("paidStatus") int paidStatus
    );

    /**
     * Marks ticket-user rows as paid for one order.
     */
    int payTicketUsersByOrderNumber(
            @Param("orderNumber") Long orderNumber,
            @Param("payTime") Instant payTime,
            @Param("paidStatus") int paidStatus
    );

    /**
     * Lists expired unpaid order numbers.
     */
    List<Long> selectExpiredUnpaidOrderNumbers(
            @Param("now") Instant now,
            @Param("unpaidStatus") int unpaidStatus,
            @Param("limit") int limit
    );
}
