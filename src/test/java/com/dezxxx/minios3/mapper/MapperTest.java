package com.dezxxx.minios3.mapper;

import com.dezxxx.minios3.dto.auth.AuthenticationResponseDto;
import com.dezxxx.minios3.dto.user.UserResponseDto;
import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both mappers are pure functions, so the tests are short. What matters is not that the
 * fields are copied but that the ones deliberately left out stay out: a password hash
 * reaching a response would be the worst bug in the application.
 */
@DisplayName("Mappers")
class MapperTest {

    @Test
    @DisplayName("UserMapper copies the four public fields and nothing else")
    void userMapperCopiesPublicFields() {
        // given
        User user = fullUser();

        // when
        UserResponseDto response = UserMapper.toResponse(user);

        // then
        assertThat(response.id()).isEqualTo(1);
        assertThat(response.username()).isEqualTo("vasya");
        assertThat(response.role()).isEqualTo(Role.MODERATOR);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);

        // The DTO is a record with exactly four components, which is what keeps the hash
        // and the timestamps from ever leaving the server
        assertThat(UserResponseDto.class.getRecordComponents()).hasSize(4);
    }

    @Test
    @DisplayName("AuthenticationMapper puts the tokens beside the user they belong to")
    void authenticationMapperCombinesUserAndTokens() {
        // given
        User user = fullUser();

        // when
        AuthenticationResponseDto response =
                AuthenticationMapper.toResponse(user, "access-token", "refresh-token");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.username()).isEqualTo("vasya");
        assertThat(response.role()).isEqualTo(Role.MODERATOR);
    }

    @Test
    @DisplayName("both mappers are utility classes: final, and impossible to instantiate")
    void mappersCannotBeInstantiated() throws Exception {
        for (Class<?> type : new Class<?>[]{UserMapper.class, AuthenticationMapper.class}) {
            assertThat(Modifier.isFinal(type.getModifiers())).isTrue();

            Constructor<?> constructor = type.getDeclaredConstructor();
            assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        }
    }

    private User fullUser() {
        return User.builder()
                .id(1)
                .username("vasya")
                .role(Role.MODERATOR)
                .status(UserStatus.ACTIVE)
                // Neither of these may appear in any response
                .passwordHash("$2a$10$storedhash")
                .createdAt(LocalDateTime.of(2026, 8, 7, 2, 30))
                .build();
    }
}
