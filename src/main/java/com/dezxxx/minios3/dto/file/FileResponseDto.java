package com.dezxxx.minios3.dto.file;

import com.dezxxx.minios3.model.status.FileStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Everything about a file that may leave the server. The bucket name never does. */
@Schema(description = "A stored file as returned by the API")
public record FileResponseDto(

        @Schema(description = "Primary key", example = "7")
        Integer id,

        @Schema(description = "Name the file had when it was uploaded",
                example = "passport.pdf")
        String name,

        @Schema(description = "Key of the object inside the bucket, not a URL. The address "
                + "of the storage itself is configuration, so it is never stored beside "
                + "the row and never returned here.",
                example = "3f9ac1d2-8b4e-4a17-9c33-0d5e6f7a8b90.pdf")
        String location,

        @Schema(description = "ARCHIVED files stay in the bucket but are read only",
                example = "ACTIVE")
        FileStatus status,

        @Schema(description = "Owner. A plain USER only ever sees their own name here; "
                + "for a MODERATOR this is the only thing telling the files apart.",
                example = "vasya")
        String ownerUsername) {
}
