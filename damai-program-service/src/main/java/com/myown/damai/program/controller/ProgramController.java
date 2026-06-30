package com.myown.damai.program.controller;

import com.myown.damai.common.auth.UserRole;
import com.myown.damai.program.dto.ProgramCategoryRequest;
import com.myown.damai.program.dto.ProgramCategoryResponse;
import com.myown.damai.program.dto.ProgramCreateRequest;
import com.myown.damai.program.dto.ProgramDetailResponse;
import com.myown.damai.program.dto.ProgramInventoryRequest;
import com.myown.damai.program.dto.ProgramOrderSnapshotRequest;
import com.myown.damai.program.dto.ProgramOrderSnapshotResponse;
import com.myown.damai.program.dto.ProgramResponse;
import com.myown.damai.program.dto.SeatBatchCreateRequest;
import com.myown.damai.program.dto.SeatResponse;
import com.myown.damai.program.dto.TicketCategoryPriceUpdateRequest;
import com.myown.damai.program.dto.TicketCategoryResponse;
import com.myown.damai.program.service.ProgramService;
import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.observability.TraceContext;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides program, category, ticket category, show time, and seat APIs.
 */
@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramController.class);

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    /**
     * Creates a program category.
     */
    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<ProgramCategoryResponse>> createCategory(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @Valid @RequestBody ProgramCategoryRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        LOGGER.info("program category create request received, name={}, parentId={}", request.name(), request.parentId());
        ProgramCategoryResponse response = programService.createCategory(request);
        LOGGER.info("program category create request succeeded, categoryId={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Lists program categories.
     */
    @GetMapping("/categories")
    public ApiResponse<List<ProgramCategoryResponse>> listCategories() {
        LOGGER.info("program category list request received");
        List<ProgramCategoryResponse> categories = programService.listCategories();
        LOGGER.info("program category list request succeeded, count={}", categories.size());
        return ApiResponse.success(categories);
    }

    /**
     * Creates a program with show times and ticket categories.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProgramDetailResponse>> createProgram(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @Valid @RequestBody ProgramCreateRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        LOGGER.info("program create request received, title={}", request.title());
        ProgramDetailResponse response = programService.createProgram(request);
        TraceContext.putProgramId(response.program().id());
        LOGGER.info("program create request succeeded, programId={}", response.program().id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Lists programs.
     */
    @GetMapping
    public ApiResponse<List<ProgramResponse>> listPrograms(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "areaId", required = false) Long areaId,
            @RequestParam(value = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        LOGGER.info("program list request received, keyword={}, categoryId={}, areaId={}", keyword, categoryId, areaId);
        List<ProgramResponse> programs = programService.listPrograms(keyword, categoryId, areaId, pageNumber, pageSize);
        LOGGER.info("program list request succeeded, count={}", programs.size());
        return ApiResponse.success(programs);
    }

    /**
     * Searches programs with Elasticsearch smart filters.
     */
    @GetMapping("/search")
    public ApiResponse<List<ProgramResponse>> searchPrograms(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "areaId", required = false) Long areaId,
            @RequestParam(value = "programCategoryId", required = false) Long programCategoryId,
            @RequestParam(value = "timeType", defaultValue = "0") Integer timeType,
            @RequestParam(value = "startDateTime", required = false) String startDateTime,
            @RequestParam(value = "endDateTime", required = false) String endDateTime,
            @RequestParam(value = "type", defaultValue = "1") Integer type,
            @RequestParam(value = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        LOGGER.info(
                "program search request received, keyword={}, areaId={}, programCategoryId={}, timeType={}, type={}",
                keyword,
                areaId,
                programCategoryId,
                timeType,
                type
        );
        List<ProgramResponse> programs = programService.searchPrograms(
                keyword,
                areaId,
                programCategoryId,
                timeType,
                startDateTime,
                endDateTime,
                type,
                pageNumber,
                pageSize
        );
        LOGGER.info("program search request succeeded, count={}", programs.size());
        return ApiResponse.success(programs);
    }

    /**
     * Gets one program detail.
     */
    @GetMapping("/{programId}")
    public ApiResponse<ProgramDetailResponse> getProgramDetail(@PathVariable Long programId) {
        LOGGER.info("program detail request received, programId={}", programId);
        ProgramDetailResponse response = programService.getProgramDetail(programId);
        LOGGER.info("program detail request succeeded, programId={}", programId);
        return ApiResponse.success(response);
    }

    /**
     * Resolves authoritative database values used to create an order snapshot.
     */
    @PostMapping("/{programId}/order-snapshot")
    public ApiResponse<ProgramOrderSnapshotResponse> getOrderSnapshot(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable Long programId,
            @Valid @RequestBody ProgramOrderSnapshotRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.SYSTEM);
        LOGGER.info(
                "program order snapshot request received, programId={}, showTimeId={}, ticketCategoryCount={}",
                programId,
                request.showTimeId(),
                request.ticketCategoryIds().size()
        );
        ProgramOrderSnapshotResponse response = programService.getOrderSnapshot(programId, request);
        LOGGER.info(
                "program order snapshot request succeeded, programId={}, showTimeId={}",
                programId,
                request.showTimeId()
        );
        return ApiResponse.success(response);
    }

    /**
     * Marks one program offline.
     */
    @PostMapping("/{programId}/offline")
    public ApiResponse<Void> offlineProgram(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable Long programId
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        LOGGER.info("program offline request received, programId={}", programId);
        programService.offlineProgram(programId);
        LOGGER.info("program offline request succeeded, programId={}", programId);
        return ApiResponse.success();
    }

    /**
     * Updates one ticket category price.
     */
    @PostMapping("/{programId}/ticket-categories/{ticketCategoryId}/price")
    public ApiResponse<TicketCategoryResponse> updateTicketCategoryPrice(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable Long programId,
            @PathVariable Long ticketCategoryId,
            @Valid @RequestBody TicketCategoryPriceUpdateRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        LOGGER.info("ticket category price update request received, programId={}, ticketCategoryId={}, price={}", programId, ticketCategoryId, request.price());
        TicketCategoryResponse response = programService.updateTicketCategoryPrice(programId, ticketCategoryId, request);
        LOGGER.info("ticket category price update request succeeded, programId={}, ticketCategoryId={}", programId, ticketCategoryId);
        return ApiResponse.success(response);
    }

    /**
     * Batch creates seats for a program.
     */
    @PostMapping("/{programId}/seats")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> createSeats(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable Long programId,
            @Valid @RequestBody SeatBatchCreateRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        LOGGER.info(
                "program seat create request received, programId={}, ticketCategoryId={}",
                programId,
                request.ticketCategoryId()
        );
        List<SeatResponse> seats = programService.createSeats(programId, request);
        LOGGER.info("program seat create request succeeded, programId={}, seatCount={}", programId, seats.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(seats));
    }

    /**
     * Lists seats for a program.
     */
    @GetMapping("/{programId}/seats")
    public ApiResponse<List<SeatResponse>> listSeats(@PathVariable Long programId) {
        LOGGER.info("program seat list request received, programId={}", programId);
        List<SeatResponse> seats = programService.listSeats(programId);
        LOGGER.info("program seat list request succeeded, programId={}, seatCount={}", programId, seats.size());
        return ApiResponse.success(seats);
    }

    /**
     * Locks ticket stock and optional seats for one order.
     */
    @PostMapping("/{programId}/inventory/lock")
    public ApiResponse<Void> lockInventory(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable Long programId,
            @Valid @RequestBody ProgramInventoryRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.SYSTEM);
        TraceContext.putProgramId(programId);
        TraceContext.putOrderNumber(request.orderNumber());
        LOGGER.info("program inventory lock request received, programId={}, orderNumber={}", programId, request.orderNumber());
        programService.lockInventory(programId, request);
        LOGGER.info("program inventory lock request succeeded, programId={}, orderNumber={}", programId, request.orderNumber());
        return ApiResponse.success();
    }

    /**
     * Releases ticket stock and locked seats for one canceled order.
     */
    @PostMapping("/{programId}/inventory/release")
    public ApiResponse<Void> releaseInventory(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable Long programId,
            @Valid @RequestBody ProgramInventoryRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.SYSTEM);
        TraceContext.putProgramId(programId);
        TraceContext.putOrderNumber(request.orderNumber());
        LOGGER.info("program inventory release request received, programId={}, orderNumber={}", programId, request.orderNumber());
        programService.releaseInventory(programId, request);
        LOGGER.info("program inventory release request succeeded, programId={}, orderNumber={}", programId, request.orderNumber());
        return ApiResponse.success();
    }

    /**
     * Marks locked seats as sold after an order is paid.
     */
    @PostMapping("/{programId}/inventory/sold")
    public ApiResponse<Void> markInventorySold(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable Long programId,
            @Valid @RequestBody ProgramInventoryRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.SYSTEM);
        TraceContext.putProgramId(programId);
        TraceContext.putOrderNumber(request.orderNumber());
        LOGGER.info("program inventory sold request received, programId={}, orderNumber={}", programId, request.orderNumber());
        programService.markInventorySold(programId, request);
        LOGGER.info("program inventory sold request succeeded, programId={}, orderNumber={}", programId, request.orderNumber());
        return ApiResponse.success();
    }
}
