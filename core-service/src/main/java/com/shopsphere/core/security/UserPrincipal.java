package com.shopsphere.core.security;

/**
 * Lightweight representation of the authenticated caller, extracted from the
 * validated JWT and attached to the Spring Security context for this request.
 */
public record UserPrincipal(Long userId, String email, String role) {
    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role);
    }
}
