package com.baiflow.auth.security;

import com.baiflow.auth.config.BaiflowProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final BaiflowProperties baiflowProperties;

    public JwtService(BaiflowProperties baiflowProperties) {
        this.baiflowProperties = baiflowProperties;
    }

    public String generateToken(String userId, String username, String role) {
        long now = System.currentTimeMillis();
        long expireMs = baiflowProperties.getJwt().getExpireSeconds() * 1000L;
        SecretKey key = getKey();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMs))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        SecretKey key = getKey();
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token) {
        try { parseToken(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    private SecretKey getKey() {
        String secret = baiflowProperties.getJwt().getSecret();
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
