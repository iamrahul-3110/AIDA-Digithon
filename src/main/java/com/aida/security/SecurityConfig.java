package com.aida.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP security. M1: the mock routes are open under the "mock" profile and everything else is denied.
 * M4 (A3) adds the bearer-token chain for /mcp and /admin.
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
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
