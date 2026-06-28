package com.myown.damai.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.program.dao.ProgramDao;
import com.myown.damai.program.dto.ProgramCategoryRequest;
import com.myown.damai.program.dto.ProgramCreateRequest;
import com.myown.damai.program.dto.ProgramDetailResponse;
import com.myown.damai.program.dto.ProgramInventoryItemRequest;
import com.myown.damai.program.dto.ProgramInventoryRequest;
import com.myown.damai.program.entity.TicketCategory;
import com.myown.damai.program.service.ProgramService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies ticket inventory remains correct under concurrent database-backed reservation attempts.
 */
@SpringBootTest
class ProgramInventoryConcurrencyIntegrationTest {

    @Autowired
    private ProgramService programService;

    @Autowired
    private ProgramDao programDao;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Verifies two concurrent orders cannot both reserve the final ticket.
     */
    @Test
    void concurrentReservationCannotOversellLastTicket() throws Exception {
        ProgramDetailResponse program = createSingleTicketProgram();
        Long programId = program.program().id();
        Long ticketCategoryId = program.ticketCategories().get(0).id();
        ProgramInventoryRequest firstRequest = inventoryRequest(88001L, ticketCategoryId);
        ProgramInventoryRequest secondRequest = inventoryRequest(88002L, ticketCategoryId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(
                    () -> reserveAtTheSameTime(ready, start, programId, firstRequest)
            );
            Future<Boolean> secondResult = executor.submit(
                    () -> reserveAtTheSameTime(ready, start, programId, secondRequest)
            );
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Boolean> results = List.of(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS)
            );
            assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
            assertEquals(1L, results.stream().filter(result -> !result).count());
        } finally {
            executor.shutdownNow();
        }

        TicketCategory ticketCategory = programDao.findTicketCategoryById(ticketCategoryId).orElseThrow();
        assertEquals(0L, ticketCategory.remainNumber);
        assertFalse(ticketCategory.remainNumber < 0L);
    }

    /**
     * Creates a persisted program whose only ticket category has one available ticket.
     */
    private ProgramDetailResponse createSingleTicketProgram() throws Exception {
        Long parentCategoryId = programService.createCategory(
                new ProgramCategoryRequest(0L, "Concurrency Parent", 1)
        ).id();
        Long categoryId = programService.createCategory(
                new ProgramCategoryRequest(parentCategoryId, "Concurrency Child", 2)
        ).id();
        ProgramCreateRequest request = objectMapper.readValue(
                """
                {
                  "areaId": 110000,
                  "programCategoryId": %d,
                  "parentProgramCategoryId": %d,
                  "title": "Inventory Concurrency Concert",
                  "place": "Concurrency Arena",
                  "detail": "Inventory concurrency integration fixture.",
                  "permitChooseSeat": 0,
                  "showTimes": [
                    {
                      "showTime": "2026-09-01T12:00:00Z"
                    }
                  ],
                  "ticketCategories": [
                    {
                      "introduce": "Only Ticket",
                      "price": 680,
                      "totalNumber": 1
                    }
                  ]
                }
                """.formatted(categoryId, parentCategoryId),
                ProgramCreateRequest.class
        );
        return programService.createProgram(request);
    }

    /**
     * Builds one inventory reservation for a single ticket without seat selection.
     */
    private ProgramInventoryRequest inventoryRequest(Long orderNumber, Long ticketCategoryId) {
        return new ProgramInventoryRequest(
                orderNumber,
                List.of(new ProgramInventoryItemRequest(ticketCategoryId, null))
        );
    }

    /**
     * Waits for the shared start signal and reports whether inventory reservation succeeded.
     */
    private boolean reserveAtTheSameTime(
            CountDownLatch ready,
            CountDownLatch start,
            Long programId,
            ProgramInventoryRequest request
    ) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            programService.lockInventory(programId, request);
            return true;
        } catch (BusinessException exception) {
            assertEquals("PROGRAM_INVENTORY_NOT_ENOUGH", exception.getCode());
            return false;
        }
    }
}
