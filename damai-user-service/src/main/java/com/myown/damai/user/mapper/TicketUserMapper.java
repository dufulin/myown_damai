package com.myown.damai.user.mapper;

import com.myown.damai.user.entity.TicketUser;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Provides MyBatis operations for real-name ticket buyers.
 */
@Mapper
public interface TicketUserMapper {

    /**
     * Selects active ticket buyers owned by one user.
     */
    List<TicketUser> selectActiveByUserId(@Param("userId") Long userId);

    /**
     * Selects one active ticket buyer by id and owner.
     */
    TicketUser selectActiveByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    /**
     * Inserts one ticket buyer row.
     */
    int insert(TicketUser ticketUser);

    /**
     * Soft deletes one ticket buyer row.
     */
    int softDeleteByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("now") Instant now
    );
}
