package com.dezxxx.minios3.security;


import com.dezxxx.minios3.configuration.JwtProperties;
import com.dezxxx.minios3.model.status.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
@Slf4j

public class JwtTokenProvider {

  private static final String ROLE_CLAIM = "role";
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Issues a signed token carrying the username and the role. */
    public String createToken(String username, Role role) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM,role.name())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }


    /** Verifies signature and expiry, then returns the payload. */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token).getPayload();
    }

    public String getUsername(Claims claims) {
        return claims.getSubject();
    }

    public Role getRole(Claims claims) {
        return Role.valueOf(claims.get(ROLE_CLAIM, String.class));
    }


}
