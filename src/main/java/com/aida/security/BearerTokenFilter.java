package com.aida.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BearerTokenFilter extends OncePerRequestFilter {

    private final TokenRegistry tokenRegistry;
    private final ObjectMapper objectMapper;

    public BearerTokenFilter(TokenRegistry tokenRegistry, ObjectMapper objectMapper) {
        this.tokenRegistry = tokenRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        } else if (request.getParameter("token") != null && !request.getParameter("token").isBlank()) {
            token = request.getParameter("token").trim();
        } else if (request.getParameter("apiKey") != null && !request.getParameter("apiKey").isBlank()) {
            token = request.getParameter("apiKey").trim();
        }

        if (token != null) {
            var principal = tokenRegistry.authenticate(token);
            if (principal.isPresent()) {
                TokenPrincipal tp = principal.get();
                CurrentPrincipalHolder.set(tp);
                List<SimpleGrantedAuthority> authorities = tp.scopes().stream()
                    .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                    .collect(Collectors.toList());
                var auth = new UsernamePasswordAuthenticationToken(tp, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                sendUnauthorized(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", "unauthorized")));
    }
}
