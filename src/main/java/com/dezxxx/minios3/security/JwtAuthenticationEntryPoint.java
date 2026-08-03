package com.dezxxx.minios3.security;

import com.dezxxx.minios3.dto.ErrorResponseDto;
import com.dezxxx.minios3.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


import java.time.LocalDateTime;

/**
 * Answers a request that reached a protected path without
 * authentication.
 *
 * <p>Replaces the default entry point, which returned an
 * empty body and a
 * WWW-Authenticate: Basic header — a header that made
 * browsers show a login
 * prompt for a mechanism this application does not have,
 * so no credentials
 * typed into it could ever work.
 *
 * <p>The body is written by hand because this runs before
 * any controller is
 * chosen, so neither the message writers nor
 * GlobalExceptionHandler apply.
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException e) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

// No separate code for a missing token: to the client "absent" and
        // "not valid" mean the same next step, and telling them apart only
        // helps someone probe the API

        ErrorResponseDto body = new ErrorResponseDto(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ErrorCode.INVALID_TOKEN,
                "A valid access token is required in the Authorization header"
        );

        try {
            DataBuffer buffer = response.bufferFactory()
                    .wrap(objectMapper.writeValueAsBytes(body));
            return response.writeWith(Mono.just(buffer));
        } catch (JacksonException ex) {
            log.error("Could not serialize the 401 body", ex);
            return response.setComplete();
        }
    }
}

