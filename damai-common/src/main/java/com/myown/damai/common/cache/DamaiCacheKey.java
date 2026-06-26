package com.myown.damai.common.cache;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builds normalized Redis keys for all Damai services.
 */
public final class DamaiCacheKey {

    private static final String ROOT = "damai";
    private static final String LOCK_SCOPE = "lock";

    /**
     * Prevents utility class instantiation.
     */
    private DamaiCacheKey() {
    }

    /**
     * Builds a data cache key using the damai:{service}:{resource}:{id...} convention.
     */
    public static String of(String service, String resource, Object... parts) {
        return join(service, resource, parts);
    }

    /**
     * Builds a mutex lock key using the damai:lock:{service}:{resource}:{id...} convention.
     */
    public static String lock(String service, String resource, Object... parts) {
        Object[] lockParts = new Object[parts.length + 1];
        lockParts[0] = resource;
        System.arraycopy(parts, 0, lockParts, 1, parts.length);
        return join(LOCK_SCOPE, service, lockParts);
    }

    /**
     * Joins key segments after trimming unsafe blanks.
     */
    private static String join(String first, String second, Object... parts) {
        String suffix = Arrays.stream(parts)
                .map(DamaiCacheKey::normalizePart)
                .collect(Collectors.joining(":"));
        String prefix = ROOT + ":" + normalizePart(first) + ":" + normalizePart(second);
        return suffix.isEmpty() ? prefix : prefix + ":" + suffix;
    }

    /**
     * Converts one key part to a non-blank string segment.
     */
    private static String normalizePart(Object part) {
        if (part == null) {
            return "null";
        }
        String value = String.valueOf(part).trim();
        return value.isEmpty() ? "blank" : value.replace(':', '_');
    }
}
