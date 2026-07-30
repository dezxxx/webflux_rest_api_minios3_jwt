package com.dezxxx.minios3.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequestDto(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 255, message = "Username must be 3 to 255 characters")
                                              String username,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 72, message = "Password must be 6 to 72 characters")
                                              String password) {

}

