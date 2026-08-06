package com.dezxxx.minios3.rest;

import com.dezxxx.minios3.dto.ErrorResponseDto;
import com.dezxxx.minios3.dto.event.EventCreateRequestDto;
import com.dezxxx.minios3.dto.event.EventResponseDto;
import com.dezxxx.minios3.dto.event.EventUpdateRequestDto;
import com.dezxxx.minios3.service.EventService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Events",
     description = "The audit trail. Entries are written by the server on every upload, "
             + "update and delete, so this is normally a read-only history. A USER sees only "
             + "what they did themselves; ADMIN and MODERATOR see everything and may correct "
             + "or hide an entry.")
public class EventControllerV1 {

    private final EventService eventService;

    @Operation(summary = "Create event",
               description = "ADMIN only, and it exists because the specification asks for full "
                       + "CRUD on Event. Writing history by hand is not how the trail is meant "
                       + "to fill up — every real entry is written by the server at the moment "
                       + "the action happens. The actor is taken from the token and not from the "
                       + "body, so even an administrator cannot file an entry under another name.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recorded"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED — missing file id or status",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED — caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "FILE_NOT_FOUND — no such file, or it was deleted",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EventResponseDto> createEvent(@Valid @RequestBody EventCreateRequestDto requestDto,
                                              @AuthenticationPrincipal String username) {
        return eventService.create(requestDto, username);
    }

    @Operation(summary = "Get events",
               description = "One path, two answers, the same as for files: your own history, "
                       + "or all of it. Entries about a deleted file are still listed — keeping "
                       + "the row and the bytes is what makes the history readable later.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Your events, or all of them"),
            @ApiResponse(responseCode = "401", description = "No token, or not an access token",
                    content = @Content)
    })
    @GetMapping
    public Flux<EventResponseDto> findAllEvents(@AuthenticationPrincipal String username) {
        return eventService.getAll(username);
    }

    @Operation(summary = "Get event by id",
               description = "An entry recorded by somebody else answers 404 rather than 403, "
                       + "so no id can be confirmed to exist by probing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The event"),
            @ApiResponse(responseCode = "404",
                    description = "EVENT_NOT_FOUND — no such id, it was deleted, or it is not yours",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/{eventId}")
    public Mono<EventResponseDto> findEventById(
            @Parameter(description = "Primary key of the event")
            @PathVariable Integer eventId,
            @AuthenticationPrincipal String username) {
        return eventService.getById(eventId, username);
    }

    @Operation(summary = "Update event",
               description = "ADMIN and MODERATOR only — the specification gives a MODERATOR "
                       + "\"чтение/изменение/удаление всех Events\". Only the status may be "
                       + "corrected: the file, the actor and the timestamp are what the entry is "
                       + "evidence of.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED — missing status",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED — a plain USER asked",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public Mono<EventResponseDto> updateEvent(
            @Parameter(description = "Primary key of the event to correct")
            @PathVariable Integer eventId,
            @Valid @RequestBody EventUpdateRequestDto requestDto,
            @AuthenticationPrincipal String username) {
        return eventService.update(eventId, requestDto, username);
    }

    @Operation(summary = "Delete event",
               description = "ADMIN and MODERATOR only. Soft delete: the row stays in the table "
                       + "and only stops being read, so an entry can be hidden but never truly "
                       + "erased — which is the point of an audit trail.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED — a plain USER asked",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "EVENT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @DeleteMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteEvent(
            // No example on purpose: Postman imports it as the actual path value,
            // and a pre-filled id turns this into a one-click delete
            @Parameter(description = "Primary key of the event to delete")
            @PathVariable Integer eventId,
            @AuthenticationPrincipal String username) {
        return eventService.delete(eventId, username);
    }
}
