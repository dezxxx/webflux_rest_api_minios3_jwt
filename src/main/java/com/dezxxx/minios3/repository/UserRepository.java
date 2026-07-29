package com.dezxxx.minios3.repository;

import com.dezxxx.minios3.model.User;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User, Integer> {

    Mono<User> findByIdAndDeletedAtIsNull(Integer id);

    Mono<User> findByUsernameAndDeletedAtIsNull(String username);

    Flux<User> findAllByDeletedAtIsNull();

    Mono<Boolean> existsByUsername(String username);
}
