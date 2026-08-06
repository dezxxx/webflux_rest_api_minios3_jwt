package com.dezxxx.minios3.service;

import com.dezxxx.minios3.dto.file.FileResponseDto;
import com.dezxxx.minios3.dto.file.FileUpdateRequestDto;
import com.dezxxx.minios3.exception.FileNotFoundException;
import com.dezxxx.minios3.exception.UserNotFoundException;
import com.dezxxx.minios3.model.File;
import com.dezxxx.minios3.model.User;
import com.dezxxx.minios3.model.status.EventStatus;
import com.dezxxx.minios3.model.status.FileStatus;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import com.dezxxx.minios3.repository.FileRepository;
import com.dezxxx.minios3.repository.UserRepository;
import com.dezxxx.minios3.storage.S3Storage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests: every collaborator is a mock, so no MySQL and no MinIO are involved. What is
 * being checked here is the decision-making — who may see what, what gets recorded, and in
 * which order the two halves of a file are written.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileService")
class FileServiceTest {

    private static final String OWNER = "vasya";
    private static final String MODERATOR = "petya";

    @Mock
    private FileRepository fileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventService eventService;

    @Mock
    private S3Storage s3Storage;

    @InjectMocks
    private FileService fileService;

    @Test
    @DisplayName("upload stores the bytes before the row and records a CREATED event")
    void uploadStoresBytesThenRow() {
        // given
        User owner = user(1, OWNER, Role.USER);
        FilePart filePart = filePart("passport.pdf", "hello", MediaType.APPLICATION_PDF);
        File saved = file(7, owner.getId());
        FileResponseDto expected = response(7, OWNER);

        when(userRepository.findByUsernameAndDeletedAtIsNull(OWNER)).thenReturn(Mono.just(owner));
        when(s3Storage.upload(any(), any(), any())).thenReturn(Mono.empty());
        when(fileRepository.save(any(File.class))).thenReturn(Mono.just(saved));
        when(eventService.create(1, 7, EventStatus.CREATED)).thenReturn(Mono.empty());
        when(fileRepository.findResponseById(7)).thenReturn(Mono.just(expected));

        // when
        Mono<FileResponseDto> result = fileService.upload(filePart, OWNER);

        // then
        StepVerifier.create(result)
                .expectNext(expected)
                .verifyComplete();

        // An orphaned object is survivable, a row pointing at nothing is not
        InOrder order = inOrder(s3Storage, fileRepository, eventService);
        order.verify(s3Storage).upload(any(), any(), any());
        order.verify(fileRepository).save(any(File.class));
        order.verify(eventService).create(1, 7, EventStatus.CREATED);
    }

