package com.dezxxx.minios3.service;

import com.dezxxx.minios3.dto.user.UserCreateRequestDto;
import com.dezxxx.minios3.dto.user.UserResponseDto;
import com.dezxxx.minios3.dto.user.UserUpdateRequestDto;
import com.dezxxx.minios3.exception.UserNotFoundException;
import com.dezxxx.minios3.exception.UsernameAlreadyTakenException;
import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import com.dezxxx.minios3.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The CRUD here is unremarkable; what is worth testing are the three guards that stop an
 * administrator locking everybody out through their own account, and the rule that a raw
 * password never reaches the database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    private static final String ADMIN = "admin";
    private static final String VASYA = "vasya";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("an administrator cannot take the ADMIN role away from their own account")
    void adminCannotDemoteThemselves() {
        // given
        User admin = TestUsersUtil.admin(1, ADMIN);
        when(userRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Mono.just(admin));

        UserUpdateRequestDto requestDto =
                new UserUpdateRequestDto(ADMIN, Role.USER, UserStatus.ACTIVE);

        // when
        Mono<UserResponseDto> result = userService.update(1, requestDto, ADMIN);

        // then
        StepVerifier.create(result)
                .expectError(AccessDeniedException.class)
                .verify();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("an administrator cannot block their own account")
    void adminCannotBlockThemselves() {
        // given
        User admin = TestUsersUtil.admin(1, ADMIN);
        when(userRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Mono.just(admin));

        // The role stays ADMIN on purpose: the first guard lets it through, the second catches it
        UserUpdateRequestDto requestDto =
                new UserUpdateRequestDto(ADMIN, Role.ADMIN, UserStatus.BLOCKED);

        // when
        Mono<UserResponseDto> result = userService.update(1, requestDto, ADMIN);

        // then
        StepVerifier.create(result)
                .expectError(AccessDeniedException.class)
                .verify();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("an administrator cannot delete their own account")
    void adminCannotDeleteThemselves() {
        // given
        User admin = TestUsersUtil.admin(1, ADMIN);
        when(userRepository.findByIdAndDeletedAtIsNull(1)).thenReturn(Mono.just(admin));

        // when
        Mono<Void> result = userService.delete(1, ADMIN);

        // then
        StepVerifier.create(result)
                .expectError(AccessDeniedException.class)
                .verify();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("deleting somebody else sets deleted_at and keeps the row")
    void deleteIsSoft() {
        // given
        User victim = TestUsersUtil.user(2, VASYA);
        when(userRepository.findByIdAndDeletedAtIsNull(2)).thenReturn(Mono.just(victim));
        when(userRepository.save(victim)).thenReturn(Mono.just(victim));

        // when
        StepVerifier.create(userService.delete(2, ADMIN))
                .verifyComplete();

        // then
        assertThat(victim.getDeletedAt()).isNotNull();
        assertThat(victim.getUsername()).isEqualTo(VASYA);
    }

    @Test
    @DisplayName("creating a user with a taken name fails before the password is hashed")
    void createRejectsTakenUsername() {
        // given
        when(userRepository.existsByUsername(VASYA)).thenReturn(Mono.just(true));

        UserCreateRequestDto requestDto =
                new UserCreateRequestDto(VASYA, "secret123", Role.MODERATOR);

        // when
        Mono<UserResponseDto> result = userService.create(requestDto);

        // then
        StepVerifier.create(result)
                .expectError(UsernameAlreadyTakenException.class)
                .verify();

        // bcrypt is deliberately slow, so a name that is already taken must not reach it
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a created user stores the hash, never the raw password, and is ACTIVE")
    void createStoresHashNotPassword() {
        // given
        when(userRepository.existsByUsername(VASYA)).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");
        // Hand back whatever was passed in, the way a real save would
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        UserCreateRequestDto requestDto =
                new UserCreateRequestDto(VASYA, "secret123", Role.MODERATOR);

        // when
        userService.create(requestDto).block();

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User stored = captor.getValue();

        assertThat(stored.getPasswordHash()).isEqualTo("hashed-secret");
        assertThat(stored.getPasswordHash()).isNotEqualTo("secret123");
        // The role is taken from the body here, and that is safe: only an ADMIN gets this far
        assertThat(stored.getRole()).isEqualTo(Role.MODERATOR);
        assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("a deleted account is not found by id")
    void deletedAccountIsNotFound() {
        // given: the query itself filters on deleted_at, so a deleted row simply is not there
        when(userRepository.findByIdAndDeletedAtIsNull(99)).thenReturn(Mono.empty());

        // when
        Mono<UserResponseDto> result = userService.getById(99);

        // then
        StepVerifier.create(result)
                .expectError(UserNotFoundException.class)
                .verify();
    }
}
