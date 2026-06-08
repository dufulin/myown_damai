package com.myown.damai.user.service;

import com.myown.damai.user.dto.AuthResponse;
import com.myown.damai.user.dto.LoginRequest;
import com.myown.damai.user.dto.RegisterRequest;
import com.myown.damai.user.dto.UserProfileResponse;
import com.myown.damai.user.entity.UserAccount;
import com.myown.damai.user.entity.UserSession;
import com.myown.damai.user.entity.UserStatus;
import com.myown.damai.user.exception.BusinessException;
import com.myown.damai.user.repository.UserAccountRepository;
import com.myown.damai.user.repository.UserSessionRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserAccountRepository userAccountRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Duration sessionTtl;

    public UserService(
            UserAccountRepository userAccountRepository,
            UserSessionRepository userSessionRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            @Value("${damai.auth.session-ttl-hours:24}") long sessionTtlHours
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.sessionTtl = Duration.ofHours(sessionTtlHours);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (userAccountRepository.existsByUsername(username)) {
            throw new BusinessException("USERNAME_EXISTS", "username already exists", HttpStatus.CONFLICT);
        }

        UserAccount user = new UserAccount(
                username,
                passwordEncoder.encode(request.password()),
                trimToNull(request.nickname()),
                trimToNull(request.phone())
        );
        UserAccount savedUser = userAccountRepository.save(user);
        return createSession(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "username or password is incorrect", HttpStatus.UNAUTHORIZED));

        if (user.getStatus() != UserStatus.ACTIVE || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "username or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        return createSession(user);
    }

    @Transactional
    public void logout(String authorizationHeader) {
        String token = parseBearerToken(authorizationHeader);
        UserSession session = findActiveSession(token);
        session.revoke(Instant.now());
        userSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentUser(String authorizationHeader) {
        String token = parseBearerToken(authorizationHeader);
        UserSession session = findActiveSession(token);
        return UserProfileResponse.from(session.getUser());
    }

    private AuthResponse createSession(UserAccount user) {
        String token = tokenService.createToken();
        Instant expiresAt = Instant.now().plus(sessionTtl);
        UserSession session = new UserSession(tokenService.hashToken(token), user, expiresAt);
        userSessionRepository.save(session);
        return new AuthResponse(token, "Bearer", expiresAt, UserProfileResponse.from(user));
    }

    private UserSession findActiveSession(String token) {
        UserSession session = userSessionRepository.findByTokenHash(tokenService.hashToken(token))
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "invalid or expired token", HttpStatus.UNAUTHORIZED));
        if (!session.isActive(Instant.now())) {
            throw new BusinessException("UNAUTHORIZED", "invalid or expired token", HttpStatus.UNAUTHORIZED);
        }
        return session;
    }

    private String parseBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException("UNAUTHORIZED", "missing bearer token", HttpStatus.UNAUTHORIZED);
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
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
