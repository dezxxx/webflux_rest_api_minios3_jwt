package com.dezxxx.minios3.it;

import com.dezxxx.minios3.dto.auth.AuthenticationResponseDto;
import com.dezxxx.minios3.dto.auth.RegistrationRequestDto;
import com.dezxxx.minios3.dto.file.FileResponseDto;
import com.dezxxx.minios3.dto.file.FileUpdateRequestDto;
import com.dezxxx.minios3.model.status.FileStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("the owner reads their own file by id")
    void ownerReadsOwnFile() {
        String username = uniqueName("vasya");
        String token = registerUser(username);
        Integer fileId = uploadFile(token, "passport.pdf");

        webTestClient.get().uri("/api/v1/files/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(fileId)
                .jsonPath("$.name").isEqualTo("passport.pdf")
                .jsonPath("$.ownerUsername").isEqualTo(username)
                // The key is generated, so the stored object cannot collide with anyone else's
                .jsonPath("$.location").value(location ->
                        assertThat((String) location).endsWith(".pdf").isNotEqualTo("passport.pdf"));
    }

    @Test
    @DisplayName("somebody else's file answers 404, so ids cannot be probed")
    void foreignFileIsHidden() {
        Integer fileId = uploadFile(registerUser(uniqueName("vasya")), "passport.pdf");
        String strangerToken = registerUser(uniqueName("kolya"));

        webTestClient.get().uri("/api/v1/files/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken))
                .exchange()
                // The file exists; a 403 would say so, and 404 is the whole point
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_NOT_FOUND");
    }

    @Test
    @DisplayName("a MODERATOR reads a file owned by somebody else")
    void moderatorReadsForeignFile() {
        String owner = uniqueName("vasya");
        Integer fileId = uploadFile(registerUser(owner), "passport.pdf");
        String moderatorToken = createModerator(uniqueName("moderator"));

        webTestClient.get().uri("/api/v1/files/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(moderatorToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ownerUsername").isEqualTo(owner);
    }

    @Test
    @DisplayName("a MODERATOR renames and archives a file, and the change is recorded")
    void moderatorUpdatesFile() {
        String owner = uniqueName("vasya");
        String ownerToken = registerUser(owner);
        Integer fileId = uploadFile(ownerToken, "passport.pdf");
        String moderatorToken = createModerator(uniqueName("moderator"));

        webTestClient.put().uri("/api/v1/files/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(moderatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new FileUpdateRequestDto("renamed.pdf", FileStatus.ARCHIVED))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("renamed.pdf")
                .jsonPath("$.status").isEqualTo("ARCHIVED")
                // Renaming touches the row alone: the object keeps the key it was stored under
                .jsonPath("$.ownerUsername").isEqualTo(owner);

        // The owner's trail now holds the moderator's edit as well as their own upload
        webTestClient.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, bearer(moderatorToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.status == 'UPDATED')]").exists();
    }

    @Test
    @DisplayName("an update with a blank name is refused by validation")
    void updateValidatesTheBody() {
        Integer fileId = uploadFile(registerUser(uniqueName("vasya")), "passport.pdf");

        webTestClient.put().uri("/api/v1/files/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAsAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new FileUpdateRequestDto("   ", FileStatus.ACTIVE))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a deleted file answers 404 while its bytes stay in the bucket")
    void deleteIsSoftAndKeepsTheObject() {
        String ownerToken = registerUser(uniqueName("vasya"));
        Integer fileId = uploadFile(ownerToken, "passport.pdf");

        String location = webTestClient.get().uri("/api/v1/files/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(FileResponseDto.class)
                .returnResult()
                .getResponseBody()
                .location();

        webTestClient.delete().uri("/api/v1/files/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAsAdmin()))
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri("/api/v1/files/" + fileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_NOT_FOUND");

        // Nothing is ever really deleted: the statistics have to remain replayable
        assertThat(objectExists(location)).isTrue();
    }

    @Test
    @DisplayName("an unknown id answers 404, exactly like a file owned by somebody else")
    void unknownIdIsNotFound() {
        String token = registerUser(uniqueName("vasya"));

        webTestClient.get().uri("/api/v1/files/999999")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_NOT_FOUND");
    }

    /** Asks MinIO directly, which is the only way to prove the bytes outlived the row. */
    private boolean objectExists(String key) {
        try {
            s3AsyncClient.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build()).join();
            return true;
        } catch (CompletionException e) {
            return false;
        }
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
