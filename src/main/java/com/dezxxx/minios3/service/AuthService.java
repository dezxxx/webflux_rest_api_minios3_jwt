package com.dezxxx.minios3.service;


import com.dezxxx.minios3.dto.AuthenticationRequestDto;
import com.dezxxx.minios3.dto.AuthenticationResponseDto;
import com.dezxxx.minios3.dto.RegistrationRequestDto;
import com.dezxxx.minios3.exception.InvalidCredentialsException;
import com.dezxxx.minios3.exception.UserBlockedException;
import com.dezxxx.minios3.exception.UsernameAlreadyTakenException;
import com.dezxxx.minios3.mapper.AuthenticationMapper;
import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import com.dezxxx.minios3.repository.UserRepository;
import com.dezxxx.minios3.security.JwtTokenProvider;
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
                .map(user -> AuthenticationMapper.toResponse(user, createToken(user)));
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
                .map(user -> AuthenticationMapper.toResponse(user,
                        createToken(user)));
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

    private String createToken(User user) {
        return jwtTokenProvider.createToken(user.getUsername(),
                user.getRole());
    }
}

