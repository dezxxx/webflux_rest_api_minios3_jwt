package com.dezxxx.minios3.dto.user;

import com.dezxxx.minios3.model.status.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


/** Body of POST /api/v1/users. Unlike registration, an ADMIN may choose the role here. */
@Schema(description = "New account created by an administrator; the status is always ACTIVE")
public record UserCreateRequestDto(

        @Schema(description = "Must not be taken already", example = "moderator")
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 255, message = "Username must be 3 to 255 characters")
                                            String username,

        @Schema(description = "Raw password; bcrypt only reads the first 72 bytes",
                example = "secret123")
        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 72, message = "Password must be 6 to 72 characters")
                                            String password,

        @Schema(description = "Safe to take from the request here: only an ADMIN reaches "
                + "this endpoint. This is the one place a MODERATOR can be created.",
                example = "MODERATOR")
        @NotNull(message = "Role is required")
        Role role) {
}