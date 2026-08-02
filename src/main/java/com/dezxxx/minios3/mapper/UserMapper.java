package com.dezxxx.minios3.mapper;

import com.dezxxx.minios3.dto.user.UserResponseDto;
import com.dezxxx.minios3.model.User;

public final class UserMapper {

    private UserMapper() {}

    public static UserResponseDto toResponse(User user) {
        return new UserResponseDto (user.getId(),
                                user.getUsername(),
                                user.getRole(),
                                user.getStatus());
    }
}
