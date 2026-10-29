package com.aida.security;

import java.util.List;
import java.util.Set;

public record TokenPrincipal(String name, Set<String> projects, Set<String> scopes) {
    public boolean canReadProject(String project) {
        return projects.contains(project);
    }
}
