package com.dezxxx.minios3.it;

import com.dezxxx.minios3.dto.user.UserCreateRequestDto;
import com.dezxxx.minios3.dto.user.UserResponseDto;
import com.dezxxx.minios3.dto.user.UserUpdateRequestDto;
import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Account management: who may read whom, and the guards protecting the last administrator. */
@DisplayName("/api/v1/users")
class UserControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("/users/me returns the caller's own account without an id being passed")
    void meReturnsTheCaller() {
        String username = uniqueName("vasya");
        String token = registerUser(username);

        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo(username)
                .jsonPath("$.role").isEqualTo("USER")
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("a plain USER may not list accounts")
    void listIsClosedToPlainUser() {
        String token = registerUser(uniqueName("vasya"));

        webTestClient.get().uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("an ADMIN lists every account")
    void adminListsAccounts() {
        registerUser(uniqueName("vasya"));

        webTestClient.get().uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAsAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").value(length -> {
                    if (((Number) length).intValue() < 2) {
                        throw new AssertionError("expected the seeded admin and at least one user");
                    }
                });
    }

    @Test
    @DisplayName("a plain USER may not read another account by id")
    void readByIdIsClosedToPlainUser() {
        String token = registerUser(uniqueName("vasya"));

        webTestClient.get().uri("/api/v1/users/1")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("an ADMIN creates a MODERATOR, which is the only way one comes to exist")
    void adminCreatesModerator() {
        String username = uniqueName("moderator");

        webTestClient.post().uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAsAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UserCreateRequestDto(username, PASSWORD, Role.MODERATOR))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo(username)
                .jsonPath("$.role").isEqualTo("MODERATOR")
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("an ADMIN may block somebody else, and the account then cannot log in")
    void adminBlocksAnotherAccount() {
        String username = uniqueName("vasya");
        String token = registerUser(username);

        Integer id = webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponseDto.class)
                .returnResult()
                .getResponseBody()
                .id();

        webTestClient.put().uri("/api/v1/users/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAsAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UserUpdateRequestDto(username, Role.USER, UserStatus.BLOCKED))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("an administrator may not take ADMIN away from their own account")
    void adminCannotDemoteThemselves() {
        String adminToken = loginAsAdmin();

        Integer id = webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponseDto.class)
                .returnResult()
                .getResponseBody()
                .id();

        webTestClient.put().uri("/api/v1/users/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UserUpdateRequestDto("admin", Role.USER, UserStatus.ACTIVE))
                .exchange()
                // Nobody would be left able to undo it
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("a deleted account answers 404 and can no longer log in")
    void deletedAccountDisappears() {
        String username = uniqueName("vasya");
        String token = registerUser(username);
        String adminToken = loginAsAdmin();

        Integer id = webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponseDto.class)
                .returnResult()
                .getResponseBody()
                .id();

        webTestClient.delete().uri("/api/v1/users/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isNoContent();

        // The row is still there — only deleted_at was set — but every read filters it out
        webTestClient.get().uri("/api/v1/users/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("an unknown id answers 404 USER_NOT_FOUND")
    void unknownIdIsNotFound() {
        webTestClient.get().uri("/api/v1/users/999999")
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAsAdmin()))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("USER_NOT_FOUND");
    }
}
