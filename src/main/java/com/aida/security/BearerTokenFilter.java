package com.aida.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads {@code Authorization: Bearer <token>}, looks it up by hash, and authenticates the request as its
 * {@link TokenPrincipal} with authorities {@code SCOPE_<scope>}. A missing or unknown token leaves the request
 * unauthenticated, and the security chain answers 401. Not a bean: {@link SecurityConfig} adds it to its chain only.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final TokenRegistry registry;

    public BearerTokenFilter(TokenRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            registry.find(header.substring(PREFIX.length()).trim()).ifPresent(principal -> {
                List<SimpleGrantedAuthority> authorities = principal.scopes().stream()
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                        .toList();
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
