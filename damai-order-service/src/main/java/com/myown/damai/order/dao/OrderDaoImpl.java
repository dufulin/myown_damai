package com.myown.damai.order.dao;

import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderTicketUser;
import com.myown.damai.order.mapper.OrderMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Implements order persistence operations with MyBatis.
 */
@Repository
public class OrderDaoImpl implements OrderDao {

    private final OrderMapper orderMapper;

    /**
     * Creates the DAO with its MyBatis mapper.
     */
    public OrderDaoImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public Order saveOrder(Order order) {
        orderMapper.insertOrder(order);
        Objects.requireNonNull(order.id, "generated order id must not be null");
        return order;
    }

    @Override
    public void saveTicketUsers(List<OrderTicketUser> ticketUsers) {
        if (!ticketUsers.isEmpty()) {
            orderMapper.insertTicketUsers(ticketUsers);
        }
    }

    @Override
    public Optional<Order> findOrderByOrderNumber(Long orderNumber) {
        return Optional.ofNullable(orderMapper.selectOrderByOrderNumber(orderNumber));
    }

    @Override
    public List<Order> listOrdersByUserId(Long userId, int limit, int offset) {
        return orderMapper.selectOrdersByUserId(userId, limit, offset);
    }

    @Override
    public List<OrderTicketUser> listTicketUsers(Long orderNumber) {
        return orderMapper.selectTicketUsersByOrderNumber(orderNumber);
    }

    @Override
    public boolean cancelUnpaidOrder(Long orderNumber, Instant now, int unpaidStatus, int canceledStatus) {
        return orderMapper.cancelUnpaidOrder(orderNumber, now, unpaidStatus, canceledStatus) > 0;
    }

    @Override
    public void cancelTicketUsers(Long orderNumber, Instant now, int canceledStatus) {
        orderMapper.cancelTicketUsersByOrderNumber(orderNumber, now, canceledStatus);
    }

    @Override
    public List<Long> listExpiredUnpaidOrderNumbers(Instant now, int unpaidStatus, int limit) {
        return orderMapper.selectExpiredUnpaidOrderNumbers(now, unpaidStatus, limit);
    }
}
