package com.dezxxx.minios3.it;

import com.dezxxx.minios3.configuration.S3Properties;
import com.dezxxx.minios3.dto.auth.AuthenticationRequestDto;
import com.dezxxx.minios3.dto.auth.AuthenticationResponseDto;
import com.dezxxx.minios3.dto.auth.RegistrationRequestDto;
import com.dezxxx.minios3.dto.file.FileResponseDto;
import com.dezxxx.minios3.dto.user.UserCreateRequestDto;
import com.dezxxx.minios3.model.status.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MySQLContainer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")

public abstract class AbstractIntegrationTest {

    /** Long enough for the bcrypt constraint on the DTOs; the value itself is irrelevant. */
    protected static final String PASSWORD = "secret123";

    @Autowired
    protected S3AsyncClient s3AsyncClient;

    @Autowired
    protected S3Properties s3Properties;

    // Spring Boot 4 no longer publishes a WebTestClient bound to the running server, so
    // it is built by hand from the port the application actually got.
    @LocalServerPort
    private int port;

    protected WebTestClient webTestClient;

    @BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                // The default is five seconds; a first upload into a cold MinIO can exceed it
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    @BeforeEach
    void createBucket() {
        try {

            s3AsyncClient.createBucket(CreateBucketRequest.builder()
                    .bucket(s3Properties.bucket())
                    .build()).join();
        } catch (CompletionException e) {
            // Second and later tests find it already there, and that is fine
        }
    }


    /**
     * Registers a fresh account and returns its access token. Registration always produces
     * a plain USER, which is exactly what most tests want to be.
     */
    protected String registerUser(String username) {
        return tokenOf(webTestClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationRequestDto(username, PASSWORD))
                .exchange()
                .expectStatus().isCreated());
    }

    /** The seeded administrator from V2__seed_admin.sql — the only way to reach ADMIN paths. */
    protected String loginAsAdmin() {
        return tokenOf(webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthenticationRequestDto("admin", "admin"))
                .exchange()
                .expectStatus().isOk());
    }

    /**
     * A moderator has to be made by an administrator: registration cannot grant a role, and
     * that restriction is the whole point of the endpoint.
     */
    protected String createModerator(String username) {
        webTestClient.post().uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(loginAsAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UserCreateRequestDto(username, PASSWORD, Role.MODERATOR))
                .exchange()
                .expectStatus().isCreated();

        return tokenOf(webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthenticationRequestDto(username, PASSWORD))
                .exchange()
                .expectStatus().isOk());
    }

    /** Uploads a small file on behalf of the token holder and returns its id. */
    protected Integer uploadFile(String token, String filename) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)))
                .filename(filename)
                .contentType(MediaType.APPLICATION_PDF);

        return webTestClient.post().uri("/api/v1/files")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FileResponseDto.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /** Names have to be unique: the database outlives every test in the run. */
    protected String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String tokenOf(WebTestClient.ResponseSpec response) {
        return response
                .expectBody(AuthenticationResponseDto.class)
                .returnResult()
                .getResponseBody()
                .accessToken();
    }

    /*
     * Started by hand rather than with @Container on purpose. That annotation stops a
     * static container once its own class is done, and these are shared by every subclass:
     * the second suite would find a dead port. Nothing stops them here — Testcontainers'
     * own reaper removes them when the JVM exits.
     */
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest");

    static {
        MYSQL.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry
                                   registry) {
        registry.add("app.s3.endpoint", MINIO::getS3URL);
        registry.add("app.s3.access-key",
                MINIO::getUserName);
        registry.add("app.s3.secret-key",
                MINIO::getPassword);
        registry.add("spring.flyway.url",
                MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user",
                MYSQL::getUsername);
        registry.add("spring.flyway.password",
                MYSQL::getPassword);
    }
}
