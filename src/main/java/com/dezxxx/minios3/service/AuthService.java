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
import com.dezxxx.minios3.mapper.AuthenticationMapper;
import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import com.dezxxx.minios3.repository.UserRepository;
import com.dezxxx.minios3.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor

public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Verifies the credentials and issues a token.
     */

    public Mono<AuthenticationResponseDto> login(AuthenticationRequestDto request) {
        return userRepository.findByUsernameAndDeletedAtIsNull(request.username())
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .switchIfEmpty(Mono.error(
                        new InvalidCredentialsException("Invalid user name or password")))
                .flatMap(this::ensureActive)
                .map(this::toResponse);
    }

    /**
     * Creates a plain USER and logs them in straight away.
     */
    public Mono<AuthenticationResponseDto>
    register(RegistrationRequestDto request) {
        return userRepository.existsByUsername(request.username())
                .flatMap(exists -> exists
                        ? Mono.error(new
                        UsernameAlreadyTakenException(request.username()))
                        : createUser(request))
                .map(this::toResponse);
    }

    /**
     * Trades a refresh token for a fresh pair.
     * The user row is read again here, which is the only reason this endpoint exists:
     * a changed role or a block takes effect within one access-token lifetime
     * instead of waiting for the old token to expire.
     */
    public Mono<AuthenticationResponseDto> refresh(RefreshRequestDto request) {
        return Mono.fromCallable(() -> parseRefreshToken(request.refreshToken()))
                .map(jwtTokenProvider::getUsername)
                .flatMap(userRepository::findByUsernameAndDeletedAtIsNull)
                .switchIfEmpty(Mono.error(
                        new InvalidTokenException("The user this token belongs to no longer exists")))
                .flatMap(this::ensureActive)
                .map(this::toResponse);
    }

    /** Rejects anything that is not a live refresh token, telling the two failures apart. */
    private Claims parseRefreshToken(String token) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(token);
        } catch (ExpiredJwtException e) {
            // Must be caught first: it is a subclass of JwtException
            throw new ExpiredTokenException("Refresh token has expired, please log in again");
        } catch (JwtException e) {
            throw new InvalidTokenException("Refresh token is not valid");
        }

        if (!jwtTokenProvider.isRefreshToken(claims)) {
            throw new InvalidTokenException("Provided token is not a refresh token");
        }
        return claims;
    }

    private Mono<User> createUser(RegistrationRequestDto request) {
        User user = User.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))

                // Never taken from the request: it would make authorisation meaningless
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();

        log.info("Registering new user: {}", request.username());
        return userRepository.save(user);
    }

    private Mono<User> ensureActive(User user) {
        return user.getStatus() == UserStatus.ACTIVE
                ? Mono.just(user)
                : Mono.error(new UserBlockedException("User is blocked: " + user.getUsername()));
    }

    /** The one place a token pair is issued — login, register and refresh all end here. */
    private AuthenticationResponseDto toResponse(User user) {
        return AuthenticationMapper.toResponse(user,
                jwtTokenProvider.createAccessToken(user.getUsername(), user.getRole()),
                jwtTokenProvider.createRefreshToken(user.getUsername()));
    }
}