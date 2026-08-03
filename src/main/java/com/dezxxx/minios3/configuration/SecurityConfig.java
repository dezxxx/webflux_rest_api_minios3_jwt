package com.dezxxx.minios3.configuration;


import com.dezxxx.minios3.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor

public class SecurityConfig {

    /** Listed one by one: a wildcard would also publish whatever is added here later. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    };

    /** Open under the dev profile only: the document maps out the whole API. */
    private static final String[] SWAGGER_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    private static final String DEV_PROFILE = "dev";


    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final Environment environment;


    @Bean
    public SecurityWebFilterChain springWebFilterChain(ServerHttpSecurity http) {
        return
                http
// Nothing is kept server side, so there is no session to forge a request against
                        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        // Both answer with their own login prompt instead of a plain 401
                        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                        .authorizeExchange(exchange -> exchange
                                .pathMatchers(publicEndpoints()).permitAll()
                                .anyExchange().authenticated())
                        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                        .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                        .build();
    }

    /** The authentication paths always, the API documentation only while developing. */
    private String[] publicEndpoints() {
        if (!environment.matchesProfiles(DEV_PROFILE)) {
            return PUBLIC_ENDPOINTS;
        }
        return Stream.concat(Arrays.stream(PUBLIC_ENDPOINTS), Arrays.stream(SWAGGER_ENDPOINTS))
                .toArray(String[]::new);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}