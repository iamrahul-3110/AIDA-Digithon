package com.aida.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenRegistry tokenRegistry;
    private final ObjectMapper objectMapper;

    public SecurityConfig(TokenRegistry tokenRegistry, ObjectMapper objectMapper) {
        this.tokenRegistry = tokenRegistry;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/chat.html", "/index.html", "/static/**", "/error").permitAll()
                .requestMatchers("/api/chat", "/api/chat/**").permitAll()
                .requestMatchers("/mcp/tools/**").permitAll()
                .requestMatchers("/mock/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/mcp/**", "/mcp", "/sse/**", "/sse").hasAuthority("SCOPE_mcp")
                .requestMatchers("/admin/**", "/admin").hasAuthority("SCOPE_admin")
                .anyRequest().denyAll()
            )
            .addFilterBefore(new BearerTokenFilter(tokenRegistry, objectMapper),
                    UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"unauthorized\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"forbidden\"}");
                })
            );
        return http.build();
    }
}
