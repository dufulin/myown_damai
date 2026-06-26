package com.myown.damai.user.dao;

import com.myown.damai.common.cache.DamaiCacheKey;
import com.myown.damai.common.cache.RedisStringCacheClient;
import com.myown.damai.user.entity.UserSession;
import com.myown.damai.user.mapper.UserSessionMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Stores user sessions with Redis-assisted token lookup caching.
 */
@Repository
public class UserSessionDaoImpl implements UserSessionDao {

    private final UserSessionMapper userSessionMapper;
    private final RedisStringCacheClient cacheClient;
    private final Duration nullTtl;
    private final Duration sessionTtl;
    private final Duration mutexLockTtl;
    private final Duration mutexWaitTimeout;
    private final Duration mutexRetryInterval;

    /**
     * Creates the DAO with session persistence and standardized Redis cache support.
     */
    public UserSessionDaoImpl(
            UserSessionMapper userSessionMapper,
            RedisStringCacheClient cacheClient,
            @Value("${damai.cache.null-ttl-minutes:5}") long nullTtlMinutes,
            @Value("${damai.cache.session-ttl-hours:24}") long sessionTtlHours,
            @Value("${damai.cache.mutex-lock-seconds:5}") long mutexLockSeconds,
            @Value("${damai.cache.mutex-wait-millis:300}") long mutexWaitMillis,
            @Value("${damai.cache.mutex-retry-millis:50}") long mutexRetryMillis
    ) {
        this.userSessionMapper = userSessionMapper;
        this.cacheClient = cacheClient;
        this.nullTtl = Duration.ofMinutes(nullTtlMinutes);
        this.sessionTtl = Duration.ofHours(sessionTtlHours);
        this.mutexLockTtl = Duration.ofSeconds(mutexLockSeconds);
        this.mutexWaitTimeout = Duration.ofMillis(mutexWaitMillis);
        this.mutexRetryInterval = Duration.ofMillis(mutexRetryMillis);
    }

    @Override
    public Optional<UserSession> findByTokenHash(String tokenHash) {
        String key = tokenKey(tokenHash);
        Optional<Optional<UserSession>> cachedSession = readCachedSessionIndex(key);
        if (cachedSession.isPresent()) {
            return cachedSession.get();
        }
        return cacheClient.rebuildWithMutex(
                DamaiCacheKey.lock("user", "session", tokenHash),
                mutexLockTtl,
                mutexWaitTimeout,
                mutexRetryInterval,
                () -> readCachedSessionIndex(key),
                () -> loadSessionIndex(key, tokenHash)
        );
    }

    /**
     * Reads one cached session index and preserves null-marker hits as present empty values.
     */
    private Optional<Optional<UserSession>> readCachedSessionIndex(String key) {
        Optional<String> cachedSessionId = cacheClient.get(key);
        if (cachedSessionId.isPresent()) {
            String value = cachedSessionId.get();
            if (cacheClient.isNullValue(value)) {
                return Optional.of(Optional.empty());
            }
            return findCachedSessionById(value, key).map(Optional::of);
        }
        return Optional.empty();
    }

    /**
     * Loads one session index from MySQL and writes a value or null marker back to Redis.
     */
    private Optional<UserSession> loadSessionIndex(String key, String tokenHash) {
        Optional<UserSession> session = Optional.ofNullable(userSessionMapper.selectByTokenHash(tokenHash));
        if (session.isPresent()) {
            cacheClient.put(key, String.valueOf(session.get().getId()), sessionTtl);
        } else {
            cacheClient.putNull(key, nullTtl);
        }
        return session;
    }

    @Override
    public UserSession save(UserSession session) {
        if (session.getId() == null) {
            session.setCreatedAt(Instant.now());
            userSessionMapper.insert(session);
            Objects.requireNonNull(session.getId(), "generated session id must not be null");
        } else {
            userSessionMapper.update(session);
        }
        cacheClient.put(tokenKey(session.getTokenHash()), String.valueOf(session.getId()), sessionTtl);
        return session;
    }

    /**
     * Loads a cached session id and removes the token cache when it is stale or malformed.
     */
    private Optional<UserSession> findCachedSessionById(String value, String cacheKey) {
        try {
            Long sessionId = Long.valueOf(value);
            Optional<UserSession> session = Optional.ofNullable(userSessionMapper.selectById(sessionId));
            if (session.isEmpty()) {
                cacheClient.delete(cacheKey);
            }
            return session;
        } catch (NumberFormatException exception) {
            cacheClient.delete(cacheKey);
            return Optional.empty();
        }
    }

    /**
     * Builds the cache key for a token hash lookup.
     */
    private String tokenKey(String tokenHash) {
        return DamaiCacheKey.of("user", "session", tokenHash);
    }
}
