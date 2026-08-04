package com.dezxxx.minios3.repository;

import com.dezxxx.minios3.dto.file.FileResponseDto;
import com.dezxxx.minios3.model.File;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileRepository extends R2dbcRepository<File, Integer> {

    // --- Entities: used for writing and for the ownership check, where the
    //     API contract is irrelevant and the whole row is needed.

    Mono<File> findByIdAndDeletedAtIsNull(Integer id);

    Flux<File> findAllByDeletedAtIsNull();

    Flux<File> findAllByUserIdAndDeletedAtIsNull(Integer userId);

    // --- Projections: the owner name lives in another table, so every read that
    //     leaves the server is a join. Returning the DTO straight from here keeps
    //     it to one query; the alternative is a second trip to users per file.
    //
    //     Two things are ours to get right now that the SQL is written by hand:
    //     the owner_username alias, without which the column never reaches the
    //     record component, and deleted_at, which the derived queries above used
    //     to take care of by name.

    @Query("""
            SELECT f.id, f.name, f.location, f.status, u.username AS owner_username
            FROM files f
            JOIN users u ON u.id = f.user_id
            WHERE f.id = :id AND f.deleted_at IS NULL
            """)
    Mono<FileResponseDto> findResponseById(Integer id);

    @Query("""
            SELECT f.id, f.name, f.location, f.status, u.username AS owner_username
            FROM files f
            JOIN users u ON u.id = f.user_id
            WHERE f.deleted_at IS NULL
            """)
    Flux<FileResponseDto> findAllResponses();

    @Query("""
            SELECT f.id, f.name, f.location, f.status, u.username AS owner_username
            FROM files f
            JOIN users u ON u.id = f.user_id
            WHERE f.user_id = :userId AND f.deleted_at IS NULL
            """)
    Flux<FileResponseDto> findAllResponsesByUserId(Integer userId);
}
