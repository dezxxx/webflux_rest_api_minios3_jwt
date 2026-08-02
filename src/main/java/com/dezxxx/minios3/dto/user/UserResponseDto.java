package com.dezxxx.minios3.dto.user;

import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Everything about a user that may leave the server. The password hash never does. */
@Schema(description = "A user account as returned by the API")
public record UserResponseDto(

        @Schema(description = "Primary key", example = "1")
        Integer id,

        @Schema(example = "admin")
        String username,

        @Schema(example = "ADMIN")
        Role role,

        @Schema(example = "ACTIVE")
        UserStatus status) {
}