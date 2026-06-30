package com.myown.damai.program.search;

import com.myown.damai.program.dto.ProgramDetailResponse;
import com.myown.damai.program.dto.ProgramResponse;
import com.myown.damai.program.dto.ProgramSearchRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Disables search-index operations when Elasticsearch integration is turned off.
 */
@Component
@ConditionalOnProperty(value = "damai.search.es-enabled", havingValue = "false")
public class NoopProgramSearchGateway implements ProgramSearchGateway {

    /**
     * Reports that no search index is available while search is disabled.
     */
    @Override
    public boolean createProgramDetailIndexIfAbsent() {
        return false;
    }

    /**
     * Ignores document writes while search is disabled.
     */
    @Override
    public void saveProgramDetail(ProgramSearchDocument document) {
    }

    /**
     * Ignores document deletes while search is disabled.
     */
    @Override
    public void deleteProgramDetail(Long programId) {
    }

    /**
     * Returns an empty result while search is disabled.
     */
    @Override
    public Optional<ProgramDetailResponse> findProgramDetail(Long programId) {
        return Optional.empty();
    }

    /**
     * Returns an empty optional so callers can fall back while search is disabled.
     */
    @Override
    public Optional<List<ProgramResponse>> searchPrograms(ProgramSearchRequest request) {
        return Optional.empty();
    }
}
