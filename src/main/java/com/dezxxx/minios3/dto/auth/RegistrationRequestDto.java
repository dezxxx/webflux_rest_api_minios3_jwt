package com.dezxxx.minios3.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-service registration. The role is never read from here — it is always USER. */
@Schema(description = "Self-registration request; the new account is always an active USER")
public record RegistrationRequestDto(

        @Schema(description = "Desired user name, must not be taken already", example = "vasya")
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 255, message = "Username must be 3 to 255 characters")
                                              String username,

        @Schema(description = "Raw password; bcrypt only reads the first 72 bytes",
                example = "secret123")
        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 72, message = "Password must be 6 to 72 characters")
                                              String password) {

}