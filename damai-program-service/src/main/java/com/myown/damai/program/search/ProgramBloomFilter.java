package com.myown.damai.program.search;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides an in-memory Bloom filter for fast invalid program id rejection.
 */
@Component
public class ProgramBloomFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramBloomFilter.class);

    private final int bitSize;
    private final int hashCount;
    private volatile BitSet bits;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Creates the Bloom filter with configurable bit and hash sizes.
     */
    public ProgramBloomFilter(
            @Value("${damai.search.bloom.bit-size:1048576}") int bitSize,
            @Value("${damai.search.bloom.hash-count:5}") int hashCount
    ) {
        this.bitSize = bitSize;
        this.hashCount = hashCount;
        this.bits = new BitSet(bitSize);
    }

    /**
     * Rebuilds the Bloom filter with the current normal program id collection.
     */
    public synchronized void rebuild(Collection<Long> programIds) {
        BitSet nextBits = new BitSet(bitSize);
        for (Long programId : programIds) {
            addTo(nextBits, programId);
        }
        this.bits = nextBits;
        this.initialized.set(true);
        LOGGER.info("program bloom filter rebuilt, programCount={}, bitSize={}, hashCount={}", programIds.size(), bitSize, hashCount);
    }

    /**
     * Adds one program id to the current Bloom filter.
     */
    public void add(Long programId) {
        addTo(bits, programId);
    }

    /**
     * Returns true when the filter is ready to reject impossible program ids.
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Returns whether a program id may exist.
     */
    public boolean mightContain(Long programId) {
        BitSet currentBits = bits;
        for (int index = 0; index < hashCount; index++) {
            if (!currentBits.get(hash(programId, index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds one program id into the provided bit set.
     */
    private void addTo(BitSet targetBits, Long programId) {
        for (int index = 0; index < hashCount; index++) {
            targetBits.set(hash(programId, index));
        }
    }

    /**
     * Produces a stable positive hash position for one program id and salt.
     */
    private int hash(Long programId, int salt) {
        byte[] bytes = (programId + ":" + salt).getBytes(StandardCharsets.UTF_8);
        int hash = 0x811c9dc5;
        for (byte value : bytes) {
            hash ^= value;
            hash *= 0x01000193;
        }
        return Math.floorMod(hash, bitSize);
    }
}
