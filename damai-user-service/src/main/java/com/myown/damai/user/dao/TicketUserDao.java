package com.myown.damai.user.dao;

import com.myown.damai.user.entity.TicketUser;
import java.util.List;
import java.util.Optional;

/**
 * Provides ticket buyer persistence operations without exposing MyBatis details.
 */
public interface TicketUserDao {

    /**
     * Lists active ticket buyers owned by one user.
     */
    List<TicketUser> listByUserId(Long userId);

    /**
     * Finds one active ticket buyer owned by one user.
     */
    Optional<TicketUser> findByIdAndUserId(Long id, Long userId);

    /**
     * Saves one ticket buyer.
     */
    TicketUser save(TicketUser ticketUser);

    /**
     * Soft deletes one ticket buyer.
     */
    boolean deleteByIdAndUserId(Long id, Long userId);
}
