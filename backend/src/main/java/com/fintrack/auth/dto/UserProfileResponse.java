package com.fintrack.auth.dto;

/**
 * User profile data returned in auth responses and GET /me.
 */
public record UserProfileResponse(
        String id,
        String username,
        String email,
        String role,
        String createdAt
) {
}
