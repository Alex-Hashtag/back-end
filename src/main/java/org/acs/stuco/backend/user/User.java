package org.acs.stuco.backend.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "users")
@Getter
@Setter
public class User
{
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.ORDINAL)
    private Role role = Role.USER;

    private String avatarUrl;
    private boolean emailVerified = false;

    @Column(unique = true)
    private String verificationToken;
    
    @Column(unique = true)
    private String resetPasswordToken;
    
    private LocalDateTime resetPasswordTokenExpiry;

    @Column(precision = 10, scale = 2)
    private BigDecimal collectedBalance = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

