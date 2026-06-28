package com.myown.damai.user.service;

import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.user.dao.TicketUserDao;
import com.myown.damai.user.dto.TicketUserCreateRequest;
import com.myown.damai.user.dto.TicketUserResponse;
import com.myown.damai.user.entity.TicketUser;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Handles real-name ticket buyer creation, listing, and deletion for the current user.
 */
@Service
public class TicketUserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketUserService.class);

    private final TicketUserDao ticketUserDao;

    /**
     * Creates the service with ticket buyer persistence.
     */
    public TicketUserService(TicketUserDao ticketUserDao) {
        this.ticketUserDao = ticketUserDao;
    }

    /**
     * Lists active ticket buyers for the current logged-in user.
     */
    @Transactional(readOnly = true)
    public List<TicketUserResponse> listTicketUsers(Long userId) {
        return ticketUserDao.listByUserId(userId)
                .stream()
                .map(TicketUserResponse::from)
                .toList();
    }

    /**
     * Creates one ticket buyer for the current logged-in user.
     */
    @Transactional
    public TicketUserResponse createTicketUser(Long userId, TicketUserCreateRequest request) {
        TicketUser ticketUser = new TicketUser();
        ticketUser.setUserId(userId);
        ticketUser.setRelName(normalizeRequired(request.relName(), "relName"));
        ticketUser.setIdType(request.idType());
        ticketUser.setIdNumber(normalizeRequired(request.idNumber(), "idNumber"));
        TicketUser savedTicketUser = ticketUserDao.save(ticketUser);
        LOGGER.info("ticket user created, userId={}, ticketUserId={}", userId, savedTicketUser.getId());
        return TicketUserResponse.from(savedTicketUser);
    }

    /**
     * Deletes one ticket buyer for the current logged-in user.
     */
    @Transactional
    public void deleteTicketUser(Long userId, Long ticketUserId) {
        boolean deleted = ticketUserDao.deleteByIdAndUserId(ticketUserId, userId);
        if (!deleted) {
            LOGGER.warn("ticket user delete rejected because row was not found, userId={}, ticketUserId={}", userId, ticketUserId);
            throw new BusinessException("TICKET_USER_NOT_FOUND", "ticket user not found", HttpStatus.NOT_FOUND);
        }
        LOGGER.info("ticket user deleted, userId={}, ticketUserId={}", userId, ticketUserId);
    }

    /**
     * Verifies every supplied ticket buyer is active and owned by the specified user.
     */
    @Transactional(readOnly = true)
    public void validateTicketUserOwnership(Long userId, List<Long> ticketUserIds) {
        for (Long ticketUserId : ticketUserIds.stream().distinct().toList()) {
            if (ticketUserDao.findByIdAndUserId(ticketUserId, userId).isEmpty()) {
                LOGGER.warn(
                        "ticket user ownership validation rejected, userId={}, ticketUserId={}",
                        userId,
                        ticketUserId
                );
                throw new BusinessException(
                        "TICKET_USER_NOT_OWNED",
                        "ticket user is unavailable or does not belong to current user",
                        HttpStatus.FORBIDDEN
                );
            }
        }
    }

    /**
     * Normalizes required text fields for ticket buyer storage.
     */
    private String normalizeRequired(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("INVALID_TICKET_USER", fieldName + " is required", HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }
}
