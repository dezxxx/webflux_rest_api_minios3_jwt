package com.dezxxx.minios3.mapper;

import com.dezxxx.minios3.dto.AuthenticationResponseDto;
import com.dezxxx.minios3.model.User;

/**
 * Builds the payload returned by the auth endpoints.
 * The token arrives as a parameter so this class stays a pure function
 * with no Spring dependencies and can be tested without a context.
 */
public final class AuthenticationMapper {

    private AuthenticationMapper() {
    }

    public static AuthenticationResponseDto toResponse(User user, String token) {
        return new AuthenticationResponseDto(token, user.getUsername(), user.getRole());
    }
}