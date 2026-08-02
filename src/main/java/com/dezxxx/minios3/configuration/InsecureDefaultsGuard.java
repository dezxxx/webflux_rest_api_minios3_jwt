package com.dezxxx.minios3.configuration;

import com.dezxxx.minios3.exception.InsecureConfigurationException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start while any development credential is still in place.
 *
 * <p>Deliberately fail-closed: the check runs unless the {@code dev} profile is switched on
 * explicitly. Keying it the other way round — strict only when {@code prod} is named — would
 * mean that forgetting to set the profile leaves every default quietly in place, which is the
 * exact accident this class exists to prevent.
 *
 * <p>The JWT secret is the dangerous one. Anyone holding it can sign a token claiming any
 * user name and the ADMIN role; the signature verifies, so no password is ever checked.
 */
@Component
@Profile("!dev")
@RequiredArgsConstructor
@Slf4j
public class InsecureDefaultsGuard {

    private static final String DEV_JWT_SECRET = "change-me-in-production-at-least-32-bytes-long";
    private static final String DEV_DB_PASSWORD = "root";
    private static final String DEV_S3_KEY = "minioadmin";

    private final Environment environment;

    @PostConstruct
    void verify() {
        reject("app.jwt.secret", DEV_JWT_SECRET, "app.jwt.secret", "JWT_SECRET");
        reject("spring.r2dbc.password", DEV_DB_PASSWORD, "The database password", "DB_PASSWORD");
        reject("app.s3.secret-key", DEV_S3_KEY, "The S3 secret key", "S3_SECRET_KEY");

        log.info("No development defaults found in the active configuration");
    }

    private void reject(String property, String devValue, String setting, String variable) {
        if (devValue.equals(environment.getProperty(property))) {
            throw new InsecureConfigurationException(setting, variable);
        }
    }
}