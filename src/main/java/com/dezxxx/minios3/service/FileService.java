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
import com.dezxxx.minios3.repository.FileRepository;
import com.dezxxx.minios3.repository.UserRepository;
import com.dezxxx.minios3.storage.S3Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A file lives in two places at once: the bytes in MinIO, the row in MySQL. This class
 * is the only thing that knows both, and it is where the access rules land — a USER
 * sees nothing but their own, a MODERATOR sees everything, and every change is recorded
 * whether the caller asked for it or not.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private static final String UNNAMED = "unnamed";

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final EventService eventService;
    private final S3Storage s3Storage;

    /**
     * Backs POST /files. The order matters: bytes first, row second.
     *
     * <p>Break in between and an unreferenced object is left in the bucket — invisible,
     * harmless, cheap to sweep up later. The other order would leave a row pointing at
     * nothing, and that one the client sees as a broken file.
     */
    public Mono<FileResponseDto> upload(FilePart filePart, String username) {
        return findCaller(username)
                .flatMap(caller -> readBytes(filePart)
                        .flatMap(content -> store(filePart, content, caller)));
    }

    /** Backs GET /files/{id}. A file belonging to somebody else is simply not there. */
    public Mono<FileResponseDto> getById(Integer id, String username) {
        return findCaller(username)
                .flatMap(caller -> fileRepository.findResponseById(id)
                        .switchIfEmpty(Mono.error(new FileNotFoundException(id)))
                        .filter(file -> maySee(caller, file.ownerUsername()))
                        .switchIfEmpty(Mono.error(new FileNotFoundException(id))));
    }

    /** Backs GET /files. One path, two answers: your own files, or all of them. */
    public Flux<FileResponseDto> getAll(String username) {
        return findCaller(username)
                .flatMapMany(caller -> readsEverything(caller)
                        ? fileRepository.findAllResponses()
                        : fileRepository.findAllResponsesByUserId(caller.getId()));
    }

    /**
     * Backs PUT /files/{id}. Only the two fields describing the row may change: rewriting
     * the object key would orphan the bytes, and handing a file to another owner is not
     * an edit but a transfer, which this API does not offer.
     */
    public Mono<FileResponseDto> update(Integer id,
                                        FileUpdateRequestDto requestDto,
                                        String username) {
        return findCaller(username)
                .flatMap(caller -> findOwned(id, caller)
                        .flatMap(file -> applyUpdate(file, requestDto))
                        .flatMap(fileRepository::save)
                        .flatMap(saved -> eventService
                                .create(caller.getId(), saved.getId(), EventStatus.UPDATED)
                                .then(fileRepository.findResponseById(saved.getId()))));
    }

    /**
     * Backs DELETE /files/{id}. Soft delete, and the bytes stay in the bucket: the row is
     * kept for statistics, so discarding the object would leave a history that can no
     * longer be replayed.
     */
    public Mono<Void> delete(Integer id, String username) {
        return findCaller(username)
                .flatMap(caller -> findOwned(id, caller)
                        .flatMap(this::markDeleted)
                        .flatMap(deleted -> eventService
                                .create(caller.getId(), deleted.getId(), EventStatus.DELETED)))
                .then();
    }

    private Mono<FileResponseDto> store(FilePart filePart, byte[] content, User owner) {
        String key = objectKey(filePart.filename());

        File file = File.builder()
                .userId(owner.getId())
                .name(originalName(filePart))
                .location(key)
                .status(FileStatus.ACTIVE)
                .build();

        return s3Storage.upload(key, content, contentType(filePart))
                .then(fileRepository.save(file))
                .flatMap(saved -> eventService
                        .create(owner.getId(), saved.getId(), EventStatus.CREATED)
                        .then(fileRepository.findResponseById(saved.getId())));
    }

    /**
     * Drains the incoming stream into one array. The whole file sits in memory for the
     * length of the request, which is the price of knowing its size: S3 wants the content
     * length before it will accept the first byte.
     */
    private Mono<byte[]> readBytes(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(buffer -> {
                    byte[] content = new byte[buffer.readableByteCount()];
                    buffer.read(content);
                    // Netty hands out pooled buffers; not returning one leaks it
                    DataBufferUtils.release(buffer);
                    return content;
                });
    }

    /**
     * A fresh UUID keeps the original extension and nothing else. Two people uploading
     * passport.pdf must not collide, and putObject overwrites without complaining.
     */
    private String objectKey(String filename) {
        return UUID.randomUUID() + extensionOf(filename);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        // Nothing before the dot means the name is ".bashrc", not an extension
        return dot > 0 ? filename.substring(dot) : "";
    }

    private String originalName(FilePart filePart) {
        String filename = filePart.filename();
        return filename == null || filename.isBlank() ? UNNAMED : filename;
    }

    private String contentType(FilePart filePart) {
        MediaType type = filePart.headers().getContentType();
        return type != null ? type.toString() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private Mono<File> applyUpdate(File file, FileUpdateRequestDto requestDto) {
        file.setName(requestDto.name());
        file.setStatus(requestDto.status());
        return Mono.just(file);
    }

    private Mono<File> markDeleted(File file) {
        file.setDeletedAt(LocalDateTime.now());
        log.info("Soft-deleting file {} ({})", file.getId(), file.getLocation());
        return fileRepository.save(file);
    }

    /**
     * The same 404 twice, on purpose: a file that exists but belongs to somebody else has
     * to be indistinguishable from one that never existed. A 403 would confirm the id is
     * real and let anyone map the storage by counting upwards.
     */
    private Mono<File> findOwned(Integer id, User caller) {
        return fileRepository.findByIdAndDeletedAtIsNull(id)
                .switchIfEmpty(Mono.error(new FileNotFoundException(id)))
                .filter(file -> readsEverything(caller) || file.getUserId().equals(caller.getId()))
                .switchIfEmpty(Mono.error(new FileNotFoundException(id)));
    }

    /**
     * The token carries a name; the rules are written in terms of an id and a role, and
     * both of those live in the database. Keeping them out of the token is what lets a
     * block or a demotion take effect without waiting for the token to expire.
     */
    private Mono<User> findCaller(String username) {
        return userRepository.findByUsernameAndDeletedAtIsNull(username)
                .switchIfEmpty(Mono.error(new UserNotFoundException(username)));
    }

    private boolean maySee(User caller, String ownerUsername) {
        return readsEverything(caller) || caller.getUsername().equals(ownerUsername);
    }

    /** ADMIN and MODERATOR read every file; the specification says so in as many words. */
    private boolean readsEverything(User caller) {
        return caller.getRole() != Role.USER;
    }
}
