package com.dezxxx.minios3.mapper;

import com.dezxxx.minios3.dto.auth.AuthenticationResponseDto;
import com.dezxxx.minios3.model.User;

/**
 * Builds the payload returned by the auth endpoints.
 * The tokens arrive as parameters so this class stays a pure function
 * with no Spring dependencies and can be tested without a context.
 */
public final class AuthenticationMapper {

    private AuthenticationMapper() {
    }

    public static AuthenticationResponseDto toResponse(User user, String accessToken, String refreshToken) {
        return new AuthenticationResponseDto(accessToken, refreshToken, user.getUsername(), user.getRole());
    }
}