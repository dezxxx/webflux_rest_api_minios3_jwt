package com.dezxxx.minios3.repository;

import com.dezxxx.minios3.dto.event.EventResponseDto;
import com.dezxxx.minios3.model.Event;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventRepository extends R2dbcRepository<Event, Integer> {

    Mono<Event> findByIdAndDeletedAtIsNull(Integer id);

    Flux<Event> findAllByDeletedAtIsNull();

    Flux<Event> findAllByUserIdAndDeletedAtIsNull(Integer userId);

    Flux<Event> findAllByFileIdAndDeletedAtIsNull(Integer fileId);

    /*
     * The three queries below return the response DTO straight away, because both foreign
     * keys have to be resolved to names and R2DBC has no relations to walk.
     *
     * Only the event's own deleted_at is checked. A deleted file keeps its history: that is
     * the whole reason the bytes and the row are never really thrown away.
     */

    @Query("""
            SELECT e.id, u.username AS username, f.name AS file_name, e.status, e.created_at
            FROM events e
            JOIN users u ON u.id = e.user_id
            JOIN files f ON f.id = e.file_id
            WHERE e.id = :id AND e.deleted_at IS NULL
            """)
    Mono<EventResponseDto> findResponseById(Integer id);

    @Query("""
            SELECT e.id, u.username AS username, f.name AS file_name, e.status, e.created_at
            FROM events e
            JOIN users u ON u.id = e.user_id
            JOIN files f ON f.id = e.file_id
            WHERE e.deleted_at IS NULL
            ORDER BY e.id
            """)
    Flux<EventResponseDto> findAllResponses();

    @Query("""
            SELECT e.id, u.username AS username, f.name AS file_name, e.status, e.created_at
            FROM events e
            JOIN users u ON u.id = e.user_id
            JOIN files f ON f.id = e.file_id
            WHERE e.user_id = :userId AND e.deleted_at IS NULL
            ORDER BY e.id
            """)
    Flux<EventResponseDto> findAllResponsesByUserId(Integer userId);
}
