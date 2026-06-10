package com.myown.damai.user.dao;

import com.myown.damai.user.entity.UserAccount;
import com.myown.damai.user.mapper.UserAccountMapper;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserAccountDaoImpl implements UserAccountDao {

    private static final String NULL_VALUE = "__NULL__";
    private static final String USERNAME_KEY_PREFIX = "damai:user:username:";

    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final Duration nullTtl;
    private final Duration userTtl;

    public UserAccountDaoImpl(
            UserAccountMapper userAccountMapper,
            StringRedisTemplate redisTemplate,
            @Value("${damai.cache.redis-enabled:true}") boolean redisEnabled,
            @Value("${damai.cache.null-ttl-minutes:5}") long nullTtlMinutes,
            @Value("${damai.cache.user-ttl-hours:6}") long userTtlHours
    ) {
        this.userAccountMapper = userAccountMapper;
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled;
        this.nullTtl = Duration.ofMinutes(nullTtlMinutes);
        this.userTtl = Duration.ofHours(userTtlHours);
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        String key = usernameKey(username);
        Optional<String> cachedUserId = getCache(key);
        if (cachedUserId.isPresent()) {
            String value = cachedUserId.get();
            if (NULL_VALUE.equals(value)) {
                return Optional.empty();
            }
            return findCachedUserById(value, key);
        }

        Optional<UserAccount> user = Optional.ofNullable(userAccountMapper.selectByUsername(username));
        if (user.isPresent()) {
            putCache(key, String.valueOf(user.get().getId()), userTtl);
        } else {
            putCache(key, NULL_VALUE, nullTtl);
        }
        return user;
    }

    @Override
    public UserAccount save(UserAccount user) {
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);
        Objects.requireNonNull(user.getId(), "generated user id must not be null");
        putCache(usernameKey(user.getUsername()), String.valueOf(user.getId()), userTtl);
        return user;
    }

    private Optional<UserAccount> findCachedUserById(String value, String cacheKey) {
        try {
            Long userId = Long.valueOf(value);
            Optional<UserAccount> user = Optional.ofNullable(userAccountMapper.selectById(userId));
            if (user.isEmpty()) {
                deleteCache(cacheKey);
            }
            return user;
        } catch (NumberFormatException exception) {
            deleteCache(cacheKey);
            return Optional.empty();
        }
    }

    private String usernameKey(String username) {
        return USERNAME_KEY_PREFIX + username;
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
