package com.dezxxx.minios3.repository;

import com.dezxxx.minios3.exception.UserNotFoundException;
import com.dezxxx.minios3.model.User;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User, Integer> {

    Mono<User> findByIdAndDeletedAtIsNull(Integer id);

    Mono<User> findByUsernameAndDeletedAtIsNull(String username);

    Flux<User> findAllByDeletedAtIsNull();

    Mono<Boolean> existsByUsername(String username);

    /**
     * The caller behind a token, or an error if that account is gone.
     *
     * <p>Every service that checks ownership starts here: the token carries a name, while
     * the rules are written in terms of an id and a role, and both of those live in the
     * database. Reading the row on each request is what lets a block or a demotion take
     * effect without waiting for the token to expire.
     *
     * <p>A default method rather than a helper bean — the query it wraps is right above it,
     * and the exception belongs to the same question.
     */
    default Mono<User> findCallerOrThrow(String username) {
        return findByUsernameAndDeletedAtIsNull(username)
                .switchIfEmpty(Mono.error(new UserNotFoundException(username)));
    }
}
