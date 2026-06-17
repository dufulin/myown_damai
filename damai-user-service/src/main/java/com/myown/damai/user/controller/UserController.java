package com.myown.damai.user.controller;

import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.user.dto.AuthResponse;
import com.myown.damai.user.dto.LoginRequest;
import com.myown.damai.user.dto.RegisterRequest;
import com.myown.damai.user.dto.TicketUserCreateRequest;
import com.myown.damai.user.dto.TicketUserResponse;
import com.myown.damai.user.dto.UserProfileResponse;
import com.myown.damai.user.service.TicketUserService;
import com.myown.damai.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * Creates the controller with user and ticket buyer services.
     */
    public UserController(UserService userService, TicketUserService ticketUserService) {
        this.userService = userService;
        this.ticketUserService = ticketUserService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        String mobile = request.mobile() != null ? request.mobile() : request.phone();
        LOGGER.info("user register request received, mobile={}", mobile);
        AuthResponse authResponse = userService.register(request);
        LOGGER.info(
                "user register request succeeded, mobile={}, userId={}",
                authResponse.user().mobile(),
                authResponse.user().id()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(authResponse));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String loginIdentifier = request.login() != null ? request.login() : request.username();
        LOGGER.info("user login request received, loginType={}", loginIdentifier != null && loginIdentifier.contains("@") ? "email" : "mobile");
        AuthResponse authResponse = userService.login(request);
        LOGGER.info(
                "user login request succeeded, userId={}, mobile={}",
                authResponse.user().id(),
                authResponse.user().mobile()
        );
        return ApiResponse.success(authResponse);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        LOGGER.info("user logout request received, hasAuthorizationHeader={}", authorizationHeader != null);
        userService.logout(authorizationHeader);
        LOGGER.info("user logout request succeeded");
        return ApiResponse.success();
    }

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
     * Lists real-name ticket buyers for the current logged-in user.
     */
    @GetMapping("/ticket-users")
    public ApiResponse<List<TicketUserResponse>> listTicketUsers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        LOGGER.info("ticket user list request received, hasAuthorizationHeader={}", authorizationHeader != null);
        List<TicketUserResponse> ticketUsers = ticketUserService.listTicketUsers(authorizationHeader);
        LOGGER.info("ticket user list request succeeded, count={}", ticketUsers.size());
        return ApiResponse.success(ticketUsers);
    }

    /**
     * Creates one real-name ticket buyer for the current logged-in user.
     */
    @PostMapping("/ticket-users")
    public ResponseEntity<ApiResponse<TicketUserResponse>> createTicketUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @Valid @RequestBody TicketUserCreateRequest request
    ) {
        LOGGER.info("ticket user create request received, relName={}", request.relName());
        TicketUserResponse ticketUser = ticketUserService.createTicketUser(authorizationHeader, request);
        LOGGER.info("ticket user create request succeeded, ticketUserId={}", ticketUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(ticketUser));
    }

    /**
     * Deletes one real-name ticket buyer for the current logged-in user.
     */
    @DeleteMapping("/ticket-users/{ticketUserId}")
    public ApiResponse<Void> deleteTicketUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long ticketUserId
    ) {
        LOGGER.info("ticket user delete request received, ticketUserId={}", ticketUserId);
        ticketUserService.deleteTicketUser(authorizationHeader, ticketUserId);
        LOGGER.info("ticket user delete request succeeded, ticketUserId={}", ticketUserId);
        return ApiResponse.success();
    }
}
