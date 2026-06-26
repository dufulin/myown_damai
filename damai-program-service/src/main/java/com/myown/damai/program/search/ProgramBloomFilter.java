package com.myown.damai.program.search;

import com.myown.damai.common.cache.InMemoryLongBloomFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides an in-memory Bloom filter for fast invalid program id rejection.
 */
@Component
public class ProgramBloomFilter extends InMemoryLongBloomFilter {

    /**
     * Creates the Bloom filter with configurable bit and hash sizes.
     */
    public ProgramBloomFilter(
            @Value("${damai.search.bloom.bit-size:1048576}") int bitSize,
            @Value("${damai.search.bloom.hash-count:5}") int hashCount
    ) {
        super("program", bitSize, hashCount);
    }
}
