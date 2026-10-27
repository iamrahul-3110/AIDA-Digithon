package com.aida.security;

import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Plain values of the test tokens in {@code src/test/resources/tokens-test.yml}, and helpers for tool tests
 * (Person B's tool tests can use these too).
 */
public final class TestTokens {

    /** SHOP and OPS; scopes mcp and admin. */
    public static final String FULL = "test-token-full";
    /** OPS only; scope mcp. */
    public static final String OPS_ONLY = "test-token-ops";
    /** SHOP; scope admin only (no mcp). */
    public static final String ADMIN_ONLY = "test-token-admin";

    private TestTokens() {
    }

    /** An authentication as the filter would create it, for calling tools directly in tests. */
    public static Authentication authentication(String name, Set<String> projects, Set<String> scopes) {
        List<SimpleGrantedAuthority> authorities = scopes.stream().map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(new TokenPrincipal(name, projects, scopes), null,
                authorities);
    }
}
