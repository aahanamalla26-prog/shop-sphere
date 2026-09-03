package com.shopsphere.auth.dto;

import com.shopsphere.auth.entity.Role;
import com.shopsphere.auth.entity.User;

public record UserResponse(Long id, String fullName, String email, Role role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
