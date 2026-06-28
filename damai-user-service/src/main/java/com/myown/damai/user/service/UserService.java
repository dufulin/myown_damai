package com.myown.damai.user.service;

import com.myown.damai.common.auth.UserRole;
import com.myown.damai.user.dao.UserAccountDao;
import com.myown.damai.user.dao.UserRefreshTokenDao;
import com.myown.damai.user.dao.UserSessionDao;
import com.myown.damai.user.dto.AuthResponse;
import com.myown.damai.user.dto.AuthenticationResult;
import com.myown.damai.user.dto.LoginRequest;
import com.myown.damai.user.dto.RegisterRequest;
import com.myown.damai.user.dto.UserProfileResponse;
import com.myown.damai.user.dto.UserRoleUpdateRequest;
import com.myown.damai.user.entity.UserAccount;
import com.myown.damai.user.entity.UserSession;
import com.myown.damai.user.entity.UserStatus;
import com.myown.damai.user.entity.UserRefreshToken;
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
    private final UserRefreshTokenDao userRefreshTokenDao;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    /**
     * Creates the authentication service with account, token, and password dependencies.
     */
    public UserService(
            UserAccountDao userAccountDao,
            UserSessionDao userSessionDao,
            UserRefreshTokenDao userRefreshTokenDao,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            @Value("${damai.auth.access-token-ttl-minutes:15}") long accessTokenTtlMinutes,
            @Value("${damai.auth.refresh-token-ttl-days:7}") long refreshTokenTtlDays
    ) {
        this.userAccountDao = userAccountDao;
        this.userSessionDao = userSessionDao;
        this.userRefreshTokenDao = userRefreshTokenDao;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    /**
     * Registers an account and issues its first access and refresh token pair.
     */
    @Transactional
    public AuthenticationResult register(RegisterRequest request) {
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
        return createTokenPair(savedUser);
    }

    /**
     * Verifies credentials and issues a new access and refresh token pair.
     */
    @Transactional
    public AuthenticationResult login(LoginRequest request) {
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
        return createTokenPair(user);
    }

    /**
     * Revokes any valid access token and refresh token supplied by the client.
     */
    @Transactional
    public void logout(String authorizationHeader, String refreshToken) {
        Instant now = Instant.now();
        revokeAccessTokenIfPresent(authorizationHeader, now);
        revokeRefreshTokenIfPresent(refreshToken, now);
        LOGGER.info("user logout token revocation completed");
    }

    /**
     * Rotates a valid refresh token and returns a fresh token pair.
     */
    @Transactional
    public AuthenticationResult refresh(String refreshToken) {
        UserRefreshToken storedRefreshToken = findActiveRefreshToken(refreshToken);
        UserAccount user = storedRefreshToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            LOGGER.warn("refresh rejected because user is not active, userId={}", user.getId());
            throw unauthorized("invalid or expired refresh token");
        }

        // The conditional update ensures concurrent browser tabs cannot both replay one cookie.
        boolean revoked = userRefreshTokenDao.revokeIfActive(storedRefreshToken.getId(), Instant.now());
        if (!revoked) {
            LOGGER.warn(
                    "refresh rejected because token was consumed concurrently, refreshTokenId={}",
                    storedRefreshToken.getId()
            );
            throw unauthorized("invalid or expired refresh token");
        }
        LOGGER.info("refresh token rotated, userId={}, refreshTokenId={}", user.getId(), storedRefreshToken.getId());
        return createTokenPair(user);
    }

    /**
     * Returns the profile for the supplied access token.
     */
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

    /**
     * Updates a human account role while preventing assignment of the internal SYSTEM identity.
     */
    @Transactional
    public UserProfileResponse updateUserRole(Long userId, UserRoleUpdateRequest request) {
        if (request.role() == UserRole.SYSTEM) {
            throw new BusinessException(
                    "INVALID_USER_ROLE",
                    "SYSTEM role cannot be assigned to a user account",
                    HttpStatus.BAD_REQUEST
            );
        }
        UserAccount user = userAccountDao.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "user not found",
                        HttpStatus.NOT_FOUND
                ));
        userAccountDao.updateRole(userId, request.role());
        user.setRole(request.role());
        LOGGER.info("user role updated, userId={}, role={}", userId, request.role());
        return UserProfileResponse.from(user);
    }

    /**
     * Creates and persists one short-lived access token and one rotating refresh token.
     */
    private AuthenticationResult createTokenPair(UserAccount user) {
        Instant now = Instant.now();
        String accessToken = tokenService.createToken();
        Instant accessTokenExpiresAt = now.plus(accessTokenTtl);
        UserSession session = new UserSession(tokenService.hashToken(accessToken), user, accessTokenExpiresAt);
        userSessionDao.save(session);
        String refreshToken = tokenService.createToken();
        Instant refreshTokenExpiresAt = now.plus(refreshTokenTtl);
        UserRefreshToken storedRefreshToken = new UserRefreshToken(
                tokenService.hashToken(refreshToken),
                user,
                refreshTokenExpiresAt
        );
        userRefreshTokenDao.save(storedRefreshToken);
        LOGGER.debug(
                "user token pair created, userId={}, sessionId={}, refreshTokenId={}, accessExpiresAt={}, refreshExpiresAt={}",
                user.getId(),
                session.getId(),
                storedRefreshToken.getId(),
                accessTokenExpiresAt,
                refreshTokenExpiresAt
        );
        AuthResponse response = new AuthResponse(
                accessToken,
                "Bearer",
                accessTokenExpiresAt,
                UserProfileResponse.from(user)
        );
        return new AuthenticationResult(response, refreshToken, refreshTokenExpiresAt);
    }

    /**
     * Finds an active access-token session.
     */
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

    /**
     * Finds and validates an active refresh token.
     */
    private UserRefreshToken findActiveRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            LOGGER.warn("refresh rejected because refresh cookie is missing");
            throw unauthorized("missing refresh token");
        }
        Optional<UserRefreshToken> foundToken = userRefreshTokenDao.findByTokenHash(
                tokenService.hashToken(refreshToken)
        );
        if (foundToken.isEmpty() || !foundToken.get().isActive(Instant.now())) {
            LOGGER.warn("refresh rejected because refresh token is missing or inactive");
            throw unauthorized("invalid or expired refresh token");
        }
        return foundToken.get();
    }

    /**
     * Revokes a bearer access token when one was supplied during logout.
     */
    private void revokeAccessTokenIfPresent(String authorizationHeader, Instant now) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return;
        }
        String token = parseBearerToken(authorizationHeader);
        Optional<UserSession> session = userSessionDao.findByTokenHash(tokenService.hashToken(token));
        if (session.isPresent() && session.get().isActive(now)) {
            session.get().revoke(now);
            userSessionDao.save(session.get());
            LOGGER.info(
                    "access token revoked during logout, userId={}, sessionId={}",
                    session.get().getUser().getId(),
                    session.get().getId()
            );
        }
    }

    /**
     * Revokes a refresh token cookie when one was supplied during logout.
     */
    private void revokeRefreshTokenIfPresent(String refreshToken, Instant now) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        Optional<UserRefreshToken> storedToken = userRefreshTokenDao.findByTokenHash(
                tokenService.hashToken(refreshToken)
        );
        if (storedToken.isPresent() && storedToken.get().isActive(now)) {
            storedToken.get().revoke(now);
            userRefreshTokenDao.save(storedToken.get());
            LOGGER.info(
                    "refresh token revoked during logout, userId={}, refreshTokenId={}",
                    storedToken.get().getUser().getId(),
                    storedToken.get().getId()
            );
        }
    }

    /**
     * Creates a consistent unauthorized business exception.
     */
    private BusinessException unauthorized(String message) {
        return new BusinessException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Persists an account and translates unique-index conflicts.
     */
    private UserAccount saveUser(UserAccount user) {
        try {
            return userAccountDao.save(user);
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("saving user failed because mobile or email already exists, mobile={}", user.getMobile(), exception);
            throw new BusinessException("LOGIN_IDENTIFIER_EXISTS", "mobile or email already exists", HttpStatus.CONFLICT);
        }
    }

    /**
     * Extracts a nonblank bearer token from an Authorization header.
     */
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
