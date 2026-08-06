package com.dezxxx.minios3.service;

import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;

/** Ready-made accounts for tests, so no test has to spell
 out a builder again. */

public final class TestUsersUtil {
    private TestUsersUtil() {}

    public  static User admin (Integer id, String username) {
        return of(id, username, Role.ADMIN);
    }

    public static User moderator (Integer id, String username) {
        return of(id, username, Role.MODERATOR);
    }

    public static User user (Integer id, String username) {
        return of(id, username, Role.USER);
    }

    private  static  User of (Integer id, String username, Role role) {
        return User.builder()
                .id(id)
                .username(username)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
