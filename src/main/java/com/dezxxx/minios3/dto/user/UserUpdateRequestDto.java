package com.dezxxx.minios3.dto.user;

import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/v1/users/{id}. A PUT replaces the whole state, so every field is
 * required — a missing one would otherwise overwrite the stored value with null.
 * The password is not here: changing it is a separate operation.
 */
@Schema(description = "Full replacement of an account. All three fields are mandatory.")
public record UserUpdateRequestDto(

        @Schema(description = "New user name", example = "vasya")
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 255, message = "Username must be 3 to 255 characters")
                String username,

        @Schema(description = "An administrator may not take ADMIN away from their own account",
                example = "MODERATOR")
        @NotNull(message = "Role is required")
        Role role,

        @Schema(description = "BLOCKED stops the account logging in; an administrator "
                + "may not block their own account",
                example = "ACTIVE")
        @NotNull(message = "Status is required")
        UserStatus status) {
}