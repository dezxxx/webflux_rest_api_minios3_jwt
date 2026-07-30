package com.dezxxx.minios3.dto;
import com.dezxxx.minios3.exception.ErrorCode;
import java.time.LocalDateTime;

public record ErrorResponseDto(LocalDateTime timestamp,
                               int status,
                               String error,
                               ErrorCode code,
                               String message) {
}
