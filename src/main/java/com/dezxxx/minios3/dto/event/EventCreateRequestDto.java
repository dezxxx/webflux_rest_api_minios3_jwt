package com.dezxxx.minios3.dto.event;

import com.dezxxx.minios3.model.status.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Body of POST /api/v1/events. There is deliberately no field for the actor: the name comes
 * from the token, so an entry cannot be written into somebody else's history.
 */
@Schema(description = "A manually written audit entry")
public record EventCreateRequestDto(

        @Schema(description = "File the entry is about. It has to exist and not be deleted.",
                example = "1")
        @NotNull(message = "File id is required")
        Integer fileId,

        @Schema(description = "What the entry records", example = "UPDATED")
        @NotNull(message = "Status is required")
        EventStatus status) {
}
