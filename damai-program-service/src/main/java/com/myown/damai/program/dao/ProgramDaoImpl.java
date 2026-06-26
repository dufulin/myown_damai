package com.myown.damai.program.dao;

import com.myown.damai.program.entity.Program;
import com.myown.damai.program.entity.ProgramCategory;
import com.myown.damai.program.entity.ProgramGroup;
import com.myown.damai.program.entity.ProgramShowTime;
import com.myown.damai.program.entity.ProgramTicketPriceRange;
import com.myown.damai.program.entity.Seat;
import com.myown.damai.program.entity.TicketCategory;
import com.myown.damai.program.mapper.ProgramMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Implements program persistence operations with MyBatis.
 */
@Repository
public class ProgramDaoImpl implements ProgramDao {

    private final ProgramMapper programMapper;

    public ProgramDaoImpl(ProgramMapper programMapper) {
        this.programMapper = programMapper;
    }

    @Override
    public ProgramCategory saveCategory(ProgramCategory category) {
        programMapper.insertCategory(category);
        Objects.requireNonNull(category.id, "generated category id must not be null");
        return category;
    }

    @Override
    public Optional<ProgramCategory> findCategoryById(Long id) {
        return Optional.ofNullable(programMapper.selectCategoryById(id));
    }

    @Override
    public List<ProgramCategory> listCategories() {
        return programMapper.selectCategories();
    }

    @Override
    public ProgramGroup saveGroup(ProgramGroup group) {
        programMapper.insertGroup(group);
        Objects.requireNonNull(group.id, "generated program group id must not be null");
        return group;
    }

    @Override
    public Program saveProgram(Program program) {
        programMapper.insertProgram(program);
        Objects.requireNonNull(program.id, "generated program id must not be null");
        return program;
    }

    /**
     * Marks one program as offline by updating its business status.
     */
    @Override
    public boolean offlineProgram(Long programId) {
        return programMapper.offlineProgram(programId, Instant.now()) > 0;
    }

    @Override
    public Optional<Program> findProgramById(Long id) {
        return Optional.ofNullable(programMapper.selectProgramById(id));
    }

    @Override
    public List<Program> listPrograms(String keyword, Long categoryId, Long areaId, int limit, int offset) {
        return programMapper.selectPrograms(keyword, categoryId, areaId, limit, offset);
    }

    /**
     * Lists all normal program ids by delegating to the MyBatis mapper.
     */
    @Override
    public List<Long> listNormalProgramIds() {
        return programMapper.selectNormalProgramIds();
    }

    /**
     * Lists ticket price ranges by delegating to the MyBatis mapper.
     */
    @Override
    public List<ProgramTicketPriceRange> listTicketPriceRanges() {
        return programMapper.selectTicketPriceRanges();
    }

    @Override
    public ProgramShowTime saveShowTime(ProgramShowTime showTime) {
        programMapper.insertShowTime(showTime);
        Objects.requireNonNull(showTime.id, "generated show time id must not be null");
        return showTime;
    }

    @Override
    public List<ProgramShowTime> listShowTimes(Long programId) {
        return programMapper.selectShowTimesByProgramId(programId);
    }

    @Override
    public TicketCategory saveTicketCategory(TicketCategory ticketCategory) {
        programMapper.insertTicketCategory(ticketCategory);
        Objects.requireNonNull(ticketCategory.id, "generated ticket category id must not be null");
        return ticketCategory;
    }

    /**
     * Updates one ticket category price by delegating to MyBatis.
     */
    @Override
    public boolean updateTicketCategoryPrice(Long programId, Long ticketCategoryId, BigDecimal price) {
        return programMapper.updateTicketCategoryPrice(programId, ticketCategoryId, price, Instant.now()) > 0;
    }

    @Override
    public Optional<TicketCategory> findTicketCategoryById(Long id) {
        return Optional.ofNullable(programMapper.selectTicketCategoryById(id));
    }

    @Override
    public List<TicketCategory> listTicketCategories(Long programId) {
        return programMapper.selectTicketCategoriesByProgramId(programId);
    }

    @Override
    public void saveSeats(List<Seat> seats) {
        if (!seats.isEmpty()) {
            programMapper.insertSeats(seats);
        }
    }

    @Override
    public List<Seat> listSeats(Long programId) {
        return programMapper.selectSeatsByProgramId(programId);
    }

    /**
     * Decreases remaining stock for one ticket category using an atomic SQL predicate.
     */
    @Override
    public boolean decreaseTicketCategoryRemain(Long programId, Long ticketCategoryId, long quantity) {
        return programMapper.decreaseTicketCategoryRemain(programId, ticketCategoryId, quantity, Instant.now()) > 0;
    }

    /**
     * Increases remaining stock for one ticket category using an atomic SQL update.
     */
    @Override
    public boolean increaseTicketCategoryRemain(Long programId, Long ticketCategoryId, long quantity) {
        return programMapper.increaseTicketCategoryRemain(programId, ticketCategoryId, quantity, Instant.now()) > 0;
    }

    /**
     * Updates one seat status using an expected-status guard.
     */
    @Override
    public boolean updateSeatSellStatus(Long programId, Long ticketCategoryId, Long seatId, int fromStatus, int toStatus) {
        return programMapper.updateSeatSellStatus(programId, ticketCategoryId, seatId, fromStatus, toStatus, Instant.now()) > 0;
    }
}
