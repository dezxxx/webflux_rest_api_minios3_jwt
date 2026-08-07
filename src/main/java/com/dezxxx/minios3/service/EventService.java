package com.dezxxx.minios3.service;

import com.dezxxx.minios3.dto.event.EventCreateRequestDto;
import com.dezxxx.minios3.dto.event.EventResponseDto;
import com.dezxxx.minios3.dto.event.EventUpdateRequestDto;
import com.dezxxx.minios3.exception.EventNotFoundException;
import com.dezxxx.minios3.exception.FileNotFoundException;
import com.dezxxx.minios3.model.Event;
import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.EventStatus;
import com.dezxxx.minios3.repository.EventRepository;
import com.dezxxx.minios3.repository.FileRepository;
import com.dezxxx.minios3.repository.UserRepository;
import com.dezxxx.minios3.util.AccessRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Writes the audit trail the specification asks for: an upload always leaves a record
 * behind, and so does every change after it.
 *
 * <p>Normally only the server creates an event — a client free to skip that call would make
 * the history worthless. The public create below exists because the specification asks for
 * full CRUD on Event; it is closed to everyone but an administrator, and even there the
 * actor is taken from the token rather than from the body.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;

    /**
     * Records one thing that happened to one file. Called from {@link FileService}, not
     * from a controller.
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

    /**
     * Backs POST /events. The file is looked up first: an entry pointing at a file that
     * does not exist would survive in the trail forever and could never be explained.
     */
    public Mono<EventResponseDto> create(EventCreateRequestDto requestDto, String username) {
        return findCaller(username)
                .flatMap(caller -> fileRepository.findByIdAndDeletedAtIsNull(requestDto.fileId())
                        .switchIfEmpty(Mono.error(new FileNotFoundException(requestDto.fileId())))
                        .flatMap(file -> save(caller, file.getId(), requestDto.status())));
    }

    /** Backs GET /events/{id}. Somebody else's entry is simply not there. */
    public Mono<EventResponseDto> getById(Integer id, String username) {
        return findCaller(username)
                .flatMap(caller -> eventRepository.findResponseById(id)
                        .switchIfEmpty(Mono.error(new EventNotFoundException(id)))
                        .filter(event -> AccessRules.maySee(caller, event.username()))
                        .switchIfEmpty(Mono.error(new EventNotFoundException(id))));
    }

    /** Backs GET /events. A USER reads their own history, the other two read all of it. */
    public Flux<EventResponseDto> getAll(String username) {
        return findCaller(username)
                .flatMapMany(caller -> AccessRules.readsEverything(caller)
                        ? eventRepository.findAllResponses()
                        : eventRepository.findAllResponsesByUserId(caller.getId()));
    }

    /**
     * Backs PUT /events/{id}. Only the status may be corrected: the file, the actor and the
     * timestamp are what the entry is evidence of, and rewriting those is not an edit.
     */
    public Mono<EventResponseDto> update(Integer id,
                                         EventUpdateRequestDto requestDto,
                                         String username) {
        return findCaller(username)
                .flatMap(caller -> findVisible(id, caller)
                        .flatMap(event -> applyUpdate(event, requestDto))
                        .flatMap(saved -> eventRepository.findResponseById(saved.getId())));
    }

    /** Backs DELETE /events/{id}. Soft delete — the row stays, it just stops being read. */
    public Mono<Void> delete(Integer id, String username) {
        return findCaller(username)
                .flatMap(caller -> findVisible(id, caller)
                        .flatMap(this::markDeleted))
                .then();
    }

    private Mono<EventResponseDto> save(User caller, Integer fileId, EventStatus status) {
        Event event = Event.builder()
                .userId(caller.getId())
                .fileId(fileId)
                .status(status)
                .build();

        log.info("Recording {} on file {} by user {} by hand", status, fileId, caller.getId());
        return eventRepository.save(event)
                .flatMap(saved -> eventRepository.findResponseById(saved.getId()));
    }

    private Mono<Event> applyUpdate(Event event, EventUpdateRequestDto requestDto) {
        event.setStatus(requestDto.status());
        return eventRepository.save(event);
    }

    private Mono<Event> markDeleted(Event event) {
        event.setDeletedAt(LocalDateTime.now());
        log.info("Soft-deleting event {}", event.getId());
        return eventRepository.save(event);
    }

    /**
     * The same 404 twice, exactly as for files: an entry that exists but belongs to another
     * user has to look the same as one that never existed.
     */
    private Mono<Event> findVisible(Integer id, User caller) {
        return eventRepository.findByIdAndDeletedAtIsNull(id)
                .switchIfEmpty(Mono.error(new EventNotFoundException(id)))
                .filter(event -> AccessRules.readsEverything(caller)
                        || event.getUserId().equals(caller.getId()))
                .switchIfEmpty(Mono.error(new EventNotFoundException(id)));
    }

    private Mono<User> findCaller(String username) {
        return userRepository.findCallerOrThrow(username);
    }
}
