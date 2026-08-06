package com.dezxxx.minios3.service;

import com.dezxxx.minios3.dto.auth.AuthenticationRequestDto;
import com.dezxxx.minios3.dto.auth.AuthenticationResponseDto;
import com.dezxxx.minios3.dto.auth.RefreshRequestDto;
import com.dezxxx.minios3.dto.auth.RegistrationRequestDto;
import com.dezxxx.minios3.exception.ExpiredTokenException;
import com.dezxxx.minios3.exception.InvalidCredentialsException;
import com.dezxxx.minios3.exception.InvalidTokenException;
import com.dezxxx.minios3.exception.UserBlockedException;
import com.dezxxx.minios3.exception.UsernameAlreadyTakenException;
import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import com.dezxxx.minios3.repository.UserRepository;
import com.dezxxx.minios3.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Everything an attacker would try lives here: a wrong password, a blocked account, a
 * forged token, and an access token offered where a refresh token is required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String VASYA = "vasya";
    private static final String HASH = "$2a$10$storedhash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("login with the right password returns a token pair")
    void loginSucceeds() {
        // given
        User vasya = withPassword(TestUsersUtil.user(1, VASYA));
        when(userRepository.findByUsernameAndDeletedAtIsNull(VASYA)).thenReturn(Mono.just(vasya));
        when(passwordEncoder.matches("secret123", HASH)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(VASYA, Role.USER)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(VASYA)).thenReturn("refresh-token");

        // when
        Mono<AuthenticationResponseDto> result =
                authService.login(new AuthenticationRequestDto(VASYA, "secret123"));

        // then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.accessToken()).isEqualTo("access-token");
                    assertThat(response.refreshToken()).isEqualTo("refresh-token");
                    assertThat(response.username()).isEqualTo(VASYA);
                    assertThat(response.role()).isEqualTo(Role.USER);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("a wrong password is rejected, and never as a different error than a wrong name")
    void loginRejectsWrongPassword() {
        // given
        User vasya = withPassword(TestUsersUtil.user(1, VASYA));
        when(userRepository.findByUsernameAndDeletedAtIsNull(VASYA)).thenReturn(Mono.just(vasya));
        when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

        // when
        Mono<AuthenticationResponseDto> result =
                authService.login(new AuthenticationRequestDto(VASYA, "wrong"));

        // then: the same exception an unknown user name produces, so neither can be probed
        StepVerifier.create(result)
                .expectError(InvalidCredentialsException.class)
                .verify();

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(Role.class));
    }

    @Test
    @DisplayName("an unknown user name is rejected exactly like a wrong password")
    void loginRejectsUnknownUser() {
        // given
        when(userRepository.findByUsernameAndDeletedAtIsNull("ghost")).thenReturn(Mono.empty());

        // when
        Mono<AuthenticationResponseDto> result =
                authService.login(new AuthenticationRequestDto("ghost", "secret123"));

        // then
        StepVerifier.create(result)
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    @DisplayName("a blocked account cannot log in even with the right password")
    void loginRejectsBlockedUser() {
        // given
        User vasya = withPassword(TestUsersUtil.user(1, VASYA));
        vasya.setStatus(UserStatus.BLOCKED);

        when(userRepository.findByUsernameAndDeletedAtIsNull(VASYA)).thenReturn(Mono.just(vasya));
        when(passwordEncoder.matches("secret123", HASH)).thenReturn(true);

        // when
        Mono<AuthenticationResponseDto> result =
                authService.login(new AuthenticationRequestDto(VASYA, "secret123"));

        // then
        StepVerifier.create(result)
                .expectError(UserBlockedException.class)
                .verify();

        // Blocking is worthless if a token is handed out anyway
        verify(jwtTokenProvider, never()).createAccessToken(anyString(), any(Role.class));
    }

    @Test
    @DisplayName("registration always produces an active USER, whatever the request says")
    void registerAlwaysCreatesPlainUser() {
        // given
        when(userRepository.existsByUsername(VASYA)).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("secret123")).thenReturn(HASH);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(jwtTokenProvider.createAccessToken(VASYA, Role.USER)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(VASYA)).thenReturn("refresh-token");

        // when
        authService.register(new RegistrationRequestDto(VASYA, "secret123")).block();

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User stored = captor.getValue();

        // The registration DTO has no role field at all, and this is why
        assertThat(stored.getRole()).isEqualTo(Role.USER);
        assertThat(stored.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(stored.getPasswordHash()).isEqualTo(HASH);
        assertThat(stored.getPasswordHash()).isNotEqualTo("secret123");
    }

    @Test
    @DisplayName("registering a taken name fails before the password is hashed")
    void registerRejectsTakenName() {
        // given
        when(userRepository.existsByUsername(VASYA)).thenReturn(Mono.just(true));

        // when
        Mono<AuthenticationResponseDto> result =
                authService.register(new RegistrationRequestDto(VASYA, "secret123"));

        // then
        StepVerifier.create(result)
                .expectError(UsernameAlreadyTakenException.class)
                .verify();

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("an access token is refused where a refresh token is required")
    void refreshRefusesAccessToken() {
        // given: the signature is fine, the kind of token is not
        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.parseToken("access-token")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(false);

        // when
        Mono<AuthenticationResponseDto> result =
                authService.refresh(new RefreshRequestDto("access-token"));

        // then
        StepVerifier.create(result)
                .expectError(InvalidTokenException.class)
                .verify();

        verify(userRepository, never()).findByUsernameAndDeletedAtIsNull(anyString());
    }

    @Test
    @DisplayName("an expired refresh token is told apart from a forged one")
    void refreshTellsExpiryFromForgery() {
        // given
        when(jwtTokenProvider.parseToken("old-token"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        // when
        Mono<AuthenticationResponseDto> result =
                authService.refresh(new RefreshRequestDto("old-token"));

        // then: expiry means "log in again", forgery means "this token was never ours"
        StepVerifier.create(result)
                .expectError(ExpiredTokenException.class)
                .verify();
    }

    @Test
    @DisplayName("a forged refresh token is rejected as invalid")
    void refreshRejectsForgedToken() {
        // given
        when(jwtTokenProvider.parseToken("forged"))
                .thenThrow(new MalformedJwtException("bad signature"));

        // when
        Mono<AuthenticationResponseDto> result =
                authService.refresh(new RefreshRequestDto("forged"));

        // then
        StepVerifier.create(result)
                .expectError(InvalidTokenException.class)
                .verify();
    }

    @Test
    @DisplayName("a refresh token belonging to a deleted account no longer works")
    void refreshRejectsDeletedUser() {
        // given
        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.parseToken("refresh-token")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(jwtTokenProvider.getUsername(claims)).thenReturn(VASYA);
        when(userRepository.findByUsernameAndDeletedAtIsNull(VASYA)).thenReturn(Mono.empty());

        // when
        Mono<AuthenticationResponseDto> result =
                authService.refresh(new RefreshRequestDto("refresh-token"));

        // then: this is the whole point of reading the row again instead of trusting the token
        StepVerifier.create(result)
                .expectError(InvalidTokenException.class)
                .verify();
    }

    /** TestUsersUtil builds accounts without a password, which login needs. */
    private User withPassword(User user) {
        user.setPasswordHash(HASH);
        return user;
    }
}
