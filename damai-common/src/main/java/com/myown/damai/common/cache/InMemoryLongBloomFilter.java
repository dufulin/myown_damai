package com.myown.damai.common.cache;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a reusable in-memory Bloom filter for Long identifiers.
 */
public class InMemoryLongBloomFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryLongBloomFilter.class);

    private final String name;
    private final int bitSize;
    private final int hashCount;
    private volatile BitSet bits;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Creates a Bloom filter with a log name and hash settings.
     */
    public InMemoryLongBloomFilter(String name, int bitSize, int hashCount) {
        this.name = name;
        this.bitSize = bitSize;
        this.hashCount = hashCount;
        this.bits = new BitSet(bitSize);
    }

    /**
     * Rebuilds the filter from the full current identifier collection.
     */
    public synchronized void rebuild(Collection<Long> ids) {
        BitSet nextBits = new BitSet(bitSize);
        for (Long id : ids) {
            addTo(nextBits, id);
        }
        this.bits = nextBits;
        this.initialized.set(true);
        LOGGER.info("{} bloom filter rebuilt, count={}, bitSize={}, hashCount={}", name, ids.size(), bitSize, hashCount);
    }

    /**
     * Adds one identifier to the current filter.
     */
    public void add(Long id) {
        addTo(bits, id);
    }

    /**
     * Returns true when the filter is ready to reject impossible ids.
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Returns whether the identifier may exist.
     */
    public boolean mightContain(Long id) {
        BitSet currentBits = bits;
        for (int index = 0; index < hashCount; index++) {
            if (!currentBits.get(hash(id, index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds one identifier into the provided bit set.
     */
    private void addTo(BitSet targetBits, Long id) {
        if (id == null) {
            return;
        }
        for (int index = 0; index < hashCount; index++) {
            targetBits.set(hash(id, index));
        }
    }

    /**
     * Produces a stable positive hash position for one identifier and salt.
     */
    private int hash(Long id, int salt) {
        byte[] bytes = (id + ":" + salt).getBytes(StandardCharsets.UTF_8);
        int hash = 0x811c9dc5;
        for (byte value : bytes) {
            hash ^= value;
            hash *= 0x01000193;
        }
        return Math.floorMod(hash, bitSize);
    }
}
