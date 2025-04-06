package org.acs.stuco.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.acs.stuco.backend.auth.JwtService;
import org.acs.stuco.backend.user.Role;
import org.acs.stuco.backend.user.User;
import org.acs.stuco.backend.user.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Component
public class JwtAuthFilter extends OncePerRequestFilter
{

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository)
    {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException
    {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer "))
        {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String userEmail = jwtService.extractEmail(jwt);

        // If we already have an authentication, skip
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null)
        {
            User user = userRepository.findByEmail(userEmail).orElse(null);

            if (user != null && jwtService.isTokenValid(jwt, user))
            {
                // Map the enum role to a Spring Security authority
                List<GrantedAuthority> authorities = mapRoleToAuthorities(user.getRole());

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);

                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    // Helper method that converts your enum Role -> GrantedAuthority
    private List<GrantedAuthority> mapRoleToAuthorities(Role role)
    {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // Because your SecurityConfig uses hasAnyRole("STUCO", "ADMIN", "REP"),
        // we need ROLE_ prefixes.
        switch (role)
        {
            case ADMIN -> authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            case STUCO -> authorities.add(new SimpleGrantedAuthority("ROLE_STUCO"));
            case CLASS_REP -> authorities.add(new SimpleGrantedAuthority("ROLE_REP"));
            default -> authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return authorities;
    }
}

