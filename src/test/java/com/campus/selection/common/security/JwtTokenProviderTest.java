package com.campus.selection.common.security;

import com.campus.selection.common.config.JwtProperties;
import com.campus.selection.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(
            "test-secret-key-with-at-least-thirty-two-bytes-0123456789",
            "campus-test",
            3600,
            7200
    ));

    private final LoginUser user = new LoginUser(42L, "20260001", "刘同学", Role.STUDENT);

    @Test
    void accessTokenRoundTrip() {
        String token = provider.createAccessToken(user);
        Claims claims = provider.parseAccessToken(token);
        LoginUser parsed = provider.toLoginUser(claims);

        assertEquals(42L, parsed.id());
        assertEquals("20260001", parsed.studentNo());
        assertEquals("刘同学", parsed.name());
        assertEquals(Role.STUDENT, parsed.role());
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String refreshToken = provider.createRefreshToken(user);
        assertThrows(JwtException.class, () -> provider.parseAccessToken(refreshToken));
    }
}
