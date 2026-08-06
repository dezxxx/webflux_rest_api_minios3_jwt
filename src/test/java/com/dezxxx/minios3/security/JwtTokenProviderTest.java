package com.dezxxx.minios3.security;

import com.dezxxx.minios3.configuration.JwtProperties;
import com.dezxxx.minios3.model.status.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No Spring and no mocks: the class takes its settings through the constructor, so a test
 * can hand it a different secret or a negative lifetime and see what it does.
 *
 * <p>This is the most security-sensitive class in the application. Anyone able to forge a
 * token it accepts is an administrator, without ever knowing a password.
 */
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";
    private static final String USERNAME = "vasya";

    private final JwtTokenProvider provider = providerWith(SECRET, Duration.ofMinutes(15));

    @Test
    @DisplayName("an access token carries the user name and the role")
    void accessTokenCarriesNameAndRole() {
        // given
        String token = provider.createAccessToken(USERNAME, Role.MODERATOR);

        // when
        Claims claims = provider.parseToken(token);

        // then
        assertThat(provider.getUsername(claims)).isEqualTo(USERNAME);
        assertThat(provider.getRole(claims)).isEqualTo(Role.MODERATOR);
        assertThat(provider.isAccessToken(claims)).isTrue();
        assertThat(provider.isRefreshToken(claims)).isFalse();
    }

    @Test
    @DisplayName("a refresh token carries no role, so a stale one cannot be trusted")
    void refreshTokenCarriesNoRole() {
        // given
        String token = provider.createRefreshToken(USERNAME);

        // when
        Claims claims = provider.parseToken(token);

        // then
        assertThat(provider.getUsername(claims)).isEqualTo(USERNAME);
        assertThat(provider.isRefreshToken(claims)).isTrue();
        assertThat(provider.isAccessToken(claims)).isFalse();
        // The point of refreshing is that the role is read from the database again
        assertThat(claims.get("role")).isNull();
    }

    @Test
    @DisplayName("the two kinds are told apart by a claim, not by their shape")
    void theTwoKindsAreDistinguishable() {
        // given
        Claims access = provider.parseToken(provider.createAccessToken(USERNAME, Role.USER));
        Claims refresh = provider.parseToken(provider.createRefreshToken(USERNAME));

        // then: both are signed with the same key, so without this claim a 30-day refresh
        // token would pass as a Bearer on every endpoint
        assertThat(provider.isAccessToken(access)).isTrue();
        assertThat(provider.isAccessToken(refresh)).isFalse();
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void aTokenFromAnotherSecretIsRejected() {
        // given: same class, same claims, different key — this is what forgery looks like
        JwtTokenProvider attacker =
                providerWith("some-other-secret-that-is-also-32-bytes", Duration.ofMinutes(15));
        String forged = attacker.createAccessToken("admin", Role.ADMIN);

        // when + then
        assertThatThrownBy(() -> provider.parseToken(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a tampered token is rejected: the signature covers the payload")
    void aTamperedTokenIsRejected() {
        // given: flip the role inside the payload of a genuine token
        String token = provider.createAccessToken(USERNAME, Role.USER);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA."
                + parts[2];

        // when + then
        assertThatThrownBy(() -> provider.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("an expired token is rejected with ExpiredJwtException, not silently accepted")
    void anExpiredTokenIsRejected() {
        // given: a negative lifetime produces a token that was already dead when issued
        JwtTokenProvider shortLived = providerWith(SECRET, Duration.ofSeconds(-60));
        String expired = shortLived.createAccessToken(USERNAME, Role.USER);

        // when + then: AuthService turns this into "log in again", unlike a forgery
        assertThatThrownBy(() -> provider.parseToken(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("a string that is not a JWT at all is rejected")
    void garbageIsRejected() {
        assertThatThrownBy(() -> provider.parseToken("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    private static JwtTokenProvider providerWith(String secret, Duration accessTtl) {
        return new JwtTokenProvider(
                new JwtProperties(secret, accessTtl, Duration.ofDays(30), "minios3"));
    }
}
