package com.myown.damai.user.controller;

import com.myown.damai.common.auth.UserRole;
import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import com.myown.damai.user.dto.AuthResponse;
import com.myown.damai.user.dto.AuthenticationResult;
import com.myown.damai.user.dto.LoginRequest;
import com.myown.damai.user.dto.RegisterRequest;
import com.myown.damai.user.dto.TicketUserCreateRequest;
import com.myown.damai.user.dto.TicketUserResponse;
import com.myown.damai.user.dto.TicketUserValidationRequest;
import com.myown.damai.user.dto.UserProfileResponse;
import com.myown.damai.user.dto.UserRoleUpdateRequest;
import com.myown.damai.user.service.TicketUserService;
import com.myown.damai.user.service.RefreshTokenCookieService;
import com.myown.damai.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides user registration, login, logout, and profile query APIs.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final TicketUserService ticketUserService;
    private final RefreshTokenCookieService refreshTokenCookieService;

    /**
     * Creates the controller with user and ticket buyer services.
     */
    public UserController(
            UserService userService,
            TicketUserService ticketUserService,
            RefreshTokenCookieService refreshTokenCookieService
    ) {
        this.userService = userService;
        this.ticketUserService = ticketUserService;
        this.refreshTokenCookieService = refreshTokenCookieService;
    }

    /**
     * Registers a user and places the refresh token in an HttpOnly cookie.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        String mobile = request.mobile() != null ? request.mobile() : request.phone();
        LOGGER.info("user register request received, mobile={}", mobile);
        AuthenticationResult result = userService.register(request);
        LOGGER.info(
                "user register request succeeded, mobile={}, userId={}",
                result.response().user().mobile(),
                result.response().user().id()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result).toString())
                .body(ApiResponse.success(result.response()));
    }

    /**
     * Logs in a user and places the refresh token in an HttpOnly cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        String loginIdentifier = request.login() != null ? request.login() : request.username();
        LOGGER.info("user login request received, loginType={}", loginIdentifier != null && loginIdentifier.contains("@") ? "email" : "mobile");
        AuthenticationResult result = userService.login(request);
        LOGGER.info(
                "user login request succeeded, userId={}, mobile={}",
                result.response().user().id(),
                result.response().user().mobile()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result).toString())
                .body(ApiResponse.success(result.response()));
    }

    /**
     * Rotates the HttpOnly refresh token and returns a new short-lived access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(
                    value = "${damai.auth.refresh-cookie.name:damai_refresh_token}",
                    required = false
            ) String refreshToken
    ) {
        LOGGER.info("user token refresh request received, hasRefreshCookie={}", refreshToken != null);
        AuthenticationResult result = userService.refresh(refreshToken);
        LOGGER.info("user token refresh request succeeded, userId={}", result.response().user().id());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result).toString())
                .body(ApiResponse.success(result.response()));
    }

    /**
     * Revokes supplied tokens and clears the refresh-token cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @CookieValue(
                    value = "${damai.auth.refresh-cookie.name:damai_refresh_token}",
                    required = false
            ) String refreshToken
    ) {
        LOGGER.info(
                "user logout request received, hasAuthorizationHeader={}, hasRefreshCookie={}",
                authorizationHeader != null,
                refreshToken != null
        );
        userService.logout(authorizationHeader, refreshToken);
        LOGGER.info("user logout request succeeded");
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clear().toString())
                .body(ApiResponse.success());
    }

    /**
     * Returns the currently authenticated user profile.
     */
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> currentUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        LOGGER.debug("current user request received, hasAuthorizationHeader={}", authorizationHeader != null);
        UserProfileResponse userProfile = userService.currentUser(authorizationHeader);
        LOGGER.debug(
                "current user request succeeded, username={}, userId={}",
                userProfile.username(),
                userProfile.id()
        );
        return ApiResponse.success(userProfile);
    }

    /**
     * Builds the refresh-token cookie for a successful authentication result.
     */
    private ResponseCookie refreshTokenCookie(AuthenticationResult result) {
        return refreshTokenCookieService.create(result.refreshToken(), result.refreshExpiresAt());
    }

    /**
     * Updates one account role through the administrator-only gateway route.
     */
    @PutMapping("/{userId}/role")
    public ApiResponse<UserProfileResponse> updateUserRole(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String operatorIdHeader,
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable Long userId,
            @Valid @RequestBody UserRoleUpdateRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.ADMIN);
        Long operatorId = AuthenticatedUserHeader.resolveRequired(operatorIdHeader);
        LOGGER.info(
                "user role update request received, operatorId={}, userId={}, role={}",
                operatorId,
                userId,
                request.role()
        );
        UserProfileResponse response = userService.updateUserRole(userId, request);
        LOGGER.info(
                "user role update request succeeded, operatorId={}, userId={}, role={}",
                operatorId,
                userId,
                response.role()
        );
        return ApiResponse.success(response);
    }

    /**
     * Lists real-name ticket buyers for the current logged-in user.
     */
    @GetMapping("/ticket-users")
    public ApiResponse<List<TicketUserResponse>> listTicketUsers(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        LOGGER.info("ticket user list request received, userId={}", authenticatedUserId);
        List<TicketUserResponse> ticketUsers = ticketUserService.listTicketUsers(authenticatedUserId);
        LOGGER.info("ticket user list request succeeded, count={}", ticketUsers.size());
        return ApiResponse.success(ticketUsers);
    }

    /**
     * Creates one real-name ticket buyer for the current logged-in user.
     */
    @PostMapping("/ticket-users")
    public ResponseEntity<ApiResponse<TicketUserResponse>> createTicketUser(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @Valid @RequestBody TicketUserCreateRequest request
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        LOGGER.info("ticket user create request received, userId={}, relName={}", authenticatedUserId, request.relName());
        TicketUserResponse ticketUser = ticketUserService.createTicketUser(authenticatedUserId, request);
        LOGGER.info("ticket user create request succeeded, ticketUserId={}", ticketUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(ticketUser));
    }

    /**
     * Deletes one real-name ticket buyer for the current logged-in user.
     */
    @DeleteMapping("/ticket-users/{ticketUserId}")
    public ApiResponse<Void> deleteTicketUser(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @PathVariable Long ticketUserId
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        LOGGER.info("ticket user delete request received, userId={}, ticketUserId={}", authenticatedUserId, ticketUserId);
        ticketUserService.deleteTicketUser(authenticatedUserId, ticketUserId);
        LOGGER.info("ticket user delete request succeeded, ticketUserId={}", ticketUserId);
        return ApiResponse.success();
    }

    /**
     * Validates ticket buyer ownership for an internal order-service request.
     */
    @PostMapping("/ticket-users/validate")
    public ApiResponse<Void> validateTicketUsers(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @Valid @RequestBody TicketUserValidationRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.SYSTEM);
        LOGGER.info(
                "ticket user ownership validation request received, userId={}, count={}",
                request.userId(),
                request.ticketUserIds().size()
        );
        ticketUserService.validateTicketUserOwnership(request.userId(), request.ticketUserIds());
        LOGGER.info(
                "ticket user ownership validation request succeeded, userId={}, count={}",
                request.userId(),
                request.ticketUserIds().size()
        );
        return ApiResponse.success();
    }
}
