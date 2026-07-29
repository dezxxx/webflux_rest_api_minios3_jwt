package com.dezxxx.minios3.repository;

import com.dezxxx.minios3.model.Event;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventRepository extends R2dbcRepository<Event, Integer> {

    Mono<Event> findByIdAndDeletedAtIsNull(Integer id);

    Flux<Event> findAllByDeletedAtIsNull();

    Flux<Event> findAllByUserIdAndDeletedAtIsNull(Integer userId);

    Flux<Event> findAllByFileIdAndDeletedAtIsNull(Integer fileId);
}
