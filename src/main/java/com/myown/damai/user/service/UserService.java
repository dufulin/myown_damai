package com.myown.damai.user.service;

import com.myown.damai.user.dao.UserAccountDao;
import com.myown.damai.user.dao.UserSessionDao;
import com.myown.damai.user.dto.AuthResponse;
import com.myown.damai.user.dto.LoginRequest;
import com.myown.damai.user.dto.RegisterRequest;
import com.myown.damai.user.dto.UserProfileResponse;
import com.myown.damai.user.entity.UserAccount;
import com.myown.damai.user.entity.UserSession;
import com.myown.damai.user.entity.UserStatus;
import com.myown.damai.user.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Handles user account authentication, session creation, and session validation.
 */
@Service
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final UserAccountDao userAccountDao;
    private final UserSessionDao userSessionDao;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Duration sessionTtl;

    public UserService(
            UserAccountDao userAccountDao,
            UserSessionDao userSessionDao,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            @Value("${damai.auth.session-ttl-hours:24}") long sessionTtlHours
    ) {
        this.userAccountDao = userAccountDao;
        this.userSessionDao = userSessionDao;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.sessionTtl = Duration.ofHours(sessionTtlHours);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        LOGGER.debug("start registering user, username={}", username);
        if (userAccountDao.existsByUsername(username)) {
            LOGGER.warn("register rejected because username already exists, username={}", username);
            throw new BusinessException("USERNAME_EXISTS", "username already exists", HttpStatus.CONFLICT);
        }

        UserAccount user = new UserAccount(
                username,
                passwordEncoder.encode(request.password()),
                trimToNull(request.nickname()),
                trimToNull(request.phone())
        );
        UserAccount savedUser = saveUser(user);
        LOGGER.info("user registered successfully, username={}, userId={}", savedUser.getUsername(), savedUser.getId());
        return createSession(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        LOGGER.debug("start logging in user, username={}", username);
        Optional<UserAccount> foundUser = userAccountDao.findByUsername(username);
        if (foundUser.isEmpty()) {
            LOGGER.warn("login rejected because username was not found, username={}", username);
            throw new BusinessException("INVALID_CREDENTIALS", "username or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        UserAccount user = foundUser.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            LOGGER.warn(
                    "login rejected because user is not active, username={}, userId={}, status={}",
                    username,
                    user.getId(),
                    user.getStatus()
            );
            throw new BusinessException("INVALID_CREDENTIALS", "username or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            LOGGER.warn("login rejected because password did not match, username={}, userId={}", username, user.getId());
            throw new BusinessException("INVALID_CREDENTIALS", "username or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        LOGGER.info("user logged in successfully, username={}, userId={}", user.getUsername(), user.getId());
        return createSession(user);
    }

    @Transactional
    public void logout(String authorizationHeader) {
        String token = parseBearerToken(authorizationHeader);
        UserSession session = findActiveSession(token);
        session.revoke(Instant.now());
        userSessionDao.save(session);
        LOGGER.info("user logged out successfully, userId={}, sessionId={}", session.getUser().getId(), session.getId());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentUser(String authorizationHeader) {
        String token = parseBearerToken(authorizationHeader);
        UserSession session = findActiveSession(token);
        LOGGER.debug("current user resolved, userId={}, sessionId={}", session.getUser().getId(), session.getId());
        return UserProfileResponse.from(session.getUser());
    }

    private AuthResponse createSession(UserAccount user) {
        String token = tokenService.createToken();
        Instant expiresAt = Instant.now().plus(sessionTtl);
        UserSession session = new UserSession(tokenService.hashToken(token), user, expiresAt);
        userSessionDao.save(session);
        LOGGER.debug(
                "user session created, userId={}, sessionId={}, expiresAt={}",
                user.getId(),
                session.getId(),
                expiresAt
        );
        return new AuthResponse(token, "Bearer", expiresAt, UserProfileResponse.from(user));
    }

    private UserSession findActiveSession(String token) {
        Optional<UserSession> foundSession = userSessionDao.findByTokenHash(tokenService.hashToken(token));
        if (foundSession.isEmpty()) {
            LOGGER.warn("session lookup failed because token was not found");
            throw new BusinessException("UNAUTHORIZED", "invalid or expired token", HttpStatus.UNAUTHORIZED);
        }

        UserSession session = foundSession.get();
        if (!session.isActive(Instant.now())) {
            LOGGER.warn(
                    "session lookup failed because session is inactive, sessionId={}, userId={}",
                    session.getId(),
                    session.getUser().getId()
            );
            throw new BusinessException("UNAUTHORIZED", "invalid or expired token", HttpStatus.UNAUTHORIZED);
        }
        return session;
    }

    private UserAccount saveUser(UserAccount user) {
        try {
            return userAccountDao.save(user);
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("saving user failed because username already exists, username={}", user.getUsername(), exception);
            throw new BusinessException("USERNAME_EXISTS", "username already exists", HttpStatus.CONFLICT);
        }
    }

    private String parseBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            LOGGER.warn("authorization rejected because bearer token is missing");
            throw new BusinessException("UNAUTHORIZED", "missing bearer token", HttpStatus.UNAUTHORIZED);
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            LOGGER.warn("authorization rejected because bearer token is blank");
            throw new BusinessException("UNAUTHORIZED", "missing bearer token", HttpStatus.UNAUTHORIZED);
        }
        return token;
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
