package org.acs.stuco.backend.auth.dto;

public record RegisterRequest(String email, String name, String password)
{
}
