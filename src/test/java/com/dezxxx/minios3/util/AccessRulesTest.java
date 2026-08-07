package com.dezxxx.minios3.util;

import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two lines of logic, and every ownership check in the application rests on them. They are
 * worth their own tests precisely because they are small enough to look obviously right.
 */
@DisplayName("AccessRules")
class AccessRulesTest {

    private static final String OWNER = "vasya";

    @Test
    @DisplayName("ADMIN and MODERATOR read everything, a plain USER does not")
    void onlyPlainUserIsScoped() {
        assertThat(AccessRules.readsEverything(user(Role.ADMIN))).isTrue();
        assertThat(AccessRules.readsEverything(user(Role.MODERATOR))).isTrue();
        assertThat(AccessRules.readsEverything(user(Role.USER))).isFalse();
    }

    @Test
    @DisplayName("a USER sees their own rows and nobody else's")
    void userSeesOnlyTheirOwn() {
        User caller = user(Role.USER);

        assertThat(AccessRules.maySee(caller, OWNER)).isTrue();
        assertThat(AccessRules.maySee(caller, "kolya")).isFalse();
    }

    @Test
    @DisplayName("a MODERATOR sees rows owned by anyone")
    void moderatorSeesEverybody() {
        User caller = user(Role.MODERATOR);

        assertThat(AccessRules.maySee(caller, "kolya")).isTrue();
    }

    @Test
    @DisplayName("names are compared exactly: a near miss is not a match")
    void namesAreComparedExactly() {
        User caller = user(Role.USER);

        assertThat(AccessRules.maySee(caller, "Vasya")).isFalse();
        assertThat(AccessRules.maySee(caller, "vasya ")).isFalse();
    }

    @Test
    @DisplayName("a utility class: final, and its constructor is private")
    void isAUtilityClass() throws Exception {
        assertThat(Modifier.isFinal(AccessRules.class.getModifiers())).isTrue();

        Constructor<?> constructor = AccessRules.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }

    private User user(Role role) {
        return User.builder()
                .id(1)
                .username(OWNER)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
