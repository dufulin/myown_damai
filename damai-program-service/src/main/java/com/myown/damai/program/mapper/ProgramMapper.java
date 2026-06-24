package com.myown.damai.program.mapper;

import com.myown.damai.program.entity.Program;
import com.myown.damai.program.entity.ProgramCategory;
import com.myown.damai.program.entity.ProgramGroup;
import com.myown.damai.program.entity.ProgramShowTime;
import com.myown.damai.program.entity.ProgramTicketPriceRange;
import com.myown.damai.program.entity.Seat;
import com.myown.damai.program.entity.TicketCategory;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Provides MyBatis operations for program domain tables.
 */
@Mapper
public interface ProgramMapper {

    /**
     * Inserts one program category.
     */
    int insertCategory(ProgramCategory category);

    /**
     * Selects one category by id.
     */
    ProgramCategory selectCategoryById(@Param("id") Long id);

    /**
     * Lists normal categories.
     */
    List<ProgramCategory> selectCategories();

    /**
     * Inserts one program group.
     */
    int insertGroup(ProgramGroup group);

    /**
     * Inserts one program.
     */
    int insertProgram(Program program);

    /**
     * Selects one normal program by id.
     */
    Program selectProgramById(@Param("id") Long id);

    /**
     * Lists normal programs with optional filters.
     */
    List<Program> selectPrograms(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("areaId") Long areaId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * Lists ids of normal programs.
     */
    List<Long> selectNormalProgramIds();

    /**
     * Lists minimum and maximum ticket prices for normal programs.
     */
    List<ProgramTicketPriceRange> selectTicketPriceRanges();

    /**
     * Inserts one show time.
     */
    int insertShowTime(ProgramShowTime showTime);

    /**
     * Lists show times for a program.
     */
    List<ProgramShowTime> selectShowTimesByProgramId(@Param("programId") Long programId);

    /**
     * Inserts one ticket category.
     */
    int insertTicketCategory(TicketCategory ticketCategory);

    /**
     * Selects one ticket category by id.
     */
    TicketCategory selectTicketCategoryById(@Param("id") Long id);

    /**
     * Lists ticket categories for a program.
     */
    List<TicketCategory> selectTicketCategoriesByProgramId(@Param("programId") Long programId);

    /**
     * Inserts seats in batch.
     */
    int insertSeats(@Param("seats") List<Seat> seats);

    /**
     * Lists seats for a program.
     */
    List<Seat> selectSeatsByProgramId(@Param("programId") Long programId);

    /**
     * Decreases remaining stock for one ticket category when enough stock is available.
     */
    int decreaseTicketCategoryRemain(
            @Param("programId") Long programId,
            @Param("ticketCategoryId") Long ticketCategoryId,
            @Param("quantity") long quantity,
            @Param("now") Instant now
    );

    /**
     * Increases remaining stock for one ticket category without exceeding total stock.
     */
    int increaseTicketCategoryRemain(
            @Param("programId") Long programId,
            @Param("ticketCategoryId") Long ticketCategoryId,
            @Param("quantity") long quantity,
            @Param("now") Instant now
    );

    /**
     * Updates one seat status when it is currently in the expected status.
     */
    int updateSeatSellStatus(
            @Param("programId") Long programId,
            @Param("ticketCategoryId") Long ticketCategoryId,
            @Param("seatId") Long seatId,
            @Param("fromStatus") int fromStatus,
            @Param("toStatus") int toStatus,
            @Param("now") Instant now
    );
}
