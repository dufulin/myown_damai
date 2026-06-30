package com.myown.damai.program.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.cache.RedisStringCacheClient;
import com.myown.damai.program.dao.ProgramDao;
import com.myown.damai.program.dto.ProgramResponse;
import com.myown.damai.program.entity.Program;
import com.myown.damai.program.messaging.ProgramChangeEventPublisher;
import com.myown.damai.program.search.ProgramBloomFilter;
import com.myown.damai.program.search.ProgramSearchGateway;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Verifies that stale or empty search read models do not hide authoritative MySQL program data.
 */
class ProgramServiceReadModelFallbackTest {

    /**
     * Falls back to MySQL when Elasticsearch successfully returns an empty result page.
     */
    @Test
    void shouldFallbackToDatabaseWhenSearchIndexReturnsEmptyPage() {
        ProgramDao programDao = mock(ProgramDao.class);
        ProgramSearchGateway searchGateway = mock(ProgramSearchGateway.class);
        Program program = createProgram();
        when(searchGateway.searchPrograms(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(List.of()));
        when(programDao.listPrograms("Demo", null, null, 10, 0)).thenReturn(List.of(program));
        when(programDao.listTicketPriceRangesByProgramIds(List.of(program.id))).thenReturn(List.of());

        ProgramService programService = createProgramService(programDao, searchGateway);

        List<ProgramResponse> result = programService.listPrograms("Demo", null, null, 1, 10);

        assertThat(result).extracting(ProgramResponse::id).containsExactly(program.id);
    }

    /**
     * Creates a minimal normal program returned by the mocked database.
     */
    private Program createProgram() {
        Program program = new Program();
        program.id = 1L;
        program.title = "Demo Concert";
        program.areaId = 2L;
        program.programCategoryId = 1L;
        program.parentProgramCategoryId = 1L;
        program.permitChooseSeat = 0;
        program.highHeat = 1;
        program.issueTime = Instant.parse("2026-06-30T00:00:00Z");
        return program;
    }

    /**
     * Creates the service with only dependencies needed by the read-model fallback test.
     */
    private ProgramService createProgramService(ProgramDao programDao, ProgramSearchGateway searchGateway) {
        return new ProgramService(
                programDao,
                searchGateway,
                mock(ProgramBloomFilter.class),
                mock(ProgramChangeEventPublisher.class),
                mock(RedisStringCacheClient.class),
                new ObjectMapper(),
                5,
                2,
                5,
                5,
                500,
                50,
                10_000
        );
    }
}
