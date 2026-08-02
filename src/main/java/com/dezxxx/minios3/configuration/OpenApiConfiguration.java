package com.dezxxx.minios3.configuration;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;


@Configuration
@OpenAPIDefinition (info = @Info(title = "MinIO S3 File Storage API",
                    version = "1.0.0",
description = """
        Reactive REST API for file storage in MinIO S3 with JWT authentication.

        **Getting started.** Call `POST /api/v1/auth/login` with `admin` / `admin`, copy the \
        `accessToken` out of the response, press **Authorize** above and paste it — without the \
        word `Bearer`, it is added for you.

        **Tokens.** The access token lives 15 minutes; when it expires, post the refresh token to \
        `/api/v1/auth/refresh` for a new pair. Neither token is stored on the server, so there is \
        no revocation: `POST /api/v1/auth/logout` returns 204 and does nothing, and a stolen token \
        works until it expires. A changed role or a blocked account takes effect at the next \
        refresh, not immediately.

        **Errors.** Every failure returns the same body — `timestamp`, `status`, `error`, `code`, \
        `message`. Branch on `code`, not on the prose: it stays stable when the wording changes. \
        The full set is `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `EXPIRED_TOKEN`, \
        `INVALID_TOKEN`, `USER_BLOCKED`, `ACCESS_DENIED`, `USER_NOT_FOUND`, `USERNAME_TAKEN`, \
        `RESOURCE_CONFLICT`, `REQUEST_FAILED`, `INTERNAL_ERROR`. A `401` with no body at all \
        means no token was sent.

        **Deletion is soft** everywhere: the row keeps its place and only `deleted_at` is set, \
        because the data is retained for later analysis. Deleted rows are invisible to every \
        read and answer 404.
        """))

@SecurityScheme(name = "bearerAuth",
                type = SecuritySchemeType.HTTP,
                scheme = "bearer",
                bearerFormat = "JWT",
                description = "Paste the accessToken from POST /api/v1/auth/login. Without the 'Bearer ' prefix.")

public class OpenApiConfiguration {
}
