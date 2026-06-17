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
import com.myown.damai.common.exception.BusinessException;
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
        String name = resolveDisplayName(request);
        String mobile = normalizeMobile(resolveMobile(request));
        String email = normalizeOptionalEmail(request.email());
        LOGGER.debug("start registering user, mobile={}, hasEmail={}", mobile, StringUtils.hasText(email));
        if (!StringUtils.hasText(mobile)) {
            LOGGER.warn("register rejected because mobile is missing");
            throw new BusinessException("MOBILE_REQUIRED", "mobile is required", HttpStatus.BAD_REQUEST);
        }
        if (userAccountDao.existsByMobile(mobile)) {
            LOGGER.warn("register rejected because mobile already exists, mobile={}", mobile);
            throw new BusinessException("MOBILE_EXISTS", "mobile already exists", HttpStatus.CONFLICT);
        }
        if (StringUtils.hasText(email) && userAccountDao.existsByEmail(email)) {
            LOGGER.warn("register rejected because email already exists, email={}", email);
            throw new BusinessException("EMAIL_EXISTS", "email already exists", HttpStatus.CONFLICT);
        }

        UserAccount user = new UserAccount(
                name,
                passwordEncoder.encode(request.password()),
                mobile,
                email
        );
        UserAccount savedUser = saveUser(user);
        LOGGER.info("user registered successfully, mobile={}, userId={}", savedUser.getMobile(), savedUser.getId());
        return createSession(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String loginIdentifier = normalizeLoginIdentifier(resolveLoginIdentifier(request));
        LOGGER.debug("start logging in user, loginType={}", isEmail(loginIdentifier) ? "email" : "mobile");
        Optional<UserAccount> foundUser = isEmail(loginIdentifier)
                ? userAccountDao.findByEmail(loginIdentifier)
                : userAccountDao.findByMobile(loginIdentifier);
        if (foundUser.isEmpty()) {
            LOGGER.warn("login rejected because login identifier was not found, loginType={}", isEmail(loginIdentifier) ? "email" : "mobile");
            throw new BusinessException("INVALID_CREDENTIALS", "mobile/email or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        UserAccount user = foundUser.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            LOGGER.warn(
                    "login rejected because user is not active, userId={}, status={}",
                    user.getId(),
                    user.getStatus()
            );
            throw new BusinessException("INVALID_CREDENTIALS", "mobile/email or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            LOGGER.warn("login rejected because password did not match, userId={}", user.getId());
            throw new BusinessException("INVALID_CREDENTIALS", "mobile/email or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        LOGGER.info("user logged in successfully, userId={}, loginType={}", user.getId(), isEmail(loginIdentifier) ? "email" : "mobile");
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

    /**
     * Resolves the current logged-in user id from the bearer token.
     */
    @Transactional(readOnly = true)
    public Long currentUserId(String authorizationHeader) {
        String token = parseBearerToken(authorizationHeader);
        UserSession session = findActiveSession(token);
        return session.getUser().getId();
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
            LOGGER.warn("saving user failed because mobile or email already exists, mobile={}", user.getMobile(), exception);
            throw new BusinessException("LOGIN_IDENTIFIER_EXISTS", "mobile or email already exists", HttpStatus.CONFLICT);
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

    /**
     * Resolves a display name while keeping username as a compatibility alias.
     */
    private String resolveDisplayName(RegisterRequest request) {
        if (StringUtils.hasText(request.name())) {
            return request.name().trim();
        }
        if (StringUtils.hasText(request.username())) {
            return request.username().trim();
        }
        return null;
    }

    /**
     * Resolves a mobile number while keeping phone as a compatibility alias.
     */
    private String resolveMobile(RegisterRequest request) {
        if (StringUtils.hasText(request.mobile())) {
            return request.mobile();
        }
        return request.phone();
    }

    /**
     * Resolves the login identifier from new and legacy request fields.
     */
    private String resolveLoginIdentifier(LoginRequest request) {
        if (StringUtils.hasText(request.login())) {
            return request.login();
        }
        return request.username();
    }

    /**
     * Normalizes a mobile number for storage and lookup.
     */
    private String normalizeMobile(String mobile) {
        return StringUtils.hasText(mobile) ? mobile.trim() : null;
    }

    /**
     * Normalizes the optional email address for storage and lookup.
     */
    private String normalizeOptionalEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
    }

    /**
     * Normalizes the required login identifier.
     */
    private String normalizeLoginIdentifier(String loginIdentifier) {
        if (!StringUtils.hasText(loginIdentifier)) {
            throw new BusinessException("LOGIN_REQUIRED", "mobile or email is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = loginIdentifier.trim();
        return isEmail(normalized) ? normalized.toLowerCase() : normalized;
    }

    /**
     * Checks whether a login identifier should use the email index.
     */
    private boolean isEmail(String loginIdentifier) {
        return loginIdentifier.contains("@");
    }
}
