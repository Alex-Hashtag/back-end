package org.acs.stuco.backend.user;

public record UserUpdateRequest(
        String fullName,
        String email,
        String password
)
{
}

