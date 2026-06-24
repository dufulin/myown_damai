package com.myown.damai.program.dao;

import com.myown.damai.program.entity.Program;
import com.myown.damai.program.entity.ProgramCategory;
import com.myown.damai.program.entity.ProgramGroup;
import com.myown.damai.program.entity.ProgramShowTime;
import com.myown.damai.program.entity.ProgramTicketPriceRange;
import com.myown.damai.program.entity.Seat;
import com.myown.damai.program.entity.TicketCategory;
import java.util.List;
import java.util.Optional;

/**
 * Defines persistence operations for the program service.
 */
public interface ProgramDao {

    /**
     * Saves one category.
     */
    ProgramCategory saveCategory(ProgramCategory category);

    /**
     * Finds one category by id.
     */
    Optional<ProgramCategory> findCategoryById(Long id);

    /**
     * Lists all normal categories.
     */
    List<ProgramCategory> listCategories();

    /**
     * Saves one program group.
     */
    ProgramGroup saveGroup(ProgramGroup group);

    /**
     * Saves one program.
     */
    Program saveProgram(Program program);

    /**
     * Finds one normal program by id.
     */
    Optional<Program> findProgramById(Long id);

    /**
     * Lists normal programs.
     */
    List<Program> listPrograms(String keyword, Long categoryId, Long areaId, int limit, int offset);

    /**
     * Lists all normal program ids for search index initialization.
     */
    List<Long> listNormalProgramIds();

    /**
     * Lists ticket price ranges for all normal programs.
     */
    List<ProgramTicketPriceRange> listTicketPriceRanges();

    /**
     * Saves one show time.
     */
    ProgramShowTime saveShowTime(ProgramShowTime showTime);

    /**
     * Lists show times by program id.
     */
    List<ProgramShowTime> listShowTimes(Long programId);

    /**
     * Saves one ticket category.
     */
    TicketCategory saveTicketCategory(TicketCategory ticketCategory);

    /**
     * Finds one ticket category by id.
     */
    Optional<TicketCategory> findTicketCategoryById(Long id);

    /**
     * Lists ticket categories by program id.
     */
    List<TicketCategory> listTicketCategories(Long programId);

    /**
     * Saves seats in batch.
     */
    void saveSeats(List<Seat> seats);

    /**
     * Lists seats by program id.
     */
    List<Seat> listSeats(Long programId);

    /**
     * Decreases remaining stock for one ticket category.
     */
    boolean decreaseTicketCategoryRemain(Long programId, Long ticketCategoryId, long quantity);

    /**
     * Increases remaining stock for one ticket category.
     */
    boolean increaseTicketCategoryRemain(Long programId, Long ticketCategoryId, long quantity);

    /**
     * Updates one seat status if it is currently in the expected status.
     */
    boolean updateSeatSellStatus(Long programId, Long ticketCategoryId, Long seatId, int fromStatus, int toStatus);
}
