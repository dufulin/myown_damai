package com.myown.damai.user.dto;

import com.myown.damai.user.entity.TicketUser;
import java.time.Instant;

/**
 * Exposes one real-name ticket buyer to frontend callers.
 */
public record TicketUserResponse(
        Long id,
        Long userId,
        String relName,
        Integer idType,
        String idNumber,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Builds a response from a ticket buyer entity.
     */
    public static TicketUserResponse from(TicketUser ticketUser) {
        return new TicketUserResponse(
                ticketUser.getId(),
                ticketUser.getUserId(),
                ticketUser.getRelName(),
                ticketUser.getIdType(),
                ticketUser.getIdNumber(),
                ticketUser.getCreatedAt(),
                ticketUser.getUpdatedAt()
        );
    }
}
