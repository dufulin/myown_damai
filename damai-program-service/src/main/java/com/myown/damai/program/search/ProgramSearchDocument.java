package com.myown.damai.program.search;

import com.myown.damai.program.dto.ProgramDetailResponse;
import com.myown.damai.program.dto.ProgramResponse;
import java.math.BigDecimal;

/**
 * Represents the Elasticsearch aggregated read model for one program.
 */
public record ProgramSearchDocument(
        Long programId,
        BigDecimal minTicketPrice,
        BigDecimal maxTicketPrice,
        ProgramResponse programSummary,
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
        BigDecimal resolvedMinTicketPrice = minTicketPrice == null ? programDetail.program().minTicketPrice() : minTicketPrice;
        BigDecimal resolvedMaxTicketPrice = maxTicketPrice == null ? programDetail.program().maxTicketPrice() : maxTicketPrice;
        ProgramResponse summary = programDetail.program().withTicketPriceRange(resolvedMinTicketPrice, resolvedMaxTicketPrice);
        return new ProgramSearchDocument(programId, resolvedMinTicketPrice, resolvedMaxTicketPrice, summary, programDetail);
    }
}
