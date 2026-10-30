package com.campus.selection.common.security;

import com.campus.selection.common.config.JwtProperties;
import com.campus.selection.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(LoginUser user) {
        return createToken(user, TYPE_ACCESS, properties.accessTokenTtlSeconds());
    }

    public String createRefreshToken(LoginUser user) {
        return createToken(user, TYPE_REFRESH, properties.refreshTokenTtlSeconds());
    }

    public Claims parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public Claims parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    public LoginUser toLoginUser(Claims claims) {
        Object rawId = claims.get("uid");
        Long id = rawId instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(rawId));
        String role = claims.get("role", String.class);
        return new LoginUser(
                id,
                claims.getSubject(),
                claims.get("name", String.class),
                Role.valueOf(role)
        );
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtlSeconds();
    }

    private String createToken(LoginUser user, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.studentNo())
                .issuer(properties.issuer())
                .claim("uid", user.id())
                .claim("name", user.name())
                .claim("role", user.role().name())
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    private Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String type = claims.get("type", String.class);
        if (!expectedType.equals(type)) {
            throw new JwtException("Unexpected token type");
        }
        return claims;
    }
}
