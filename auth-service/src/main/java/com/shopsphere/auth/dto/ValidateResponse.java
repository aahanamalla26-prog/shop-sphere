package com.shopsphere.auth.dto;

public record ValidateResponse(boolean valid, Long userId, String email, String role) {}
