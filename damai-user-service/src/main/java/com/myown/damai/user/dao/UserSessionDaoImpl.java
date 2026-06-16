package com.myown.damai.user.dao;

import com.myown.damai.user.entity.UserSession;
import com.myown.damai.user.mapper.UserSessionMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserSessionDaoImpl implements UserSessionDao {

    private static final String NULL_VALUE = "__NULL__";
    private static final String TOKEN_KEY_PREFIX = "damai:user:session:";

    private final UserSessionMapper userSessionMapper;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final Duration nullTtl;
    private final Duration sessionTtl;

    public UserSessionDaoImpl(
            UserSessionMapper userSessionMapper,
            StringRedisTemplate redisTemplate,
            @Value("${damai.cache.redis-enabled:true}") boolean redisEnabled,
            @Value("${damai.cache.null-ttl-minutes:5}") long nullTtlMinutes,
            @Value("${damai.cache.session-ttl-hours:24}") long sessionTtlHours
    ) {
        this.userSessionMapper = userSessionMapper;
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled;
        this.nullTtl = Duration.ofMinutes(nullTtlMinutes);
        this.sessionTtl = Duration.ofHours(sessionTtlHours);
    }

    @Override
    public Optional<UserSession> findByTokenHash(String tokenHash) {
        String key = tokenKey(tokenHash);
        Optional<String> cachedSessionId = getCache(key);
        if (cachedSessionId.isPresent()) {
            String value = cachedSessionId.get();
            if (NULL_VALUE.equals(value)) {
                return Optional.empty();
            }
            return findCachedSessionById(value, key);
        }

        Optional<UserSession> session = Optional.ofNullable(userSessionMapper.selectByTokenHash(tokenHash));
        if (session.isPresent()) {
            putCache(key, String.valueOf(session.get().getId()), sessionTtl);
        } else {
            putCache(key, NULL_VALUE, nullTtl);
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
        putCache(tokenKey(session.getTokenHash()), String.valueOf(session.getId()), sessionTtl);
        return session;
    }

    private Optional<UserSession> findCachedSessionById(String value, String cacheKey) {
        try {
            Long sessionId = Long.valueOf(value);
            Optional<UserSession> session = Optional.ofNullable(userSessionMapper.selectById(sessionId));
            if (session.isEmpty()) {
                deleteCache(cacheKey);
            }
            return session;
        } catch (NumberFormatException exception) {
            deleteCache(cacheKey);
            return Optional.empty();
        }
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_KEY_PREFIX + tokenHash;
    }

    private Optional<String> getCache(String key) {
        if (!redisEnabled) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private void putCache(String key, String value, Duration ttl) {
        if (!redisEnabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, withJitter(ttl));
        } catch (RuntimeException exception) {
            // Redis is an optimization here; database state remains the source of truth.
        }
    }

    private void deleteCache(String key) {
        if (!redisEnabled) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            // Ignore transient cache failures.
        }
    }

    private Duration withJitter(Duration ttl) {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, 60);
        return ttl.plusSeconds(jitterSeconds);
    }
}
