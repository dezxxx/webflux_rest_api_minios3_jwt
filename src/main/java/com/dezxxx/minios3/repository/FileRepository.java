package com.dezxxx.minios3.repository;

import com.dezxxx.minios3.model.File;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileRepository extends R2dbcRepository<File, Integer> {

    Mono<File> findByIdAndDeletedAtIsNull(Integer id);

    Flux<File> findAllByDeletedAtIsNull();

    Flux<File> findAllByUserIdAndDeletedAtIsNull(Integer userId);
}
