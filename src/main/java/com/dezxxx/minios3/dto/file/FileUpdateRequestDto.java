package com.dezxxx.minios3.dto.file;

import com.dezxxx.minios3.model.status.FileStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/v1/files/{id}. Only the two fields describing the row are here: the
 * stored bytes are never touched by an update, so neither the object key nor the owner
 * can be changed through this endpoint.
 */
@Schema(description = "Full replacement of a file record. Both fields are mandatory.")
public record FileUpdateRequestDto(

        @Schema(description = "New name shown to the user. Renaming here does not move or "
                + "rename the object in the bucket.",
                example = "passport-scan.pdf")
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Schema(description = "ARCHIVED keeps the bytes but marks the file as read only",
                example = "ARCHIVED")
        @NotNull(message = "Status is required")
        FileStatus status) {
}
