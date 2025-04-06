package org.acs.stuco.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.acs.stuco.backend.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;


@Service
public class JwtService
{

    private final long expiration = 1000 * 60 * 60 * 24;
    @Value("${jwt.secret}")
    private String secret;

    private Key getSigningKey()
    {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(User user)
    {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("role", user.getRole().ordinal())
                .claim("name", user.getName())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token)
    {
        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, User user)
    {
        return extractEmail(token).equals(user.getEmail()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token)
    {
        return getClaims(token).getExpiration().before(new Date());
    }

    private Claims getClaims(String token)
    {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

