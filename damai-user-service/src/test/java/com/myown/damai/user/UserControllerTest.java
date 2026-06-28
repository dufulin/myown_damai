package com.myown.damai.user;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies user APIs and token-based session behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    private static final String USER_ID_HEADER = "X-Damai-User-Id";
    private static final String USER_ROLE_HEADER = "X-Damai-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Verifies the register, login, current user, and logout flow.
     */
    @Test
    void registerLoginAndLogout() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice",
                                  "mobile": "18800000000",
                                  "email": "alice@example.com",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.user.mobile").value("18800000000"));

        MvcResult loginResult = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login": "alice@example.com",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
                    org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"));
                })
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.path("data").path("token").asText();

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mobile").value("18800000000"));

        mockMvc.perform(post("/api/users/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * Verifies refresh cookies rotate and cannot be replayed after one successful use.
     */
    @Test
    void refreshTokenRotatesAndRejectsReplay() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Refresh User",
                                  "mobile": "18800000004",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String originalRefreshToken = extractRefreshToken(registerResult);

        MvcResult refreshResult = mockMvc.perform(post("/api/users/refresh")
                        .cookie(new MockCookie("damai_refresh_token", originalRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andReturn();
        String rotatedRefreshToken = extractRefreshToken(refreshResult);

        mockMvc.perform(post("/api/users/refresh")
                        .cookie(new MockCookie("damai_refresh_token", originalRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/users/refresh")
                        .cookie(new MockCookie("damai_refresh_token", rotatedRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", notNullValue()));
    }

    /**
     * Verifies an administrator role update is persisted and returned by subsequent login.
     */
    @Test
    void updateUserRolePersistsForAuthentication() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Operator User",
                                  "mobile": "18800000005",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andReturn();
        Long userId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data")
                .path("user")
                .path("id")
                .asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/users/{userId}/role", userId)
                        .header(USER_ID_HEADER, "999")
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("OPERATOR"));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login": "18800000005",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("OPERATOR"));
    }

    /**
     * Verifies duplicate mobile registration is rejected.
     */
    @Test
    void duplicateMobileReturnsConflict() throws Exception {
        String body = """
                {
                  "name": "Bob",
                  "mobile": "18800000001",
                  "password": "secret123"
                }
                """;

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOBILE_EXISTS"));
    }

    /**
     * Verifies invalid credentials are rejected by the login API.
     */
    @Test
    void loginWithBadPasswordReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Charlie",
                                  "mobile": "18800000002",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login": "18800000002",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    /**
     * Verifies protected user APIs reject requests without a bearer token.
     */
    @Test
    void protectedApiWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * Verifies ticket buyer creation, listing, and soft deletion.
     */
    @Test
    void manageTicketUsers() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Dana",
                                  "mobile": "18800000003",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long userId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data")
                .path("user")
                .path("id")
                .asLong();

        MvcResult ticketUserResult = mockMvc.perform(post("/api/users/ticket-users")
                        .header(USER_ID_HEADER, String.valueOf(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "relName": "Dana Buyer",
                                  "idType": 1,
                                  "idNumber": "110101199001011234"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.relName").value("Dana Buyer"))
                .andReturn();
        Long ticketUserId = objectMapper.readTree(ticketUserResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(post("/api/users/ticket-users/validate")
                        .header("X-Damai-User-Role", "SYSTEM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "ticketUserIds": [%d]
                                }
                                """.formatted(userId, ticketUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(post("/api/users/ticket-users/validate")
                        .header("X-Damai-User-Role", "SYSTEM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "ticketUserIds": [%d]
                                }
                                """.formatted(userId + 1, ticketUserId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TICKET_USER_NOT_OWNED"));

        mockMvc.perform(get("/api/users/ticket-users")
                        .header(USER_ID_HEADER, String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(ticketUserId));

        mockMvc.perform(delete("/api/users/ticket-users/{ticketUserId}", ticketUserId)
                        .header(USER_ID_HEADER, String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(get("/api/users/ticket-users")
                        .header(USER_ID_HEADER, String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    /**
     * Extracts the refresh-token value from a Set-Cookie response header.
     */
    private String extractRefreshToken(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
        return setCookie.substring(
                "damai_refresh_token=".length(),
                setCookie.indexOf(';')
        );
    }
}
