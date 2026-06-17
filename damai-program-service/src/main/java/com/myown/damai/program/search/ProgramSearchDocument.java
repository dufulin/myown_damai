package com.myown.damai.program.search;

import com.myown.damai.program.dto.ProgramDetailResponse;
import java.math.BigDecimal;

/**
 * Represents the Elasticsearch document for one program detail.
 */
public record ProgramSearchDocument(
        Long programId,
        BigDecimal minTicketPrice,
        BigDecimal maxTicketPrice,
        ProgramDetailResponse programDetail
) {

    /**
     * Builds one search document from detail data and price range values.
     */
    public static ProgramSearchDocument of(
            Long programId,
            BigDecimal minTicketPrice,
            BigDecimal maxTicketPrice,
            ProgramDetailResponse programDetail
    ) {
        return new ProgramSearchDocument(programId, minTicketPrice, maxTicketPrice, programDetail);
    }
}
