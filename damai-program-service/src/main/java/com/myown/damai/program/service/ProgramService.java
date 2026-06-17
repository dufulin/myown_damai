package com.myown.damai.program.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.program.dao.ProgramDao;
import com.myown.damai.program.dto.ProgramCategoryRequest;
import com.myown.damai.program.dto.ProgramCategoryResponse;
import com.myown.damai.program.dto.ProgramCreateRequest;
import com.myown.damai.program.dto.ProgramDetailResponse;
import com.myown.damai.program.dto.ProgramResponse;
import com.myown.damai.program.dto.ProgramShowTimeRequest;
import com.myown.damai.program.dto.ProgramShowTimeResponse;
import com.myown.damai.program.dto.SeatBatchCreateRequest;
import com.myown.damai.program.dto.SeatResponse;
import com.myown.damai.program.dto.TicketCategoryRequest;
import com.myown.damai.program.dto.TicketCategoryResponse;
import com.myown.damai.program.entity.Program;
import com.myown.damai.program.entity.ProgramCategory;
import com.myown.damai.program.entity.ProgramGroup;
import com.myown.damai.program.entity.ProgramShowTime;
import com.myown.damai.program.entity.ProgramTicketPriceRange;
import com.myown.damai.program.entity.Seat;
import com.myown.damai.program.entity.TicketCategory;
import com.myown.damai.program.search.ProgramBloomFilter;
import com.myown.damai.program.search.ProgramSearchGateway;
import com.myown.damai.program.search.ProgramSearchDocument;
import com.myown.damai.program.dto.ProgramSearchRequest;
import com.myown.damai.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Handles program category, program, ticket category, and seat business operations.
 */
