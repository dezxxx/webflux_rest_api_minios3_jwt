package com.dezxxx.minios3.service;


import com.dezxxx.minios3.dto.user.UserCreateRequestDto;
import com.dezxxx.minios3.dto.user.UserResponseDto;
import com.dezxxx.minios3.dto.user.UserUpdateRequestDto;
import com.dezxxx.minios3.exception.UserNotFoundException;
import com.dezxxx.minios3.exception.UsernameAlreadyTakenException;
import com.dezxxx.minios3.mapper.UserMapper;
import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import com.dezxxx.minios3.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;


@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    public Flux<UserResponseDto> getAll() {
        return userRepository.findAllByDeletedAtIsNull()
                .map(UserMapper::toResponse);
    }

    public Mono<UserResponseDto> getById(Integer id) {
        return findActive(id)
                .map(user -> UserMapper.toResponse(user));
    }
/** Backs GET /users/me: the caller is known by name, never by id.**/

public Mono<UserResponseDto> getByUsername(String  username) {
    return userRepository.findByUsernameAndDeletedAtIsNull(username)
            .switchIfEmpty(Mono.error(new UserNotFoundException(username)))
            .map(UserMapper::toResponse);
    }

    public Mono<UserResponseDto> create (UserCreateRequestDto requestDto) {
        return userRepository.existsByUsername(requestDto.username())
                .flatMap(exists-> exists
                        ? Mono.error(new UsernameAlreadyTakenException(requestDto.username()))
                        : saveNew(requestDto))
                .map(UserMapper::toResponse);
    }

    public Mono<UserResponseDto> update(Integer id,
                                        UserUpdateRequestDto requestDto,
                                        String currentUsername ) {
        return findActive(id)
                .flatMap(user-> applyUpdate(user, requestDto ,currentUsername))
                .flatMap(userRepository::save)
                .map(UserMapper::toResponse);
    }

    public Mono<Void> delete(Integer id, String currentUsername) {
       return findActive(id)
                .flatMap(user -> ensureNotSelf(user, currentUsername, "You can't delete your own account"))
                .flatMap(this::markDeleted)
                .then();

    }

    /**
     * A PUT replaces the whole state, so all three fields are written.
     * The two guards stop an administrator from locking everyone out
     through
     * their own account — there would be nobody left able to undo it.
     */

    private Mono<User> applyUpdate(User user,
                                   UserUpdateRequestDto request,
                                   String currentUsername) {
        if (user.getUsername().equals(currentUsername)) {
            if (request.role() != Role.ADMIN) {
                return Mono.error(new AccessDeniedException(
                        "You cannot take the ADMIN role away from yourself"));
            }
            if (request.status() != UserStatus.ACTIVE) {
                return Mono.error(new AccessDeniedException ("You cannot block yourself"));
            }
        }

        user.setUsername(request.username());
        user.setRole(request.role());
        user.setStatus(request.status());
        return Mono.just(user);
    }


    private Mono<User> markDeleted(User user) {
        user.setDeletedAt(LocalDateTime.now());
        log.info("Soft-deleting user {}", user.getUsername());
        return userRepository.save(user);
    }

    private Mono<User> ensureNotSelf(User user, String currentUsername,
                                     String message) {
        return user.getUsername().equals(currentUsername)
                ? Mono.error(new AccessDeniedException(message))
                : Mono.just(user);
    }

    private Mono<User> findActive(Integer id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .switchIfEmpty(Mono.error(new
                        UserNotFoundException(id)));
    }

    private Mono<User> saveNew(UserCreateRequestDto request) {
        User user = User.builder()
                .username(request.username())

                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .status(UserStatus.ACTIVE)
                .build();

        log.info("Creating user {} with role {}", request.username(),
                request.role());
        return userRepository.save(user);
    }
}
