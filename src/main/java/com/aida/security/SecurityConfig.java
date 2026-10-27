package com.aida.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import jakarta.servlet.DispatcherType;

/**
 * HTTP security, all stateless:
 * <ul>
 * <li>{@code /mcp/**} needs a bearer token with scope mcp; {@code /admin/**} needs scope admin.</li>
 * <li>{@code /mock/**} and {@code /actuator/health} are open, under the "mock" profile only.</li>
 * <li>Everything else is denied.</li>
 * </ul>
 * A missing or invalid token gets 401 {"error":"unauthorized"}; a valid token without the scope gets 403.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** Demo-only routes: mock Jira, mock calendar, the leave console and health. Never active outside "mock". */
    @Bean
    @Order(1)
    @Profile("mock")
    SecurityFilterChain mockChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/mock/**", "/actuator/health")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain tokenChain(HttpSecurity http, TokenRegistry registry) throws Exception {
        return http
                .securityMatcher("/mcp", "/mcp/**", "/admin/**")
                .addFilterBefore(new BearerTokenFilter(registry), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Async (streaming) and error dispatches follow a request that was already authorized.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/mcp", "/mcp/**").hasAuthority("SCOPE_mcp")
                        .requestMatchers("/admin/**").hasAuthority("SCOPE_admin")
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized()).accessDeniedHandler(forbidden()))
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain denyChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().denyAll())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized()).accessDeniedHandler(forbidden()))
                .build();
    }

    static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> {
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"unauthorized\"}");
        };
    }

    static AccessDeniedHandler forbidden() {
        return (request, response, ex) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"forbidden\"}");
        };
    }
}
