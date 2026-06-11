package com.myown.damai.program.controller;

import com.myown.damai.program.dto.ProgramCategoryRequest;
import com.myown.damai.program.dto.ProgramCategoryResponse;
import com.myown.damai.program.dto.ProgramCreateRequest;
import com.myown.damai.program.dto.ProgramDetailResponse;
import com.myown.damai.program.dto.ProgramResponse;
import com.myown.damai.program.dto.SeatBatchCreateRequest;
import com.myown.damai.program.dto.SeatResponse;
import com.myown.damai.program.service.ProgramService;
import com.myown.damai.user.dto.ApiResponse;
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
            @Valid @RequestBody ProgramCategoryRequest request
    ) {
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
            @Valid @RequestBody ProgramCreateRequest request
    ) {
        LOGGER.info("program create request received, title={}", request.title());
        ProgramDetailResponse response = programService.createProgram(request);
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
     * Batch creates seats for a program.
     */
    @PostMapping("/{programId}/seats")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> createSeats(
            @PathVariable Long programId,
            @Valid @RequestBody SeatBatchCreateRequest request
    ) {
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
}