@Service
public class ProgramService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramService.class);
    private static final String NULL_VALUE = "__NULL__";
    private static final String PROGRAM_DETAIL_KEY_PREFIX = "damai:program:detail:";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEAT_BATCH_SIZE = 5000;

    private final ProgramDao programDao;
    private final ProgramSearchGateway programSearchGateway;
    private final ProgramBloomFilter programBloomFilter;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean redisEnabled;
    private final java.time.Duration nullTtl;
    private final java.time.Duration programDetailTtl;

    public ProgramService(
            ProgramDao programDao,
            ProgramSearchGateway programSearchGateway,
            ProgramBloomFilter programBloomFilter,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${damai.cache.redis-enabled:true}") boolean redisEnabled,
            @Value("${damai.cache.null-ttl-minutes:5}") long nullTtlMinutes,
            @Value("${damai.cache.program-detail-ttl-hours:2}") long programDetailTtlHours
    ) {
        this.programDao = programDao;
        this.programSearchGateway = programSearchGateway;
        this.programBloomFilter = programBloomFilter;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisEnabled = redisEnabled;
        this.nullTtl = java.time.Duration.ofMinutes(nullTtlMinutes);
        this.programDetailTtl = java.time.Duration.ofHours(programDetailTtlHours);
    }

    /**
     * Creates one program category.
     */
    @Transactional
    public ProgramCategoryResponse createCategory(ProgramCategoryRequest request) {
        Instant now = Instant.now();
        ProgramCategory category = new ProgramCategory();
        category.parentId = request.parentId();
        category.name = request.name().trim();
        category.type = request.type();
        category.createdAt = now;
        category.updatedAt = now;
        category.status = 1;
        ProgramCategory savedCategory = programDao.saveCategory(category);
        LOGGER.info("program category created, categoryId={}, name={}", savedCategory.id, savedCategory.name);
        return ProgramCategoryResponse.from(savedCategory);
    }

    /**
     * Lists all normal program categories.
     */
    @Transactional(readOnly = true)
    public List<ProgramCategoryResponse> listCategories() {
        return programDao.listCategories()
                .stream()
                .map(ProgramCategoryResponse::from)
                .toList();
    }

    /**
     * Creates one program with show times and ticket categories.
     */
    @Transactional
    public ProgramDetailResponse createProgram(ProgramCreateRequest request) {
        validateCategory(request.parentProgramCategoryId(), "parent program category");
        validateCategory(request.programCategoryId(), "program category");

        Instant now = Instant.now();
        Instant recentShowTime = request.showTimes()
                .stream()
                .map(ProgramShowTimeRequest::showTime)
                .min(Comparator.naturalOrder())
                .orElse(now);

        ProgramGroup group = createProgramGroup(recentShowTime, now);
        Program program = createProgramEntity(request, group.id, now);
        Program savedProgram = programDao.saveProgram(program);
        List<ProgramShowTimeResponse> showTimes = saveShowTimes(savedProgram.id, request.showTimes(), savedProgram.areaId, now);
        List<TicketCategoryResponse> ticketCategories = saveTicketCategories(savedProgram.id, request.ticketCategories(), now);

        LOGGER.info("program created, programId={}, title={}", savedProgram.id, savedProgram.title);
        ProgramDetailResponse response = ProgramDetailResponse.of(savedProgram, showTimes, ticketCategories);
        putProgramDetailCache(savedProgram.id, response);
        programBloomFilter.add(savedProgram.id);
        programSearchGateway.saveProgramDetail(buildSearchDocument(savedProgram.id, response, calculatePriceRange(ticketCategories)));
        return response;
    }

    /**
     * Lists normal programs with optional keyword and category filters.
     */
    @Transactional(readOnly = true)
    public List<ProgramResponse> listPrograms(String keyword, Long categoryId, Long areaId, int pageNumber, int pageSize) {
        int normalizedPageNumber = Math.max(pageNumber, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = (normalizedPageNumber - 1) * normalizedPageSize;
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return programDao.listPrograms(normalizedKeyword, categoryId, areaId, normalizedPageSize, offset)
                .stream()
                .map(ProgramResponse::from)
                .toList();
    }

    /**
     * Searches normal programs with Elasticsearch-first smart filtering and sorting.
     */
    @Transactional(readOnly = true)
    public List<ProgramResponse> searchPrograms(
            String keyword,
            Long areaId,
            Long programCategoryId,
            Integer timeType,
            String startDateTime,
            String endDateTime,
            Integer type,
            int pageNumber,
            int pageSize
    ) {
        int normalizedPageNumber = Math.max(pageNumber, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        ProgramSearchRequest request = new ProgramSearchRequest(
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                areaId,
                programCategoryId,
                normalizeSearchType(timeType, 0),
                resolveStartTime(timeType, startDateTime),
                resolveEndTime(timeType, endDateTime),
                normalizeSearchType(type, 1),
                normalizedPageNumber,
                normalizedPageSize
        );
        Optional<List<ProgramResponse>> esPrograms = programSearchGateway.searchPrograms(request);
        if (esPrograms.isPresent()) {
            LOGGER.info("program es search succeeded, count={}", esPrograms.get().size());
            return esPrograms.get();
        }
        LOGGER.warn("program es search unavailable, fallback to mysql list, keyword={}, areaId={}, programCategoryId={}", keyword, areaId, programCategoryId);
        return listPrograms(keyword, programCategoryId, areaId, normalizedPageNumber, normalizedPageSize);
    }

    /**
     * Gets one program detail.
     */
    @Transactional(readOnly = true)
    public ProgramDetailResponse getProgramDetail(Long programId) {
        if (programBloomFilter.isInitialized() && !programBloomFilter.mightContain(programId)) {
            LOGGER.info("program detail bloom filter rejected, programId={}", programId);
            throw new BusinessException("PROGRAM_NOT_FOUND", "program not found", HttpStatus.NOT_FOUND);
        }

        Optional<ProgramDetailResponse> esDetail = programSearchGateway.findProgramDetail(programId);
        if (esDetail.isPresent()) {
            putProgramDetailCache(programId, esDetail.get());
            return esDetail.get();
        }

        Optional<String> cachedValue = getCache(programDetailKey(programId));
        if (cachedValue.isPresent()) {
            String value = cachedValue.get();
            if (NULL_VALUE.equals(value)) {
                LOGGER.info("program detail cache null marker hit, programId={}", programId);
                throw new BusinessException("PROGRAM_NOT_FOUND", "program not found", HttpStatus.NOT_FOUND);
            }
            Optional<ProgramDetailResponse> cachedDetail = deserializeProgramDetail(value, programId);
            if (cachedDetail.isPresent()) {
                LOGGER.info("program detail cache hit, programId={}", programId);
                return cachedDetail.get();
            }
        }

        LOGGER.info("program detail cache miss, programId={}", programId);
        ProgramDetailResponse response = loadProgramDetailFromDatabase(programId, true);
        putProgramDetailCache(programId, response);
        programSearchGateway.saveProgramDetail(buildSearchDocument(programId, response, calculatePriceRange(response.ticketCategories())));
        return response;
    }

    /**
     * Synchronizes all current database program details into the search index.
     */
    @Transactional(readOnly = true)
    public void syncProgramDetailsToSearchIndex() {
        List<Long> programIds = programDao.listNormalProgramIds();
        programBloomFilter.rebuild(programIds);
        boolean createdIndex = programSearchGateway.createProgramDetailIndexIfAbsent();
        if (!createdIndex) {
            LOGGER.info("program detail es startup sync skipped because index already exists or search is unavailable");
            return;
        }
        Map<Long, ProgramTicketPriceRange> priceRangeMap = programDao.listTicketPriceRanges()
                .stream()
                .collect(Collectors.toMap(range -> range.programId, Function.identity()));
        int successCount = 0;
        for (Long programId : programIds) {
            ProgramDetailResponse response = loadProgramDetailFromDatabase(programId, false);
            programSearchGateway.saveProgramDetail(buildSearchDocument(programId, response, priceRangeMap.get(programId)));
            successCount++;
        }
        LOGGER.info("program detail es startup sync finished, count={}", successCount);
    }

    /**
     * Loads one program detail from MySQL and optionally writes a null marker for missing ids.
     */
    private ProgramDetailResponse loadProgramDetailFromDatabase(Long programId, boolean cacheNullMarker) {
        Program program = programDao.findProgramById(programId)
                .orElseThrow(() -> {
                    if (cacheNullMarker) {
                        // Cache a short-lived null marker so repeated invalid ids do not keep hitting MySQL.
                        putCache(programDetailKey(programId), NULL_VALUE, nullTtl);
                    }
                    return new BusinessException("PROGRAM_NOT_FOUND", "program not found", HttpStatus.NOT_FOUND);
                });
        List<ProgramShowTimeResponse> showTimes = programDao.listShowTimes(programId)
                .stream()
                .map(ProgramShowTimeResponse::from)
                .toList();
        List<TicketCategoryResponse> ticketCategories = programDao.listTicketCategories(programId)
                .stream()
                .map(TicketCategoryResponse::from)
                .toList();
        return ProgramDetailResponse.of(program, showTimes, ticketCategories);
    }

    /**
     * Builds the search document with program detail and ticket price range.
     */
    private ProgramSearchDocument buildSearchDocument(
            Long programId,
            ProgramDetailResponse response,
            ProgramTicketPriceRange priceRange
    ) {
        BigDecimal minTicketPrice = priceRange == null ? null : priceRange.minPrice;
        BigDecimal maxTicketPrice = priceRange == null ? null : priceRange.maxPrice;
        return ProgramSearchDocument.of(programId, minTicketPrice, maxTicketPrice, response);
    }

    /**
     * Normalizes optional integer search options.
     */
    private int normalizeSearchType(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * Resolves the inclusive search start time from a time type.
     */
    private Instant resolveStartTime(Integer timeType, String startDateTime) {
        int normalizedTimeType = normalizeSearchType(timeType, 0);
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        if (normalizedTimeType == 0) {
            return null;
        }
        if (normalizedTimeType == 1 || normalizedTimeType == 3 || normalizedTimeType == 4) {
            return today.atStartOfDay(DEFAULT_ZONE).toInstant();
        }
        if (normalizedTimeType == 2) {
            return today.plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant();
        }
        if (normalizedTimeType == 5) {
            return parseRequiredDate(startDateTime, "startDateTime").atStartOfDay(DEFAULT_ZONE).toInstant();
        }
        throw new BusinessException("PROGRAM_SEARCH_TIME_TYPE_INVALID", "timeType is invalid", HttpStatus.BAD_REQUEST);
    }

    /**
     * Resolves the exclusive search end time from a time type.
     */
    private Instant resolveEndTime(Integer timeType, String endDateTime) {
        int normalizedTimeType = normalizeSearchType(timeType, 0);
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        if (normalizedTimeType == 0) {
            return null;
        }
        if (normalizedTimeType == 1) {
            return today.plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant();
        }
        if (normalizedTimeType == 2) {
            return today.plusDays(2).atStartOfDay(DEFAULT_ZONE).toInstant();
        }
        if (normalizedTimeType == 3) {
            return today.plusWeeks(1).plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant();
        }
        if (normalizedTimeType == 4) {
            return today.plusMonths(1).plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant();
        }
        if (normalizedTimeType == 5) {
            return parseRequiredDate(endDateTime, "endDateTime").plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant();
        }
        throw new BusinessException("PROGRAM_SEARCH_TIME_TYPE_INVALID", "timeType is invalid", HttpStatus.BAD_REQUEST);
    }

    /**
     * Parses a required ISO local date string.
     */
    private LocalDate parseRequiredDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("PROGRAM_SEARCH_DATE_REQUIRED", fieldName + " is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new BusinessException("PROGRAM_SEARCH_DATE_INVALID", fieldName + " must use yyyy-MM-dd", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Calculates the ticket price range from in-memory ticket category responses.
     */
    private ProgramTicketPriceRange calculatePriceRange(List<TicketCategoryResponse> ticketCategories) {
        ProgramTicketPriceRange priceRange = new ProgramTicketPriceRange();
        priceRange.minPrice = ticketCategories.stream()
                .map(TicketCategoryResponse::price)
                .min(Comparator.naturalOrder())
                .orElse(null);
        priceRange.maxPrice = ticketCategories.stream()
                .map(TicketCategoryResponse::price)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return priceRange;
    }

    /**
     * Creates a grid of seats for one ticket category.
     */
    @Transactional
    public List<SeatResponse> createSeats(Long programId, SeatBatchCreateRequest request) {
        Program program = findProgramOrThrow(programId);
        TicketCategory ticketCategory = programDao.findTicketCategoryById(request.ticketCategoryId())
                .orElseThrow(() -> new BusinessException("TICKET_CATEGORY_NOT_FOUND", "ticket category not found", HttpStatus.NOT_FOUND));
        if (!program.id.equals(ticketCategory.programId)) {
            throw new BusinessException("TICKET_CATEGORY_MISMATCH", "ticket category does not belong to program", HttpStatus.BAD_REQUEST);
        }
        if (request.endRow() < request.startRow() || request.endCol() < request.startCol()) {
            throw new BusinessException("INVALID_SEAT_RANGE", "seat range is invalid", HttpStatus.BAD_REQUEST);
        }

        int seatCount = (request.endRow() - request.startRow() + 1) * (request.endCol() - request.startCol() + 1);
        if (seatCount > MAX_SEAT_BATCH_SIZE) {
            throw new BusinessException("SEAT_BATCH_TOO_LARGE", "seat batch is too large", HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        List<Seat> seats = buildSeats(programId, ticketCategory, request, now);
        programDao.saveSeats(seats);
        LOGGER.info("program seats created, programId={}, ticketCategoryId={}, seatCount={}", programId, ticketCategory.id, seats.size());
        return programDao.listSeats(programId)
                .stream()
                .map(SeatResponse::from)
                .toList();
    }

    /**
     * Lists seats for a program.
     */
    @Transactional(readOnly = true)
    public List<SeatResponse> listSeats(Long programId) {
        findProgramOrThrow(programId);
        return programDao.listSeats(programId)
                .stream()
                .map(SeatResponse::from)
                .toList();
    }

    /**
     * Validates a category exists.
     */
    private void validateCategory(Long categoryId, String label) {
        programDao.findCategoryById(categoryId)
                .orElseThrow(() -> new BusinessException("PROGRAM_CATEGORY_NOT_FOUND", label + " not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Builds and saves a program group.
     */
    private ProgramGroup createProgramGroup(Instant recentShowTime, Instant now) {
        ProgramGroup group = new ProgramGroup();
        group.programJson = "{}";
        group.recentShowTime = recentShowTime;
        group.createdAt = now;
        group.updatedAt = now;
        group.status = 1;
        return programDao.saveGroup(group);
    }

    /**
     * Builds the core program entity from request data.
     */
    private Program createProgramEntity(ProgramCreateRequest request, Long groupId, Instant now) {
        Program program = new Program();
        program.programGroupId = groupId;
        program.prime = 1;
        program.areaId = request.areaId();
        program.programCategoryId = request.programCategoryId();
        program.parentProgramCategoryId = request.parentProgramCategoryId();
        program.title = request.title().trim();
        program.actor = trimToNull(request.actor());
        program.place = trimToNull(request.place());
        program.itemPicture = trimToNull(request.itemPicture());
        program.preSell = defaultInt(request.preSell(), 0);
        program.preSellInstruction = trimToNull(request.preSellInstruction());
        program.importantNotice = trimToNull(request.importantNotice());
        program.detail = request.detail();
        program.perOrderLimitPurchaseCount = 6;
        program.perAccountLimitPurchaseCount = 6;
        program.refundTicketRule = trimToNull(request.refundTicketRule());
        program.deliveryInstruction = trimToNull(request.deliveryInstruction());
        program.entryRule = trimToNull(request.entryRule());
        program.childPurchase = trimToNull(request.childPurchase());
        program.invoiceSpecification = trimToNull(request.invoiceSpecification());
        program.realTicketPurchaseRule = trimToNull(request.realTicketPurchaseRule());
        program.abnormalOrderDescription = trimToNull(request.abnormalOrderDescription());
        program.kindReminder = trimToNull(request.kindReminder());
        program.performanceDuration = trimToNull(request.performanceDuration());
        program.entryTime = trimToNull(request.entryTime());
        program.permitRefund = 0;
        program.refundExplain = trimToNull(request.refundExplain());
        program.relNameTicketEntrance = 0;
        program.permitChooseSeat = defaultInt(request.permitChooseSeat(), 0);
        program.chooseSeatExplain = trimToNull(request.chooseSeatExplain());
        program.electronicDeliveryTicket = 1;
        program.electronicInvoice = 1;
        program.highHeat = defaultInt(request.highHeat(), 0);
        program.programStatus = 1;
        program.issueTime = request.issueTime() == null ? now : request.issueTime();
        program.createdAt = now;
        program.updatedAt = now;
        program.status = 1;
        return program;
    }

    /**
     * Saves show time entities for a program.
     */
    private List<ProgramShowTimeResponse> saveShowTimes(
            Long programId,
            List<ProgramShowTimeRequest> requests,
            Long defaultAreaId,
            Instant now
    ) {
        return requests.stream()
                .map(request -> createShowTime(programId, request, defaultAreaId, now))
                .map(programDao::saveShowTime)
                .map(ProgramShowTimeResponse::from)
                .toList();
    }

    /**
     * Builds one show time entity.
     */
    private ProgramShowTime createShowTime(Long programId, ProgramShowTimeRequest request, Long defaultAreaId, Instant now) {
        ProgramShowTime showTime = new ProgramShowTime();
        showTime.programId = programId;
        showTime.showTime = request.showTime();
        showTime.showDayTime = toLocalDayStart(request.showTime());
        showTime.showWeekTime = request.showTime().atZone(DEFAULT_ZONE).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        showTime.areaId = request.areaId() == null ? defaultAreaId : request.areaId();
        showTime.createdAt = now;
        showTime.updatedAt = now;
        showTime.status = 1;
        return showTime;
    }

    /**
     * Converts an instant to the start of its local business day.
     */
    private Instant toLocalDayStart(Instant showTime) {
        return showTime.atZone(DEFAULT_ZONE).toLocalDate().atStartOfDay(DEFAULT_ZONE).toInstant();
    }

    /**
     * Saves ticket category entities for a program.
     */
    private List<TicketCategoryResponse> saveTicketCategories(
            Long programId,
            List<TicketCategoryRequest> requests,
            Instant now
    ) {
        return requests.stream()
                .map(request -> createTicketCategory(programId, request, now))
                .map(programDao::saveTicketCategory)
                .map(TicketCategoryResponse::from)
                .toList();
    }

    /**
     * Builds one ticket category entity.
     */
    private TicketCategory createTicketCategory(Long programId, TicketCategoryRequest request, Instant now) {
        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.programId = programId;
        ticketCategory.introduce = request.introduce().trim();
        ticketCategory.price = request.price();
        ticketCategory.totalNumber = request.totalNumber();
        ticketCategory.remainNumber = request.totalNumber();
        ticketCategory.createdAt = now;
        ticketCategory.updatedAt = now;
        ticketCategory.status = 1;
        return ticketCategory;
    }

    /**
     * Finds a program or raises a stable API error.
     */
    private Program findProgramOrThrow(Long programId) {
        return programDao.findProgramById(programId)
                .orElseThrow(() -> new BusinessException("PROGRAM_NOT_FOUND", "program not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Builds the Redis key for one program detail snapshot.
     */
    private String programDetailKey(Long programId) {
        return PROGRAM_DETAIL_KEY_PREFIX + programId;
    }

    /**
     * Serializes and stores one program detail response in Redis.
     */
    private void putProgramDetailCache(Long programId, ProgramDetailResponse response) {
        try {
            putCache(programDetailKey(programId), objectMapper.writeValueAsString(response), programDetailTtl);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("program detail cache serialization failed, programId={}", programId, exception);
        }
    }

    /**
     * Converts cached JSON back to a program detail response.
     */
    private Optional<ProgramDetailResponse> deserializeProgramDetail(String value, Long programId) {
        try {
            return Optional.of(objectMapper.readValue(value, ProgramDetailResponse.class));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("program detail cache deserialization failed, programId={}", programId, exception);
            deleteCache(programDetailKey(programId));
            return Optional.empty();
        }
    }

    /**
     * Reads a raw Redis string value.
     */
    private Optional<String> getCache(String key) {
        if (!redisEnabled) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (RuntimeException exception) {
            LOGGER.warn("redis get failed, key={}", key, exception);
            return Optional.empty();
        }
    }

    /**
     * Writes a raw Redis string value with a TTL.
     */
    private void putCache(String key, String value, java.time.Duration ttl) {
        if (!redisEnabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (RuntimeException exception) {
            LOGGER.warn("redis set failed, key={}", key, exception);
        }
    }

    /**
     * Deletes one Redis cache key.
     */
    private void deleteCache(String key) {
        if (!redisEnabled) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            LOGGER.warn("redis delete failed, key={}", key, exception);
        }
    }

    /**
     * Builds a rectangular seat grid.
     */
    private List<Seat> buildSeats(Long programId, TicketCategory ticketCategory, SeatBatchCreateRequest request, Instant now) {
        return java.util.stream.IntStream.rangeClosed(request.startRow(), request.endRow())
                .boxed()
                .flatMap(row -> java.util.stream.IntStream.rangeClosed(request.startCol(), request.endCol())
                        .mapToObj(col -> createSeat(programId, ticketCategory, request.seatType(), row, col, now)))
                .toList();
    }

    /**
     * Builds one seat entity.
     */
    private Seat createSeat(Long programId, TicketCategory ticketCategory, Integer seatType, Integer row, Integer col, Instant now) {
        Seat seat = new Seat();
        seat.programId = programId;
        seat.ticketCategoryId = ticketCategory.id;
        seat.rowCode = row;
        seat.colCode = col;
        seat.seatType = seatType;
        seat.price = ticketCategory.price;
        seat.sellStatus = 1;
        seat.createdAt = now;
        seat.updatedAt = now;
        seat.status = 1;
        return seat;
    }

    /**
     * Returns a default integer when the input is null.
     */
    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * Trims text and converts blank text to null.
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
