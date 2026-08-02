package com.dezxxx.minios3.dto.auth;

import com.dezxxx.minios3.model.status.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned by /auth/login, /auth/register and /auth/refresh.
 * The access token expires in minutes; the refresh token is traded for a new one
 * at /auth/refresh, which re-reads the user and so picks up a changed role.
 */
@Schema(description = "A freshly issued token pair plus who they belong to")
public record AuthenticationResponseDto(

        @Schema(description = "Send as 'Authorization: Bearer <token>'. Lives 15 minutes.",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Only accepted by /api/v1/auth/refresh. Lives 30 days.",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken,

        @Schema(description = "Name the tokens were issued for", example = "admin")
        String username,

        @Schema(description = "Role carried inside the access token; "
                + "a change takes effect at the next refresh")
        Role role) {
}