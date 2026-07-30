package com.dezxxx.minios3.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }
        Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(header.substring(BEARER_PREFIX.length()));
            // Never log the token itself: it is a live credential

        } catch (JwtException e) {
            log.debug(e.getMessage());
            return chain.filter(exchange);
        }

        var authorities = List.of(new SimpleGrantedAuthority(ROLE_PREFIX + jwtTokenProvider
                .getRole(claims).name()));

        var authentication = new UsernamePasswordAuthenticationToken(
                jwtTokenProvider.getUsername(claims), null, authorities);
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }
}
