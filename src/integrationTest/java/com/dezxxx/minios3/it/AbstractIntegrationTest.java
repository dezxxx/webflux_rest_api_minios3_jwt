package com.dezxxx.minios3.it;

import com.dezxxx.minios3.configuration.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Duration;
import java.util.concurrent.CompletionException;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")

public abstract class AbstractIntegrationTest {
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
