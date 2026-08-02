package com.dezxxx.minios3.dto.auth;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Credentials sent to POST /api/v1/auth/login. */
@Schema(description = "Login credentials")
public record AuthenticationRequestDto(

        @Schema(description = "Registered user name", example = "admin")
        @NotBlank(message = "Username is required")
            String username,

            @Schema(description = "Raw password, checked against the stored bcrypt hash",
                    example = "admin")
            @NotBlank(message = "Password is required")
            String password
    ) {
    }