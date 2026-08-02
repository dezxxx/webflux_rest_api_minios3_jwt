package com.dezxxx.minios3.exception;

import com.dezxxx.minios3.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/** Turns exceptions into the ErrorResponseDto shape with a meaningful status code. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponseDto handleUserNotFoundException(UserNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, e.getMessage());
    }


    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponseDto handleInvalidCredentials(InvalidCredentialsException e) {
        // Only debug: a failed login is routine, not an application fault
        log.debug("Rejected login: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, e.getMessage());
    }

    @ExceptionHandler(UserBlockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponseDto handleUserBlocked(UserBlockedException e) {
        log.info("Blocked user tried to log in: {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, ErrorCode.USER_BLOCKED, e.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponseDto handleUsernameTaken(UsernameAlreadyTakenException e) {
        return build(HttpStatus.CONFLICT, ErrorCode.USERNAME_TAKEN, e.getMessage());
    }

    /** Two registrations racing past existsByUsername are stopped by the unique constraint. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponseDto handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Constraint violated: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT, "Resource already exists");
    }

    /** Raised by @Valid when the incoming DTO fails its constraints. */
    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponseDto handleValidation(WebExchangeBindException e) {
        String message = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    /** The refresh token ran out: the client has to log in again. */
    @ExceptionHandler(ExpiredTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponseDto handleExpiredToken(ExpiredTokenException e) {
        log.debug("Expired token presented: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, e.getMessage());
    }

    /** Forged, malformed, or the wrong kind of token — refreshing will not help. */
    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponseDto handleInvalidToken(InvalidTokenException e) {
        log.debug("Invalid token presented: {}", e.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, e.getMessage());
    }

    /** Raised by @PreAuthorize when the role does not allow the call. */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponseDto handleAccessDenied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, e.getMessage() != null ? e.getMessage() : "Access denied");
    }

    /** Spring already chose a status (404, 415, 406...) — keep it instead of masking it as 500. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDto> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String message = e.getReason() != null ? e.getReason() : status.getReasonPhrase();

        return ResponseEntity.status(status)
                .body(build(status, ErrorCode.REQUEST_FAILED, message));
    }

    /** Last resort: log the cause, but never leak a stack trace to the client. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponseDto handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Internal server error");
    }

    private ErrorResponseDto build(HttpStatus status, ErrorCode code, String message) {
        return new ErrorResponseDto(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message);
    }
}