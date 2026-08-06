package com.dezxxx.minios3.dto.event;

import com.dezxxx.minios3.model.status.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * One line of the audit trail. Both foreign keys are resolved to names here: an id tells
 * the reader nothing, and the whole point of this endpoint is that a human can read it.
 */
@Schema(description = "A recorded action on a file")
public record EventResponseDto(

        @Schema(description = "Primary key", example = "7")
        Integer id,

        @Schema(description = "Who performed the action, not who owns the file. A moderator "
                + "editing somebody else's file appears here under their own name.",
                example = "admin")
        String username,

        @Schema(description = "Name the file carried at the moment the event was written",
                example = "passport.pdf")
        String fileName,

        @Schema(description = "What was done to the file", example = "CREATED")
        EventStatus status,

        @Schema(description = "When it happened, filled in by the server",
                example = "2026-08-06T21:17:07")
        LocalDateTime createdAt) {
}
