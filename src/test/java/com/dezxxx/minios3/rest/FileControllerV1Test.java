package com.dezxxx.minios3.rest;

import com.dezxxx.minios3.dto.file.FileResponseDto;
import com.dezxxx.minios3.dto.file.FileUpdateRequestDto;
import com.dezxxx.minios3.exception.FileNotFoundException;
import com.dezxxx.minios3.model.status.FileStatus;
import com.dezxxx.minios3.security.JwtTokenProvider;
import com.dezxxx.minios3.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

/**
 * A slice test: only the web layer is started and FileService is a mock. Nothing behind the
 * controller exists — no database, no MinIO, no Docker, and the whole class runs in about a
 * second.
 *
 * <p>The integration suite covers the same endpoints for real, so the question here is
 * narrower: does the controller itself behave. Is the multipart part bound by the right
 * name, is the body validated before the service is consulted, does the role check fire
 * ahead of any work, and does an exception become the right status and the right body.
 * Those are the controller's own responsibilities, and none of them need a database to
 * answer.
 */
@WebFluxTest(FileControllerV1.class)
@Import(FileControllerV1Test.MethodSecurity.class)
@DisplayName("FileControllerV1 (web layer only)")
class FileControllerV1Test {

    private static final String USERNAME = "vasya";

    /**
     * The slice does not load SecurityConfig, and @EnableReactiveMethodSecurity lives there.
     * Without this the @PreAuthorize annotations would be ignored and the role test would
     * pass for the wrong reason.
     */
    @TestConfiguration
    @EnableReactiveMethodSecurity
    static class MethodSecurity {
    }

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private FileService fileService;

    /*
     * JwtAuthenticationFilter is a WebFilter, and the slice loads those. It needs the token
     * provider, which is not part of the web layer; a mock satisfies it, and the caller is
     * supplied below instead of by a real token.
     */
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("upload passes the part named \"file\" straight to the service and answers 201")
    void uploadReturnsCreated() {
        when(fileService.upload(any(), anyString())).thenReturn(Mono.just(response()));

        as(USERNAME, "USER")
                .post().uri("/api/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart("file").build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("passport.pdf");
    }

    @Test
    @DisplayName("a part under any other name is a 400, and the service is never reached")
    void uploadRequiresThePartToBeNamedFile() {
        as(USERNAME, "USER")
                .post().uri("/api/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart("document").build()))
                .exchange()
                .expectStatus().isBadRequest();

        // "file" is what ties the controller to the FormData key the browser sends
        verify(fileService, never()).upload(any(), anyString());
    }

    @Test
    @DisplayName("the list is serialised as JSON, owner name included")
    void listIsSerialised() {
        when(fileService.getAll(USERNAME)).thenReturn(Flux.just(response()));

        as(USERNAME, "USER")
                .get().uri("/api/v1/files")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].ownerUsername").isEqualTo(USERNAME)
                .jsonPath("$[0].status").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("a blank name is refused by @Valid before the service is consulted")
    void updateValidatesTheBody() {
        as(USERNAME, "ADMIN")
                .put().uri("/api/v1/files/7")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new FileUpdateRequestDto("   ", FileStatus.ACTIVE))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED");

        verify(fileService, never()).update(anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("a plain USER is stopped by @PreAuthorize, and the service is never called")
    void updateIsClosedToPlainUser() {
        as(USERNAME, "USER")
                .put().uri("/api/v1/files/7")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new FileUpdateRequestDto("renamed.pdf", FileStatus.ACTIVE))
                .exchange()
                .expectStatus().isForbidden();

        // The role is checked before the method body runs, not inside it
        verify(fileService, never()).update(anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("FileNotFoundException becomes a 404 in the standard error shape")
    void missingFileBecomesNotFound() {
        when(fileService.getById(7, USERNAME))
                .thenReturn(Mono.error(new FileNotFoundException(7)));

        as(USERNAME, "USER")
                .get().uri("/api/v1/files/7")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_NOT_FOUND")
                .jsonPath("$.status").isEqualTo(404);
    }

    @Test
    @DisplayName("delete answers 204 with no body at all")
    void deleteReturnsNoContent() {
        when(fileService.delete(7, USERNAME)).thenReturn(Mono.empty());

        as(USERNAME, "MODERATOR")
                .delete().uri("/api/v1/files/7")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    /**
     * Authenticates the way JwtAuthenticationFilter does: the principal is the user name as
     * a plain String, not a UserDetails. @WithMockUser would put a UserDetails there, and
     * {@code @AuthenticationPrincipal String username} would quietly arrive as null.
     */
    private WebTestClient as(String username, String role) {
        return webTestClient.mutateWith(csrf())
                .mutateWith(mockAuthentication(new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)))));
    }

    private MultipartBodyBuilder multipart(String partName) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part(partName, new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)))
                .filename("passport.pdf")
                .contentType(MediaType.APPLICATION_PDF);
        return body;
    }

    private FileResponseDto response() {
        return new FileResponseDto(7, "passport.pdf", "3f9ac1d2.pdf", FileStatus.ACTIVE, USERNAME);
    }
}
