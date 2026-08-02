package com.dezxxx.minios3.security;


import com.dezxxx.minios3.configuration.JwtProperties;
import com.dezxxx.minios3.model.status.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
@Slf4j

public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    // Both tokens are signed with the same key, so only this claim tells them apart.
    // Without it a 30-day refresh token would pass as a Bearer on every endpoint.
    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Short-lived token presented on every request; carries the role. */
    public String createAccessToken(String username, Role role) {
        return build(username, properties.accessTokenTtl())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .claim(ROLE_CLAIM, role.name())
                .compact();
    }

    /**
     * Long-lived token accepted only by /auth/refresh.
     * Deliberately carries no role: the point of refreshing is that the role
     * is read from the database again, so a stale copy here would defeat it.
     */
    public String createRefreshToken(String username) {
        return build(username, properties.refreshTokenTtl())
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .compact();
    }

    /** Verifies signature and expiry, then returns the payload. */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return ACCESS_TYPE.equals(getType(claims));
    }

    public boolean isRefreshToken(Claims claims) {
        return REFRESH_TYPE.equals(getType(claims));
    }

    public String getUsername(Claims claims) {
        return claims.getSubject();
    }

    public Role getRole(Claims claims) {
        return Role.valueOf(claims.get(ROLE_CLAIM, String.class));
    }

    /** Everything both tokens share; the caller adds the type and signs. */
    private JwtBuilder build(String username, Duration ttl) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(username)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key);
    }

    private String getType(Claims claims) {
        return claims.get(TYPE_CLAIM, String.class);
    }
}