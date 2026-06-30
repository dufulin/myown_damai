package com.myown.damai.admin.mapper;

import com.myown.damai.admin.dto.AdminOrderResponse;
import com.myown.damai.admin.dto.AdminProgramResponse;
import com.myown.damai.admin.dto.AdminUserResponse;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Reads the single-database management projection without mutating domain tables.
 */
public interface AdminReadMapper {

    /**
     * Counts active user records.
     */
    long countUsers();

    /**
     * Counts active and on-sale programs.
     */
    long countActivePrograms();

    /**
     * Counts active orders in one lifecycle status.
     */
    long countOrdersByStatus(@Param("orderStatus") int orderStatus);

    /**
     * Sums successfully paid bill amounts.
     */
    BigDecimal sumPaidAmount();

    /**
     * Lists users under optional keyword and role filters.
     */
    List<AdminUserResponse> listUsers(
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    /**
     * Counts users under optional keyword and role filters.
     */
    long countFilteredUsers(
            @Param("keyword") String keyword,
            @Param("role") String role
    );

    /**
     * Lists programs under optional keyword and business-status filters.
     */
    List<AdminProgramResponse> listPrograms(
            @Param("keyword") String keyword,
            @Param("programStatus") Integer programStatus,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    /**
     * Counts programs under optional keyword and business-status filters.
     */
    long countFilteredPrograms(
            @Param("keyword") String keyword,
            @Param("programStatus") Integer programStatus
    );

    /**
     * Lists orders under optional ownership and lifecycle filters.
     */
    List<AdminOrderResponse> listOrders(
            @Param("orderNumber") Long orderNumber,
            @Param("userId") Long userId,
            @Param("programId") Long programId,
            @Param("orderStatus") Integer orderStatus,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    /**
     * Counts orders under optional ownership and lifecycle filters.
     */
    long countFilteredOrders(
            @Param("orderNumber") Long orderNumber,
            @Param("userId") Long userId,
            @Param("programId") Long programId,
            @Param("orderStatus") Integer orderStatus
    );
}
