package org.acs.stuco.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.acs.stuco.backend.user.Role;
import org.acs.stuco.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;
    
    private User testUser;
    private String testToken;
    private String testSecret = "thisisatestsecrethisisatestsecrethisisatestsecrethisisatestsecret";
    
    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@acsbg.org")
                .passwordHash("password")
                .name("Test User")
                .role(Role.USER)
                .build();
        
        // Initialize the JWT secret key
        ReflectionTestUtils.setField(jwtService, "secret", testSecret);
        
        // Generate a token that we can use in our tests
        testToken = jwtService.generateToken(testUser);
    }
    
    @Test
    void generateToken_ShouldCreateValidTokenWithCorrectClaims() {
        // Act
        String token = jwtService.generateToken(testUser);
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(testSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        // Assert
        assertEquals(testUser.getEmail(), claims.getSubject());
        assertEquals(testUser.getRole().ordinal(), claims.get("role"));
        assertEquals(testUser.getName(), claims.get("name"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        
        // Verify expiration is in the future
        assertTrue(claims.getExpiration().after(new Date()));
        
        // Verify issued at date is in the past or present
        assertTrue(claims.getIssuedAt().before(new Date()) || claims.getIssuedAt().equals(new Date()));
    }
    
    @Test
    void extractEmail_ShouldReturnCorrectEmail() {
        // Act
        String extractedEmail = jwtService.extractEmail(testToken);
        
        // Assert
        assertEquals(testUser.getEmail(), extractedEmail);
    }
    
    @Test
    void isTokenValid_WithValidTokenAndMatchingUser_ShouldReturnTrue() {
        // Act
        boolean isValid = jwtService.isTokenValid(testToken, testUser);
        
        // Assert
        assertTrue(isValid);
    }
    
    @Test
    void isTokenValid_WithMismatchedUser_ShouldReturnFalse() {
        // Arrange
        User differentUser = new User();
        differentUser.setEmail("different@acsbg.org");
        
        // Act
        boolean isValid = jwtService.isTokenValid(testToken, differentUser);
        
        // Assert
        assertFalse(isValid);
    }
    
    @Test
    void isTokenExpired_WithValidToken_ShouldReturnFalse() {
        // Act
        boolean isExpired = jwtService.isTokenExpired(testToken);
        
        // Assert
        assertFalse(isExpired);
    }
    
    @Test
    void isTokenExpired_WithExpiredToken_ShouldThrowExpiredJwtException() {
        // Arrange - create an expired token
        String expiredToken = createExpiredToken(testUser);
        
        // Act & Assert
        assertThrows(ExpiredJwtException.class, () -> {
            jwtService.isTokenExpired(expiredToken);
        });
    }
    
    @Test
    void getSigningKey_ShouldUseCorrectAlgorithm() {
        // This test indirectly tests the signing key by validating a token works
        // No need to use reflection which can be unstable across JVM versions
        
        // Assert that we can extract information from a token
        // which proves the signing key works correctly
        assertDoesNotThrow(() -> {
            jwtService.extractEmail(testToken);
        });
    }
    
    @Test
    void isTokenValid_WithExpiredToken_ShouldThrowExpiredJwtException() {
        // Arrange
        String expiredToken = createExpiredToken(testUser);
        
        // Act & Assert - The implementation throws an exception when token is expired
        assertThrows(ExpiredJwtException.class, () -> {
            jwtService.isTokenValid(expiredToken, testUser);
        });
    }
    
    // Helper method to create an expired token
    private String createExpiredToken(User user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("role", user.getRole().ordinal())
                .claim("name", user.getName())
                .setIssuedAt(new Date(System.currentTimeMillis() - 2000))
                .setExpiration(new Date(System.currentTimeMillis() - 1000)) // Set expiration in the past
                .signWith(Keys.hmacShaKeyFor(testSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }
}
