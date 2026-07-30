package com.dezxxx.minios3.dto;

import com.dezxxx.minios3.model.status.Role;

/** Returned by both /auth/login and /auth/register. */
public record AuthenticationResponseDto(String token, String username, Role role) {
}
