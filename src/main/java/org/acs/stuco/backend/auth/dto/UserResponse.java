package org.acs.stuco.backend.auth.dto;

public record UserResponse(
        Long id,
        String name,
        String email,
        String avatarUrl,
        int role,
        boolean emailVerified
)
{
}


