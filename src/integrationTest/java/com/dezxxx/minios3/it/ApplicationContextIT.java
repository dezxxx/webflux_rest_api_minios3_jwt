package com.dezxxx.minios3.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The cheapest integration test there is: it only proves the bean graph assembles.
 *
 * <p>It lives here rather than in {@code test} for two reasons. It needs a reachable MySQL,
 * because R2DBC opens the pool at startup and Flyway migrates over JDBC. And it needs the
 * {@code dev} profile: outside it {@code InsecureDefaultsGuard} refuses to start on the
 * development secret, which is exactly what it was written to do.
 */

class ApplicationContextIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Spring context starts with every controller, service and repository wired")
    void contextLoads() {
        // given the full application configuration
        // when the context is built by @SpringBootTest
        // then it starts without a missing or duplicated bean
    }
}
