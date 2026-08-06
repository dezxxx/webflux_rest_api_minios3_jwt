package com.dezxxx.minios3.rest;

import com.dezxxx.minios3.dto.ErrorResponseDto;
import com.dezxxx.minios3.dto.file.FileResponseDto;
import com.dezxxx.minios3.dto.file.FileUpdateRequestDto;
import com.dezxxx.minios3.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Files",
        description = "Every file has two halves: a row in MySQL and an object in MinIO. "
                + "A plain USER only ever sees their own; ADMIN and MODERATOR see all of them. "
                + "A file that belongs to somebody else answers 404, never 403 — otherwise "
                + "guessing ids would tell you which ones exist.")
public class FileControllerV1 {

    private final FileService fileService;

    @Operation(summary = "Upload file",
            description = "multipart/form-data, one part named \"file\". The owner is taken "
                    + "from the token, so a file cannot be uploaded on somebody else's "
                    + "behalf. The bytes go to the bucket under a freshly generated key, "
                    + "the row is written afterwards, and a CREATED event is recorded "
                    + "without the caller asking for it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Stored"),
            @ApiResponse(responseCode = "400",
                    description = "No part named \"file\" in the request",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "No token, or not an access token",
                    content = @Content)
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<FileResponseDto> uploadFile(
            @RequestPart("file") FilePart filePart,
            @AuthenticationPrincipal String username) {
        return fileService.upload(filePart, username);
    }

    @Operation(summary = "Get files",
               description = "One path, two answers. A plain USER gets their own files and has "
                       + "no way to ask for anybody else's; ADMIN and MODERATOR get every file "
                       + "in the system, which is what makes the owner name in the response "
                       + "worth having. Deleted files are left out of both.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Your files, or all of them"),
            @ApiResponse(responseCode = "401", description = "No token, or not an access token",
                    content = @Content)
    })
    @GetMapping
    public Flux<FileResponseDto> findAllFiles(@AuthenticationPrincipal String username) {
        return fileService.getAll(username);
    }

    @Operation(summary = "Get file by id",
               description = "Returns the record, not the bytes — there is no download endpoint. "
                       + "A file owned by somebody else answers 404 rather than 403: a 403 would "
                       + "confirm the id exists and let anyone map the storage by counting.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The file"),
            @ApiResponse(responseCode = "404",
                    description = "FILE_NOT_FOUND — no such id, it was deleted, or it is not yours",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/{fileId}")
    public Mono<FileResponseDto> findFileById(
            @Parameter(description = "Primary key of the file")
            @PathVariable Integer fileId,
            @AuthenticationPrincipal String username) {
        return fileService.getById(fileId, username);
    }

    @Operation(summary = "Update file",
               description = "ADMIN and MODERATOR only — the specification gives a plain USER "
                       + "reading and uploading, nothing else. A PUT, so name and status are "
                       + "written together. Neither the object key nor the owner can be changed: "
                       + "renaming here touches the row alone, the bytes stay where they are "
                       + "under the same key. An UPDATED event is recorded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — blank name or missing status",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED — a plain USER asked",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "FILE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN' , 'MODERATOR')")
    public Mono<FileResponseDto> updateFile(
            @Parameter(description = "Primary key of the file to replace")
            @PathVariable Integer fileId,
                                            @Valid @RequestBody FileUpdateRequestDto requestDto,
                                            @AuthenticationPrincipal String username) {
        return fileService.update(fileId, requestDto, username);
    }

    @Operation(summary = "Delete file",
               description = "ADMIN and MODERATOR only. Soft delete, and the object is left in "
                       + "the bucket on purpose: the row is kept for statistics, so throwing the "
                       + "bytes away would leave a history that can no longer be replayed. The "
                       + "file then answers 404 everywhere. A DELETED event is recorded.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED — a plain USER asked",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "FILE_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteFile(
            // No example on purpose: Postman imports it as the actual path value,
            // and a pre-filled id turns this into a one-click delete
            @Parameter(description = "Primary key of the file to delete")
            @PathVariable Integer fileId,
            @AuthenticationPrincipal String username) {
        return fileService.delete(fileId, username);
    }
}
