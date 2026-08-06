package com.dezxxx.minios3.service;

import com.dezxxx.minios3.dto.event.EventCreateRequestDto;
import com.dezxxx.minios3.dto.event.EventResponseDto;
import com.dezxxx.minios3.dto.event.EventUpdateRequestDto;
import com.dezxxx.minios3.exception.EventNotFoundException;
import com.dezxxx.minios3.exception.FileNotFoundException;
import com.dezxxx.minios3.model.Event;
import com.dezxxx.minios3.model.File;
import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.EventStatus;
import com.dezxxx.minios3.model.status.FileStatus;
import com.dezxxx.minios3.repository.EventRepository;
import com.dezxxx.minios3.repository.FileRepository;
import com.dezxxx.minios3.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The audit trail has one property worth defending above all: an entry must say who really
 * did something. Most of these tests are about that, the rest mirror the file rules.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventService")
class EventServiceTest {

    private static final String VASYA = "vasya";
    private static final String PETYA = "petya";

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    @DisplayName("getAll gives a USER only the entries they made themselves")
    void getAllScopedForUser() {
        // given
        User caller = TestUsersUtil.user(1, VASYA);
        when(userRepository.findByUsernameAndDeletedAtIsNull(VASYA)).thenReturn(Mono.just(caller));
        when(eventRepository.findAllResponsesByUserId(1)).thenReturn(Flux.just(response(5, VASYA)));

        // when
        Flux<EventResponseDto> result = eventService.getAll(VASYA);

        // then
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        // Without this the test would pass even if the service returned everybody's history
        verify(eventRepository, never()).findAllResponses();
    }

    @Test
    @DisplayName("getAll gives a MODERATOR the whole trail")
    void getAllUnscopedForModerator() {
        // given
        User caller = TestUsersUtil.moderator(2, PETYA);
        when(userRepository.findByUsernameAndDeletedAtIsNull(PETYA)).thenReturn(Mono.just(caller));
        when(eventRepository.findAllResponses()).thenReturn(Flux.just(response(5, VASYA)));

        // when
        Flux<EventResponseDto> result = eventService.getAll(PETYA);

        // then
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(eventRepository, never()).findAllResponsesByUserId(anyInt());
    }

    @Test
    @DisplayName("getById hides an entry made by somebody else behind a 404")
    void getByIdHidesForeignEntry() {
        // given
        User caller = TestUsersUtil.user(3, "kolya");
        when(userRepository.findByUsernameAndDeletedAtIsNull("kolya")).thenReturn(Mono.just(caller));
        when(eventRepository.findResponseById(5)).thenReturn(Mono.just(response(5, VASYA)));

        // when
        Mono<EventResponseDto> result = eventService.getById(5, "kolya");

        // then
        StepVerifier.create(result)
                .expectError(EventNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("a manual entry records the caller from the token, not anyone named in the body")
    void createRecordsTheCallerAsActor() {
        // given: an administrator files an entry about a file owned by vasya
        User caller = TestUsersUtil.admin(9, "admin");
        when(userRepository.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Mono.just(caller));
        when(fileRepository.findByIdAndDeletedAtIsNull(7)).thenReturn(Mono.just(file(7, 1)));
        when(eventRepository.save(any(Event.class))).thenReturn(Mono.just(event(5, 9, 7)));
        when(eventRepository.findResponseById(5)).thenReturn(Mono.just(response(5, "admin")));

        EventCreateRequestDto requestDto = new EventCreateRequestDto(7, EventStatus.UPDATED);

        // when
        eventService.create(requestDto, "admin").block();

        // then
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        Event stored = captor.getValue();

        // 9 is the administrator, 1 is the owner of the file — the actor is the one who acted
        assertThat(stored.getUserId()).isEqualTo(9);
        assertThat(stored.getFileId()).isEqualTo(7);
        assertThat(stored.getStatus()).isEqualTo(EventStatus.UPDATED);
    }

    @Test
    @DisplayName("an entry about a file that does not exist is never written")
    void createRejectsUnknownFile() {
        // given
        User caller = TestUsersUtil.admin(9, "admin");
        when(userRepository.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Mono.just(caller));
        when(fileRepository.findByIdAndDeletedAtIsNull(404)).thenReturn(Mono.empty());

        EventCreateRequestDto requestDto = new EventCreateRequestDto(404, EventStatus.CREATED);

        // when
        Mono<EventResponseDto> result = eventService.create(requestDto, "admin");

        // then
        StepVerifier.create(result)
                .expectError(FileNotFoundException.class)
                .verify();

        // An entry pointing at nothing would sit in the trail forever, unexplainable
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("update changes the status and leaves the actor and the file alone")
    void updateChangesStatusOnly() {
        // given
        User caller = TestUsersUtil.moderator(2, PETYA);
        Event stored = event(5, 1, 7);

        when(userRepository.findByUsernameAndDeletedAtIsNull(PETYA)).thenReturn(Mono.just(caller));
        when(eventRepository.findByIdAndDeletedAtIsNull(5)).thenReturn(Mono.just(stored));
        when(eventRepository.save(stored)).thenReturn(Mono.just(stored));
        when(eventRepository.findResponseById(5)).thenReturn(Mono.just(response(5, VASYA)));

        // when
        eventService.update(5, new EventUpdateRequestDto(EventStatus.DELETED), PETYA).block();

        // then
        assertThat(stored.getStatus()).isEqualTo(EventStatus.DELETED);
        assertThat(stored.getUserId()).isEqualTo(1);
        assertThat(stored.getFileId()).isEqualTo(7);
    }

    @Test
    @DisplayName("delete only sets deleted_at, so an entry can be hidden but never erased")
    void deleteIsSoft() {
        // given
        User caller = TestUsersUtil.moderator(2, PETYA);
        Event stored = event(5, 1, 7);

        when(userRepository.findByUsernameAndDeletedAtIsNull(PETYA)).thenReturn(Mono.just(caller));
        when(eventRepository.findByIdAndDeletedAtIsNull(5)).thenReturn(Mono.just(stored));
        when(eventRepository.save(stored)).thenReturn(Mono.just(stored));

        // when
        StepVerifier.create(eventService.delete(5, PETYA))
                .verifyComplete();

        // then
        assertThat(stored.getDeletedAt()).isNotNull();
        assertThat(stored.getUserId()).isEqualTo(1);
    }

    private Event event(Integer id, Integer userId, Integer fileId) {
        return Event.builder()
                .id(id)
                .userId(userId)
                .fileId(fileId)
                .status(EventStatus.CREATED)
                .build();
    }

    private File file(Integer id, Integer userId) {
        return File.builder()
                .id(id)
                .userId(userId)
                .name("passport.pdf")
                .location("3f9ac1d2.pdf")
                .status(FileStatus.ACTIVE)
                .build();
    }

    private EventResponseDto response(Integer id, String username) {
        return new EventResponseDto(id, username, "passport.pdf", EventStatus.CREATED,
                LocalDateTime.of(2026, 8, 6, 21, 17));
    }
}
