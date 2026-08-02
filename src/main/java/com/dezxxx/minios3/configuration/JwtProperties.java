package com.dezxxx.minios3.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")

public record JwtProperties(String secret,
                            Duration accessTokenTtl,
                            Duration refreshTokenTtl,
                            String issuer) {

}
