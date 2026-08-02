package com.dezxxx.minios3.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/v1/auth/refresh: the long-lived token traded for a fresh access token. */
@Schema(description = "Refresh request")
public record RefreshRequestDto(

        @Schema(description = "The refreshToken from login, register or a previous refresh. "
                + "An access token is rejected here.",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        @NotBlank
        String refreshToken) {
}