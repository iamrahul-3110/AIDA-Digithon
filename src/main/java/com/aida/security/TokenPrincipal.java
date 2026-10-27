package com.aida.security;

import java.util.Set;

/**
 * Who is calling: the token's name (never the token itself), the projects it may read and its scopes.
 *
 * @param scopes mcp, admin
 */
public record TokenPrincipal(String name, Set<String> projects, Set<String> scopes) {

    public TokenPrincipal {
        projects = Set.copyOf(projects);
        scopes = Set.copyOf(scopes);
    }

    public boolean canRead(String project) {
        return project != null && projects.contains(project.toUpperCase());
    }

    @Override
    public String toString() {
        return name;
    }
}
