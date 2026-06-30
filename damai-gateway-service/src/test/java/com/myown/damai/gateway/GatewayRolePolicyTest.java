package com.myown.damai.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myown.damai.common.auth.UserRole;
import com.myown.damai.gateway.filter.GatewayRolePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Verifies centralized gateway role requirements for privileged API routes.
 */
class GatewayRolePolicyTest {

    private final GatewayRolePolicy rolePolicy = new GatewayRolePolicy();

    /**
     * Verifies program writes allow operators and administrators but reject normal users.
     */
    @Test
    void programCreationRequiresOperationsRole() {
        GatewayRolePolicy.Requirement requirement = rolePolicy
                .resolve(HttpMethod.POST, "/api/programs")
                .orElseThrow();

        assertFalse(requirement.allows(UserRole.USER));
        assertTrue(requirement.allows(UserRole.OPERATOR));
        assertTrue(requirement.allows(UserRole.ADMIN));
    }

    /**
     * Verifies role assignment is restricted to administrators.
     */
    @Test
    void roleAssignmentRequiresAdministrator() {
        GatewayRolePolicy.Requirement requirement = rolePolicy
                .resolve(HttpMethod.PUT, "/api/users/12/role")
                .orElseThrow();

        assertFalse(requirement.allows(UserRole.OPERATOR));
        assertTrue(requirement.allows(UserRole.ADMIN));
    }

    /**
     * Verifies management reads allow operators while management role updates require administrators.
     */
    @Test
    void managementRoutesUseOperationAndAdministratorBoundaries() {
        GatewayRolePolicy.Requirement dashboardRequirement = rolePolicy
                .resolve(HttpMethod.GET, "/api/admin/dashboard")
                .orElseThrow();
        assertTrue(dashboardRequirement.allows(UserRole.OPERATOR));
        assertTrue(dashboardRequirement.allows(UserRole.ADMIN));
        assertFalse(dashboardRequirement.allows(UserRole.USER));

        GatewayRolePolicy.Requirement roleRequirement = rolePolicy
                .resolve(HttpMethod.PUT, "/api/admin/users/12/role")
                .orElseThrow();
        assertFalse(roleRequirement.allows(UserRole.OPERATOR));
        assertTrue(roleRequirement.allows(UserRole.ADMIN));
    }

    /**
     * Verifies service callbacks cannot be invoked through the public gateway.
     */
    @Test
    void orderPaidCallbackIsInternalOnly() {
        GatewayRolePolicy.Requirement requirement = rolePolicy
                .resolve(HttpMethod.POST, "/api/orders/10001/paid")
                .orElseThrow();

        assertTrue(requirement.internalOnly());
        assertFalse(requirement.allows(UserRole.ADMIN));
    }

    /**
     * Verifies ticket buyer ownership validation cannot be invoked through the public gateway.
     */
    @Test
    void ticketUserValidationIsInternalOnly() {
        GatewayRolePolicy.Requirement requirement = rolePolicy
                .resolve(HttpMethod.POST, "/api/users/ticket-users/validate")
                .orElseThrow();

        assertTrue(requirement.internalOnly());
        assertFalse(requirement.allows(UserRole.ADMIN));
    }

    /**
     * Verifies normal customer order APIs do not receive privileged role requirements.
     */
    @Test
    void customerOrderCreationUsesLoginOnly() {
        assertTrue(rolePolicy.resolve(HttpMethod.POST, "/api/orders").isEmpty());
    }
}
