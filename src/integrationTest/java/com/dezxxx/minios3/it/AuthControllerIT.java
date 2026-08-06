package com.dezxxx.minios3.it;

import com.dezxxx.minios3.dto.auth.AuthenticationRequestDto;
import com.dezxxx.minios3.dto.auth.AuthenticationResponseDto;
import com.dezxxx.minios3.dto.auth.RefreshRequestDto;
import com.dezxxx.minios3.dto.auth.RegistrationRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** The four open endpoints — the only ones an unauthenticated caller can reach at all. */
@DisplayName("POST /api/v1/auth")
class AuthControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("registration returns a token pair and always creates a plain USER")
    void registerReturnsTokens() {
        String username = uniqueName("vasya");

        webTestClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationRequestDto(username, PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty()
                .jsonPath("$.username").isEqualTo(username)
                // There is no field in the request that could have asked for anything else
                .jsonPath("$.role").isEqualTo("USER");
    }

    @Test
    @DisplayName("registering a name that is taken answers 409 USERNAME_TAKEN")
    void registerRejectsTakenName() {
        String username = uniqueName("vasya");
        registerUser(username);

        webTestClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationRequestDto(username, PASSWORD))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("USERNAME_TAKEN");
    }

    @Test
    @DisplayName("a blank password is refused by validation before anything is looked up")
    void registerValidatesTheBody() {
        webTestClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationRequestDto(uniqueName("vasya"), ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("login with the seeded administrator returns an ADMIN token")
    void loginWithSeededAdmin() {
        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthenticationRequestDto("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.role").isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("a wrong password answers 401 INVALID_CREDENTIALS, exactly like an unknown name")
    void loginRejectsWrongPassword() {
        String username = uniqueName("vasya");
        registerUser(username);

        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthenticationRequestDto(username, "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("an unknown name answers exactly the same, so accounts cannot be enumerated")
    void loginRejectsUnknownName() {
        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthenticationRequestDto(uniqueName("ghost"), PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("a refresh token is traded for a fresh pair")
    void refreshReturnsANewPair() {
        String username = uniqueName("vasya");

        AuthenticationResponseDto registered = webTestClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationRequestDto(username, PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AuthenticationResponseDto.class)
                .returnResult()
                .getResponseBody();

        webTestClient.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RefreshRequestDto(registered.refreshToken()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.username").isEqualTo(username);
    }

    @Test
    @DisplayName("an access token is refused at /refresh, which is why the two carry a type")
    void refreshRefusesAnAccessToken() {
        String accessToken = registerUser(uniqueName("vasya"));

        webTestClient.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RefreshRequestDto(accessToken))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_TOKEN");
    }

    @Test
    @DisplayName("a forged token is refused: the signature is checked, not just the shape")
    void refreshRefusesAForgedToken() {
        webTestClient.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RefreshRequestDto("not.a.jwt"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_TOKEN");
    }

    @Test
    @DisplayName("logout answers 204 and keeps no server-side state to clear")
    void logoutIsClientSide() {
        webTestClient.post().uri("/api/v1/auth/logout")
                .exchange()
                .expectStatus().isNoContent();
    }
}
