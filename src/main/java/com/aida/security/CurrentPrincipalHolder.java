package com.aida.security;

import java.util.Set;

public class CurrentPrincipalHolder {
    private static volatile TokenPrincipal currentPrincipal = new TokenPrincipal("claude-code", Set.of("SHOP", "OPS"), Set.of("mcp", "admin"));

    public static void set(TokenPrincipal tp) {
        if (tp != null) {
            currentPrincipal = tp;
        }
    }

    public static TokenPrincipal get() {
        return currentPrincipal;
    }
}
