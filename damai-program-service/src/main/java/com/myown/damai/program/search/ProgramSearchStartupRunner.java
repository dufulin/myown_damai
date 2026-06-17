package com.myown.damai.program.search;

import com.myown.damai.program.service.ProgramService;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Initializes Elasticsearch with program detail documents when the service starts.
 */
@Component
@ConditionalOnProperty(value = "damai.search.startup-sync-enabled", havingValue = "true", matchIfMissing = true)
public class ProgramSearchStartupRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramSearchStartupRunner.class);

    private final ProgramService programService;

    /**
     * Creates the startup runner with the program service.
     */
    public ProgramSearchStartupRunner(ProgramService programService) {
        this.programService = programService;
    }

    /**
     * Starts current database program detail synchronization in a background task.
     */
    @Override
    public void run(ApplicationArguments args) {
        CompletableFuture.runAsync(programService::syncProgramDetailsToSearchIndex)
                .exceptionally(exception -> {
                    LOGGER.warn("program detail es startup async sync failed", exception);
                    return null;
                });
    }
}
