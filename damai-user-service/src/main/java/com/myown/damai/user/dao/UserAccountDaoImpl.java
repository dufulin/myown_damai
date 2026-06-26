package com.myown.damai.user.dao;

import com.myown.damai.common.cache.DamaiCacheKey;
import com.myown.damai.common.cache.RedisStringCacheClient;
import com.myown.damai.user.entity.UserAccount;
import com.myown.damai.user.mapper.UserAccountMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Stores user accounts and login indexes with Redis-assisted cache penetration protection.
 */
@Repository
public class UserAccountDaoImpl implements UserAccountDao {

    private final UserAccountMapper userAccountMapper;
    private final RedisStringCacheClient cacheClient;
    private final Duration nullTtl;
    private final Duration userTtl;
    private final Duration mutexLockTtl;
    private final Duration mutexWaitTimeout;
    private final Duration mutexRetryInterval;

    /**
     * Creates the DAO with user persistence and standardized Redis cache support.
     */
    public UserAccountDaoImpl(
            UserAccountMapper userAccountMapper,
            RedisStringCacheClient cacheClient,
            @Value("${damai.cache.null-ttl-minutes:5}") long nullTtlMinutes,
            @Value("${damai.cache.user-ttl-hours:6}") long userTtlHours,
            @Value("${damai.cache.mutex-lock-seconds:5}") long mutexLockSeconds,
            @Value("${damai.cache.mutex-wait-millis:300}") long mutexWaitMillis,
            @Value("${damai.cache.mutex-retry-millis:50}") long mutexRetryMillis
    ) {
        this.userAccountMapper = userAccountMapper;
        this.cacheClient = cacheClient;
        this.nullTtl = Duration.ofMinutes(nullTtlMinutes);
        this.userTtl = Duration.ofHours(userTtlHours);
        this.mutexLockTtl = Duration.ofSeconds(mutexLockSeconds);
        this.mutexWaitTimeout = Duration.ofMillis(mutexWaitMillis);
        this.mutexRetryInterval = Duration.ofMillis(mutexRetryMillis);
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
        return findByLoginIndex(
                mobileKey(mobile),
                DamaiCacheKey.lock("user", "mobile", mobile),
                () -> Optional.ofNullable(userAccountMapper.selectByMobile(mobile))
        );
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return findByLoginIndex(
                emailKey(email),
                DamaiCacheKey.lock("user", "email", email),
                () -> Optional.ofNullable(userAccountMapper.selectByEmail(email))
        );
    }

    @Override
    public UserAccount save(UserAccount user) {
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);
        Objects.requireNonNull(user.getId(), "generated user id must not be null");

        userAccountMapper.insertMobileIndex(user.getId(), user.getMobile(), now);
        cacheClient.put(mobileKey(user.getMobile()), String.valueOf(user.getId()), userTtl);
        if (StringUtils.hasText(user.getEmail())) {
            userAccountMapper.insertEmailIndex(user.getId(), user.getEmail(), now);
            cacheClient.put(emailKey(user.getEmail()), String.valueOf(user.getId()), userTtl);
        }
        return user;
    }

    /**
     * Reads through Redis first and writes null markers for missing login identifiers.
     */
    private Optional<UserAccount> findByLoginIndex(String cacheKey, String lockKey, UserLookup lookup) {
        Optional<Optional<UserAccount>> cachedUser = readCachedLoginIndex(cacheKey);
        if (cachedUser.isPresent()) {
            return cachedUser.get();
        }
        return cacheClient.rebuildWithMutex(
                lockKey,
                mutexLockTtl,
                mutexWaitTimeout,
                mutexRetryInterval,
                () -> readCachedLoginIndex(cacheKey),
                () -> loadLoginIndex(cacheKey, lookup)
        );
    }

    /**
     * Reads one cached login index and preserves null-marker hits as present empty values.
     */
    private Optional<Optional<UserAccount>> readCachedLoginIndex(String cacheKey) {
        Optional<String> cachedUserId = cacheClient.get(cacheKey);
        if (cachedUserId.isPresent()) {
            String value = cachedUserId.get();
            if (cacheClient.isNullValue(value)) {
                return Optional.of(Optional.empty());
            }
            return findCachedUserById(value, cacheKey).map(Optional::of);
        }
        return Optional.empty();
    }

    /**
     * Loads one login index from MySQL and writes a value or null marker back to Redis.
     */
    private Optional<UserAccount> loadLoginIndex(String cacheKey, UserLookup lookup) {
        Optional<UserAccount> user = lookup.find();
        if (user.isPresent()) {
            cacheClient.put(cacheKey, String.valueOf(user.get().getId()), userTtl);
        } else {
            cacheClient.putNull(cacheKey, nullTtl);
        }
        return user;
    }

    /**
     * Loads a cached user id and removes the index cache when it is stale or malformed.
     */
    private Optional<UserAccount> findCachedUserById(String value, String cacheKey) {
        try {
            Long userId = Long.valueOf(value);
            Optional<UserAccount> user = Optional.ofNullable(userAccountMapper.selectById(userId));
            if (user.isEmpty()) {
                cacheClient.delete(cacheKey);
            }
            return user;
        } catch (NumberFormatException exception) {
            cacheClient.delete(cacheKey);
            return Optional.empty();
        }
    }

    /**
     * Builds the cache key for a mobile login index.
     */
    private String mobileKey(String mobile) {
        return DamaiCacheKey.of("user", "mobile", mobile);
    }

    /**
     * Builds the cache key for an email login index.
     */
    private String emailKey(String email) {
        return DamaiCacheKey.of("user", "email", email);
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
