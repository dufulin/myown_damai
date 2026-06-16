package com.myown.damai.user.dao;

import com.myown.damai.user.entity.UserAccount;
import com.myown.damai.user.mapper.UserAccountMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Stores user accounts and login indexes with Redis-assisted cache penetration protection.
 */
@Repository
public class UserAccountDaoImpl implements UserAccountDao {

    private static final String NULL_VALUE = "__NULL__";
    private static final String MOBILE_KEY_PREFIX = "damai:user:mobile:";
    private static final String EMAIL_KEY_PREFIX = "damai:user:email:";

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
    public boolean existsByMobile(String mobile) {
        return findByMobile(mobile).isPresent();
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public Optional<UserAccount> findByMobile(String mobile) {
        return findByLoginIndex(mobileKey(mobile), () -> Optional.ofNullable(userAccountMapper.selectByMobile(mobile)));
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return findByLoginIndex(emailKey(email), () -> Optional.ofNullable(userAccountMapper.selectByEmail(email)));
    }

    @Override
    public UserAccount save(UserAccount user) {
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);
        Objects.requireNonNull(user.getId(), "generated user id must not be null");

        userAccountMapper.insertMobileIndex(user.getId(), user.getMobile(), now);
        putCache(mobileKey(user.getMobile()), String.valueOf(user.getId()), userTtl);
        if (StringUtils.hasText(user.getEmail())) {
            userAccountMapper.insertEmailIndex(user.getId(), user.getEmail(), now);
            putCache(emailKey(user.getEmail()), String.valueOf(user.getId()), userTtl);
        }
        return user;
    }

    /**
     * Reads through Redis first and writes null markers for missing login identifiers.
     */
    private Optional<UserAccount> findByLoginIndex(String cacheKey, UserLookup lookup) {
        Optional<String> cachedUserId = getCache(cacheKey);
        if (cachedUserId.isPresent()) {
            String value = cachedUserId.get();
            if (NULL_VALUE.equals(value)) {
                return Optional.empty();
            }
            return findCachedUserById(value, cacheKey);
        }

        Optional<UserAccount> user = lookup.find();
        if (user.isPresent()) {
            putCache(cacheKey, String.valueOf(user.get().getId()), userTtl);
        } else {
            putCache(cacheKey, NULL_VALUE, nullTtl);
        }
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

    private String mobileKey(String mobile) {
        return MOBILE_KEY_PREFIX + mobile;
    }

    private String emailKey(String email) {
        return EMAIL_KEY_PREFIX + email;
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

    /**
     * Defers the database lookup so mobile and email paths can share cache logic.
     */
    @FunctionalInterface
    private interface UserLookup {

        /**
         * Loads a user from the database.
         */
        Optional<UserAccount> find();
    }
}
