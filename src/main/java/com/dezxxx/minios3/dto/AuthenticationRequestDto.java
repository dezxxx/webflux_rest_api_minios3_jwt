package com.dezxxx.minios3.dto;


import jakarta.validation.constraints.NotBlank;

public record AuthenticationRequestDto(

        /** Credentials sent to POST /auth/login. */
        @NotBlank(message = "Username is required")
            String username,

            @NotBlank(message = "Password is required")
            String password
    ) {
    }

