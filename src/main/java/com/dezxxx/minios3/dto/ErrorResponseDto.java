package com.dezxxx.minios3.dto;

import com.dezxxx.minios3.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** The single shape every failure returns, whatever the status. */
@Schema(description = "Error payload returned by every endpoint on failure")
public record ErrorResponseDto(

        @Schema(description = "Server time the failure was built at",
                example = "2026-08-03T01:15:17.673")
        LocalDateTime timestamp,

        @Schema(description = "HTTP status, repeated here so the body is self-contained",
                example = "401")
        int status,

        @Schema(description = "Reason phrase of the status", example = "Unauthorized")
        String error,

        @Schema(description = "Machine-readable cause. Stays stable even when the message "
                + "is reworded, so a client can branch on it.",
                example = "INVALID_CREDENTIALS")
        ErrorCode code,

        @Schema(description = "Human-readable explanation", example = "Invalid user name or password")
        String message) {
}