    @Test
    @DisplayName("upload keeps the original name but stores the object under a generated key")
    void uploadGeneratesObjectKey() {
        // given
        User owner = user(1, OWNER, Role.USER);
        FilePart filePart = filePart("passport.pdf", "hello", MediaType.APPLICATION_PDF);

        when(userRepository.findByUsernameAndDeletedAtIsNull(OWNER)).thenReturn(Mono.just(owner));
        when(s3Storage.upload(any(), any(), any())).thenReturn(Mono.empty());
        when(fileRepository.save(any(File.class))).thenReturn(Mono.just(file(7, 1)));
        when(eventService.create(anyInt(), anyInt(), any())).thenReturn(Mono.empty());
        when(fileRepository.findResponseById(7)).thenReturn(Mono.just(response(7, OWNER)));

        // when
        fileService.upload(filePart, OWNER).block();

        // then
        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(captor.capture());
        File stored = captor.getValue();

        assertThat(stored.getName()).isEqualTo("passport.pdf");
        assertThat(stored.getLocation()).isNotEqualTo("passport.pdf").endsWith(".pdf");
        assertThat(stored.getStatus()).isEqualTo(FileStatus.ACTIVE);
        assertThat(stored.getUserId()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAll gives a USER only their own files")
    void getAllScopedForUser() {
        // given
        User caller = user(1, OWNER, Role.USER);
        when(userRepository.findByUsernameAndDeletedAtIsNull(OWNER)).thenReturn(Mono.just(caller));
        when(fileRepository.findAllResponsesByUserId(1)).thenReturn(Flux.just(response(7, OWNER)));

        // when
        Flux<FileResponseDto> result = fileService.getAll(OWNER);

        // then
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(fileRepository, never()).findAllResponses();
    }

    @Test
    @DisplayName("getAll gives a MODERATOR every file")
    void getAllUnscopedForModerator() {
        // given
        User caller = user(2, MODERATOR, Role.MODERATOR);
        when(userRepository.findByUsernameAndDeletedAtIsNull(MODERATOR)).thenReturn(Mono.just(caller));
        when(fileRepository.findAllResponses()).thenReturn(Flux.just(response(7, OWNER)));

        // when
        Flux<FileResponseDto> result = fileService.getAll(MODERATOR);

        // then
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(fileRepository, never()).findAllResponsesByUserId(anyInt());
    }

    @Test
    @DisplayName("getById hides somebody else's file behind the same 404 as a missing one")
    void getByIdHidesForeignFile() {
        // given
        User caller = user(3, "kolya", Role.USER);
        when(userRepository.findByUsernameAndDeletedAtIsNull("kolya")).thenReturn(Mono.just(caller));
        when(fileRepository.findResponseById(7)).thenReturn(Mono.just(response(7, OWNER)));

        // when
        Mono<FileResponseDto> result = fileService.getById(7, "kolya");

        // then
        StepVerifier.create(result)
                .expectError(FileNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("getById lets a MODERATOR read a file owned by somebody else")
    void getByIdAllowsModerator() {
        // given
        User caller = user(2, MODERATOR, Role.MODERATOR);
        FileResponseDto expected = response(7, OWNER);
        when(userRepository.findByUsernameAndDeletedAtIsNull(MODERATOR)).thenReturn(Mono.just(caller));
        when(fileRepository.findResponseById(7)).thenReturn(Mono.just(expected));

        // when
        Mono<FileResponseDto> result = fileService.getById(7, MODERATOR);

        // then
        StepVerifier.create(result)
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    @DisplayName("update writes name and status and records an UPDATED event")
    void updateWritesFieldsAndEvent() {
        // given
        User caller = user(2, MODERATOR, Role.MODERATOR);
        File stored = file(7, 1);
        FileUpdateRequestDto request = new FileUpdateRequestDto("renamed.pdf", FileStatus.ARCHIVED);

        when(userRepository.findByUsernameAndDeletedAtIsNull(MODERATOR)).thenReturn(Mono.just(caller));
        when(fileRepository.findByIdAndDeletedAtIsNull(7)).thenReturn(Mono.just(stored));
        when(fileRepository.save(stored)).thenReturn(Mono.just(stored));
        when(eventService.create(2, 7, EventStatus.UPDATED)).thenReturn(Mono.empty());
        when(fileRepository.findResponseById(7)).thenReturn(Mono.just(response(7, OWNER)));

        // when
        fileService.update(7, request, MODERATOR).block();

        // then
        assertThat(stored.getName()).isEqualTo("renamed.pdf");
        assertThat(stored.getStatus()).isEqualTo(FileStatus.ARCHIVED);
        // The event is recorded against the moderator, not against the owner
        verify(eventService).create(2, 7, EventStatus.UPDATED);
    }

    @Test
    @DisplayName("update refuses a file the caller does not own with a 404, not a 403")
    void updateRefusesForeignFile() {
        // given
        User caller = user(3, "kolya", Role.USER);
        when(userRepository.findByUsernameAndDeletedAtIsNull("kolya")).thenReturn(Mono.just(caller));
        when(fileRepository.findByIdAndDeletedAtIsNull(7)).thenReturn(Mono.just(file(7, 1)));

        // when
        Mono<FileResponseDto> result = fileService.update(
                7, new FileUpdateRequestDto("x.pdf", FileStatus.ACTIVE), "kolya");

        // then
        StepVerifier.create(result)
                .expectError(FileNotFoundException.class)
                .verify();

        verify(fileRepository, never()).save(any(File.class));
    }

    @Test
    @DisplayName("delete only sets deleted_at and never touches the bucket")
    void deleteIsSoftAndKeepsTheBytes() {
        // given
        User caller = user(1, OWNER, Role.USER);
        File stored = file(7, 1);

        when(userRepository.findByUsernameAndDeletedAtIsNull(OWNER)).thenReturn(Mono.just(caller));
        when(fileRepository.findByIdAndDeletedAtIsNull(7)).thenReturn(Mono.just(stored));
        when(fileRepository.save(stored)).thenReturn(Mono.just(stored));
        when(eventService.create(1, 7, EventStatus.DELETED)).thenReturn(Mono.empty());

        // when
        StepVerifier.create(fileService.delete(7, OWNER))
                .verifyComplete();

        // then
        assertThat(stored.getDeletedAt()).isNotNull();
        assertThat(stored.getLocation()).isNotNull();
        verify(eventService).create(1, 7, EventStatus.DELETED);
        // Statistics outlive the file, so the object stays in the bucket
        verify(s3Storage, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("a token naming a user who no longer exists is rejected before anything is read")
    void unknownCallerIsRejected() {
        // given
        when(userRepository.findByUsernameAndDeletedAtIsNull("ghost")).thenReturn(Mono.empty());

        // when
        Mono<FileResponseDto> result = fileService.getById(7, "ghost");

        // then
        StepVerifier.create(result)
                .expectError(UserNotFoundException.class)
                .verify();

        verify(fileRepository, never()).findResponseById(anyInt());
    }

    private User user(Integer id, String username, Role role) {
        return User.builder()
                .id(id)
                .username(username)
                .role(role)
                .status(UserStatus.ACTIVE)
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

    private FileResponseDto response(Integer id, String ownerUsername) {
        return new FileResponseDto(id, "passport.pdf", "3f9ac1d2.pdf", FileStatus.ACTIVE, ownerUsername);
    }

    /**
     * A multipart part is an interface, so it mocks cleanly. The content has to be a real
     * DataBuffer: the service drains it and releases it, and a null would blow up there.
     */
    private FilePart filePart(String filename, String content, MediaType contentType) {
        FilePart filePart = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);

        when(filePart.filename()).thenReturn(filename);
        when(filePart.headers()).thenReturn(headers);
        when(filePart.content()).thenReturn(Flux.just(
                new DefaultDataBufferFactory().wrap(content.getBytes(StandardCharsets.UTF_8))));

        return filePart;
    }
}
