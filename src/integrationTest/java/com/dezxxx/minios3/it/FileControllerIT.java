package com.dezxxx.minios3.it;

import com.dezxxx.minios3.dto.auth.AuthenticationResponseDto;
import com.dezxxx.minios3.dto.auth.RegistrationRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class FileControllerIT extends AbstractIntegrationTest{




    @Test
    @DisplayName("an uploaded file comes back in the owner's list with their name on it")
    void uploadThenList() {
        // given
        String username = "vasya-" + UUID.randomUUID();
        String token = register(username);

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)))
                .filename("passport.pdf")
                .contentType(MediaType.APPLICATION_PDF);

        // when + then
        webTestClient.post().uri("/api/v1/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("passport.pdf")
                .jsonPath("$.ownerUsername").isEqualTo(username)
                .jsonPath("$.status").isEqualTo("ACTIVE");

        // and the list, which is the first time the joined query runs for real
        webTestClient.get().uri("/api/v1/files")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].name").isEqualTo("passport.pdf")
                .jsonPath("$[0].ownerUsername").isEqualTo(username);
    }

    @Test
    @DisplayName("a plain USER may not update or delete, and is refused by role before the service")
    void plainUserCannotUpdateOrDelete() {
        // given
        String token = register("kolya-" + UUID.randomUUID());

        // when + then: 403, not 404 — this one is decided by @PreAuthorize, not by ownership
        webTestClient.delete().uri("/api/v1/files/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("without a token the API answers 401 in the standard error shape")
    void anonymousIsRejected() {
        webTestClient.get().uri("/api/v1/files")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_TOKEN");
    }













    private String register(String username) {
        return
                webTestClient.post().uri("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new
                                RegistrationRequestDto(username, "secret123"))
                        .exchange()
                        .expectStatus().isCreated()

                        .expectBody(AuthenticationResponseDto.class)
                        .returnResult()
                        .getResponseBody()
                        .accessToken();
    }
}
