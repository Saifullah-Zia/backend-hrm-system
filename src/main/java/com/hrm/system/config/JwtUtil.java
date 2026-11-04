package com.hrm.system.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/** Signs and parses login tokens. The signing key only ever comes from the JWT_SECRET env var. */
@Component
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;

    private final Key signingKey;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration:86400000}") long expirationMs) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " characters long.");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, String role, Long userId, String email) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .claim("userId", userId)
                .claim("email", email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Verifies signature and expiry; throws a JwtException when the token is not valid. */
    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** Reads the numeric userId claim, or null when it is missing or malformed. */
    public static Long userIdOf(Claims claims) {
        Object userId = claims.get("userId");
        if (userId == null) return null;
        try {
            return Long.valueOf(userId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
