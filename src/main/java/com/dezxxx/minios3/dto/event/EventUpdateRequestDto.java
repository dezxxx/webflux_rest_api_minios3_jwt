package com.dezxxx.minios3.dto.event;

import com.dezxxx.minios3.model.status.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Body of PUT /api/v1/events/{id}. Only the status may be corrected — moving an entry to a
 * different file or a different user would not be an edit but a forgery.
 */
@Schema(description = "Correction of a recorded action")
public record EventUpdateRequestDto(

        @Schema(description = "The corrected action", example = "UPDATED")
        @NotNull(message = "Status is required")
        EventStatus status) {
}
