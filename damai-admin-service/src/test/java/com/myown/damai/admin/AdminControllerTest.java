package com.myown.damai.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.admin.client.AdminOperationClient;
import com.myown.damai.admin.dto.AdminRoleUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

/**
 * Verifies management read models, role boundaries, and delegated operations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    private static final String USER_ID_HEADER = "X-Damai-User-Id";
    private static final String USER_ROLE_HEADER = "X-Damai-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminOperationClient adminOperationClient;

    /**
     * Verifies dashboard counters aggregate the single-database management projection.
     */
    @Test
    void dashboardReturnsOperationalSummary() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(2))
                .andExpect(jsonPath("$.data.activePrograms").value(1))
                .andExpect(jsonPath("$.data.pendingPaymentOrders").value(1))
                .andExpect(jsonPath("$.data.paidOrders").value(1))
                .andExpect(jsonPath("$.data.timeoutOrders").value(1))
                .andExpect(jsonPath("$.data.paidAmount").value(680.0))
                .andExpect(jsonPath("$.data.recentOrders.length()").value(3));
    }

    /**
     * Verifies management lists support role, program-status, and order-status filters.
     */
    @Test
    void managementListsApplyFiltersAndTotals() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .queryParam("role", "ADMIN")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].mobile").value("18800000001"));

        mockMvc.perform(get("/api/admin/programs")
                        .queryParam("programStatus", "0")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].remainingStock").value(20));

        mockMvc.perform(get("/api/admin/orders")
                        .queryParam("orderStatus", "3")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].orderNumber").value(90002));
    }

    /**
     * Verifies normal users cannot access management read APIs.
     */
    @Test
    void normalUserCannotReadManagementData() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header(USER_ID_HEADER, "2")
                        .header(USER_ROLE_HEADER, "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * Verifies role updates require an administrator and are delegated to the user service client.
     */
    @Test
    void administratorCanDelegateRoleUpdate() throws Exception {
        when(adminOperationClient.updateUserRole(eq(2L), any(), eq("1"), eq("ADMIN")))
                .thenReturn(objectMapper.createObjectNode().put("role", "OPERATOR"));

        mockMvc.perform(put("/api/admin/users/{userId}/role", 2)
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("UPDATE_USER_ROLE"))
                .andExpect(jsonPath("$.data.result.role").value("OPERATOR"));

        verify(adminOperationClient).updateUserRole(
                eq(2L),
                eq(new AdminRoleUpdateRequest(com.myown.damai.common.auth.UserRole.OPERATOR)),
                eq("1"),
                eq("ADMIN")
        );
    }

    /**
     * Verifies operators cannot assign account roles.
     */
    @Test
    void operatorCannotAssignRole() throws Exception {
        mockMvc.perform(put("/api/admin/users/{userId}/role", 2)
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
