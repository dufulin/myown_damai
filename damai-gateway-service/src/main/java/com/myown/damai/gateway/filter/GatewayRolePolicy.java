package com.myown.damai.gateway.filter;

import com.myown.damai.common.auth.UserRole;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Resolves role requirements for privileged and internal-only API routes.
 */
@Component
public class GatewayRolePolicy {

    private static final Set<UserRole> OPERATIONS_ROLES =
            Set.copyOf(EnumSet.of(UserRole.OPERATOR, UserRole.ADMIN));
    private static final Set<UserRole> ADMIN_ROLES =
            Set.copyOf(EnumSet.of(UserRole.ADMIN));

    /**
     * Resolves an authorization requirement for one method and normalized API path.
     */
    public Optional<Requirement> resolve(HttpMethod method, String requestPath) {
        String path = normalizePath(requestPath);
        if (isInternalOnlyEndpoint(method, path)) {
            return Optional.of(Requirement.internalOnlyRoute());
        }
        if (isAdminEndpoint(method, path)) {
            return Optional.of(Requirement.roles(ADMIN_ROLES));
        }
        if (isOperationsEndpoint(method, path)) {
            return Optional.of(Requirement.roles(OPERATIONS_ROLES));
        }
        return Optional.empty();
    }

    /**
     * Checks endpoints reserved for direct service-to-service calls.
     */
    private boolean isInternalOnlyEndpoint(HttpMethod method, String path) {
        if (!HttpMethod.POST.equals(method)) {
            return false;
        }
        return path.matches("/api/programs/\\d+/inventory/(lock|release|sold)")
                || path.matches("/api/programs/\\d+/order-snapshot")
                || "/api/users/ticket-users/validate".equals(path)
                || path.matches("/api/orders/\\d+/paid");
    }

    /**
     * Checks endpoints that only administrators may invoke.
     */
    private boolean isAdminEndpoint(HttpMethod method, String path) {
        return HttpMethod.PUT.equals(method) && path.matches("/api/users/\\d+/role");
    }

    /**
     * Checks endpoints available to operators and administrators.
     */
    private boolean isOperationsEndpoint(HttpMethod method, String path) {
        if (path.startsWith("/api/pay/events/")) {
            return HttpMethod.GET.equals(method) || HttpMethod.POST.equals(method);
        }
        if (!HttpMethod.POST.equals(method)) {
            return false;
        }
        return "/api/programs".equals(path)
                || "/api/programs/categories".equals(path)
                || "/api/orders/timeout-cancel".equals(path)
                || "/api/pay/events/compensate".equals(path)
                || path.matches("/api/programs/\\d+/offline")
                || path.matches("/api/programs/\\d+/seats")
                || path.matches("/api/programs/\\d+/ticket-categories/\\d+/price");
    }

    /**
     * Removes a trailing slash so equivalent routes share one policy.
     */
    private String normalizePath(String requestPath) {
        if (requestPath != null && requestPath.length() > 1 && requestPath.endsWith("/")) {
            return requestPath.substring(0, requestPath.length() - 1);
        }
        return requestPath;
    }

    /**
     * Describes either an allowed role set or a route blocked from the public gateway.
     */
    public record Requirement(Set<UserRole> allowedRoles, boolean internalOnly) {

        /**
         * Creates a role-based requirement.
         */
        public static Requirement roles(Set<UserRole> roles) {
            return new Requirement(Set.copyOf(roles), false);
        }

        /**
         * Creates an internal-only requirement.
         */
        public static Requirement internalOnlyRoute() {
            return new Requirement(Set.of(), true);
        }

        /**
         * Checks whether the supplied human account role is allowed.
         */
        public boolean allows(UserRole role) {
            return !internalOnly && allowedRoles.contains(role);
        }
    }
}
