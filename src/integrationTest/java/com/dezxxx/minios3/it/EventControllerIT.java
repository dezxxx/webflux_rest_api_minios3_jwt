package com.dezxxx.minios3.it;

import com.dezxxx.minios3.dto.event.EventCreateRequestDto;
import com.dezxxx.minios3.dto.event.EventUpdateRequestDto;
import com.dezxxx.minios3.model.status.EventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

/**
 * The audit trail. Nothing here has to be called for it to fill up — uploading a file is
 * what writes the entries, and these tests mostly check who is allowed to read them back.
 */
@DisplayName("/api/v1/events")
class EventControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("uploading a file leaves a CREATED entry behind without anyone asking")
    void uploadWritesAnEvent() {
        String username = uniqueName("vasya");
        String token = registerUser(username);
        uploadFile(token, "passport.pdf");

        webTestClient.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].status").isEqualTo("CREATED")
                .jsonPath("$[0].username").isEqualTo(username)
                .jsonPath("$[0].fileName").isEqualTo("passport.pdf");
    }

    @Test
    @DisplayName("a USER sees only their own entries, never anybody else's")
    void userSeesOnlyTheirOwnTrail() {
        String first = uniqueName("vasya");
        String firstToken = registerUser(first);
        uploadFile(firstToken, "first.pdf");

        String second = uniqueName("kolya");
        String secondToken = registerUser(second);
        uploadFile(secondToken, "second.pdf");

        webTestClient.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, bearer(secondToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].username").isEqualTo(second)
                .jsonPath("$[0].fileName").isEqualTo("second.pdf");
    }

    @Test
    @DisplayName("a MODERATOR sees the whole trail, including other people's entries")
    void moderatorSeesEverything() {
        String owner = uniqueName("vasya");
        uploadFile(registerUser(owner), "owned.pdf");

        String moderatorToken = createModerator(uniqueName("moderator"));

        webTestClient.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, bearer(moderatorToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").value(length -> {
                    if (((Number) length).intValue() < 1) {
                        throw new AssertionError("a moderator must see entries made by others");
                    }
                });
    }

    @Test
    @DisplayName("an entry made by somebody else answers 404, not 403")
    void foreignEntryIsHidden() {
        String ownerToken = registerUser(uniqueName("vasya"));
        uploadFile(ownerToken, "owned.pdf");

        Integer eventId = firstEventId(ownerToken);

        String strangerToken = registerUser(uniqueName("kolya"));

        webTestClient.get().uri("/api/v1/events/" + eventId)
                .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken))
                .exchange()
                // A 403 would confirm the id exists and let the trail be mapped by counting
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("EVENT_NOT_FOUND");
    }

    @Test
    @DisplayName("a plain USER may not write an entry by hand")
    void createIsAdminOnly() {
        String token = registerUser(uniqueName("vasya"));
        Integer fileId = uploadFile(token, "passport.pdf");

        webTestClient.post().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new EventCreateRequestDto(fileId, EventStatus.UPDATED))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("an ADMIN writing an entry by hand is recorded under their own name")
    void manualEntryNamesTheAdmin() {
        String owner = uniqueName("vasya");
        Integer fileId = uploadFile(registerUser(owner), "passport.pdf");

        webTestClient.post().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAsAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new EventCreateRequestDto(fileId, EventStatus.UPDATED))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                // The body named no actor; the token did, and that is the only source
                .jsonPath("$.username").isEqualTo("admin")
                .jsonPath("$.status").isEqualTo("UPDATED")
                .jsonPath("$.fileName").isEqualTo("passport.pdf");
    }

    @Test
    @DisplayName("an entry about a file that does not exist is refused")
    void manualEntryNeedsARealFile() {
        webTestClient.post().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAsAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new EventCreateRequestDto(999999, EventStatus.CREATED))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FILE_NOT_FOUND");
    }

    @Test
    @DisplayName("a MODERATOR corrects the status of an entry and then hides it")
    void moderatorUpdatesAndDeletes() {
        String ownerToken = registerUser(uniqueName("vasya"));
        uploadFile(ownerToken, "passport.pdf");
        Integer eventId = firstEventId(ownerToken);

        String moderatorToken = createModerator(uniqueName("moderator"));

        webTestClient.put().uri("/api/v1/events/" + eventId)
                .header(HttpHeaders.AUTHORIZATION, bearer(moderatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new EventUpdateRequestDto(EventStatus.UPDATED))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UPDATED");

        webTestClient.delete().uri("/api/v1/events/" + eventId)
                .header(HttpHeaders.AUTHORIZATION, bearer(moderatorToken))
                .exchange()
                .expectStatus().isNoContent();

        // Soft delete: the row survives for statistics, it just stops being readable
        webTestClient.get().uri("/api/v1/events/" + eventId)
                .header(HttpHeaders.AUTHORIZATION, bearer(moderatorToken))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("EVENT_NOT_FOUND");
    }

    /** The id of the only entry a freshly registered account has: the one from its upload. */
    private Integer firstEventId(String token) {
        List<Map> events = webTestClient.get().uri("/api/v1/events")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .returnResult()
                .getResponseBody();

        return ((Number) events.get(0).get("id")).intValue();
    }
}
