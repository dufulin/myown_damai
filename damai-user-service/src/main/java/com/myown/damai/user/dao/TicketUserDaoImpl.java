package com.myown.damai.user.dao;

import com.myown.damai.user.entity.TicketUser;
import com.myown.damai.user.mapper.TicketUserMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Stores and queries real-name ticket buyers through MyBatis.
 */
@Repository
public class TicketUserDaoImpl implements TicketUserDao {

    private final TicketUserMapper ticketUserMapper;

    /**
     * Creates the DAO with the ticket buyer mapper.
     */
    public TicketUserDaoImpl(TicketUserMapper ticketUserMapper) {
        this.ticketUserMapper = ticketUserMapper;
    }

    @Override
    public List<TicketUser> listByUserId(Long userId) {
        return ticketUserMapper.selectActiveByUserId(userId);
    }

    @Override
    public Optional<TicketUser> findByIdAndUserId(Long id, Long userId) {
        return Optional.ofNullable(ticketUserMapper.selectActiveByIdAndUserId(id, userId));
    }

    @Override
    public TicketUser save(TicketUser ticketUser) {
        Instant now = Instant.now();
        ticketUser.setCreatedAt(now);
        ticketUser.setUpdatedAt(now);
        ticketUser.setStatus(1);
        ticketUserMapper.insert(ticketUser);
        return ticketUser;
    }

    @Override
    public boolean deleteByIdAndUserId(Long id, Long userId) {
        return ticketUserMapper.softDeleteByIdAndUserId(id, userId, Instant.now()) > 0;
    }
}
