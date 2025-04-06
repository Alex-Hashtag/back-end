package org.acs.stuco.backend.user;

// UserUpdateRequest.java
public record UserUpdateRequest(
        String fullName,
        String email,
        String password
)
{
}