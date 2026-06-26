package com.myown.damai.program.search;

import com.myown.damai.program.dto.ProgramDetailResponse;
import com.myown.damai.program.dto.ProgramResponse;
import com.myown.damai.program.dto.ProgramSearchRequest;
import java.util.List;
import java.util.Optional;

/**
 * Defines search-index operations for program detail documents.
 */
public interface ProgramSearchGateway {

    /**
     * Creates the program detail index when it does not exist.
     */
    boolean createProgramDetailIndexIfAbsent();

    /**
     * Stores one program detail document in the search index.
     */
    void saveProgramDetail(ProgramSearchDocument document);

    /**
     * Deletes one program detail document from the search index.
     */
    void deleteProgramDetail(Long programId);

    /**
     * Finds one program detail document by program id.
     */
    Optional<ProgramDetailResponse> findProgramDetail(Long programId);

    /**
     * Searches program summary data from the search index.
     */
    Optional<List<ProgramResponse>> searchPrograms(ProgramSearchRequest request);
}
