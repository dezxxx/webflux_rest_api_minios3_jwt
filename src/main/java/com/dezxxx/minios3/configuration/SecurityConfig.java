package com.dezxxx.minios3.configuration;


import com.dezxxx.minios3.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor

public class SecurityConfig {
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };


    private final JwtAuthenticationFilter jwtAuthenticationFilter;


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
                                .pathMatchers(PUBLIC_ENDPOINTS).permitAll()
                                .anyExchange().authenticated())
                        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                        .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                        .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}