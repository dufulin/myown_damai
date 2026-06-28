package com.myown.damai.user.service;

import com.myown.damai.user.dao.UserRefreshTokenDao;
import com.myown.damai.user.dao.UserSessionDao;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically removes expired and revoked access and refresh token records.
 */
@Component
public class ExpiredTokenCleanupTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpiredTokenCleanupTask.class);

    private final UserSessionDao userSessionDao;
    private final UserRefreshTokenDao userRefreshTokenDao;
    private final Duration retention;

    /**
     * Creates the cleanup task with token DAOs and the audit retention period.
     */
    public ExpiredTokenCleanupTask(
            UserSessionDao userSessionDao,
            UserRefreshTokenDao userRefreshTokenDao,
            @Value("${damai.auth.cleanup-retention-hours:24}") long cleanupRetentionHours
    ) {
        this.userSessionDao = userSessionDao;
        this.userRefreshTokenDao = userRefreshTokenDao;
        this.retention = Duration.ofHours(cleanupRetentionHours);
    }

    /**
     * Deletes token records after their configured audit retention period.
     */
    @Scheduled(cron = "${damai.auth.cleanup-cron:0 0 * * * *}", zone = "${damai.auth.cleanup-zone:Asia/Shanghai}")
    @Transactional
    public void cleanupInactiveTokens() {
        Instant deleteBefore = Instant.now().minus(retention);
        int refreshTokenCount = userRefreshTokenDao.deleteInactiveBefore(deleteBefore);
        int accessTokenCount = userSessionDao.deleteInactiveBefore(deleteBefore);
        if (refreshTokenCount > 0 || accessTokenCount > 0) {
            LOGGER.info(
                    "inactive token cleanup completed, accessTokenCount={}, refreshTokenCount={}, deleteBefore={}",
                    accessTokenCount,
                    refreshTokenCount,
                    deleteBefore
            );
        }
    }
}
