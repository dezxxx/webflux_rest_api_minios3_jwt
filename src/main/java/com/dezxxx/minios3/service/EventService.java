package com.dezxxx.minios3.service;

import com.dezxxx.minios3.model.Event;
import com.dezxxx.minios3.model.status.EventStatus;
import com.dezxxx.minios3.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Writes the audit trail the specification asks for: an upload always leaves a record
 * behind, and so does every change after it.
 *
 * <p>Only the server creates an event, which is the whole point — a client free to skip
 * this call would make the history worthless. Nothing about an event comes off the wire,
 * so there is no request DTO either.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;

    /**
     * Records one thing that happened to one file.
     *
     * <p>{@code userId} is whoever performed the action, not whoever owns the file. For
     * an upload the two are the same; when a moderator edits somebody else's file they
     * are not, and the moderator is the answer worth keeping.
     */
    public Mono<Void> create(Integer userId, Integer fileId, EventStatus status) {
        Event event = Event.builder()
                .userId(userId)
                .fileId(fileId)
                .status(status)
                .build();

        log.info("Recording {} on file {} by user {}", status, fileId, userId);
        return eventRepository.save(event).then();
    }
}
