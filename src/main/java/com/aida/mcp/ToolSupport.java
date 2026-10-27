package com.aida.mcp;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.aida.security.TokenPrincipal;

import tools.jackson.databind.json.JsonMapper;

/**
 * The single path every tool goes through. FROZEN signature. Checks that the caller's token covers the project,
 * runs the body, writes one audit line and returns the result with a "meta" block. Tools never check access or
 * write audit lines themselves.
 */
@Component
public class ToolSupport {

    /** Thrown when the caller's token does not cover the project; MCP returns it as an error result. */
    public static class AccessDenied extends RuntimeException {
        public AccessDenied(String message) {
            super(message);
        }
    }

    private static final int SUMMARY_LIMIT = 300;

    private final JsonMapper mapper;
    private final AuditLog auditLog;

    public ToolSupport(JsonMapper mapper, AuditLog auditLog) {
        this.mapper = mapper;
        this.auditLog = auditLog;
    }

    /**
     * @param project the project the tool reads, or null for a tool that reads none
     * @param args    the tool's arguments, as they will appear in the audit log
     * @return the body's result as a JSON object plus a "meta" block {tool, calledAt, windowStart, windowEnd}
     * @throws AccessDenied when the caller's token does not cover {@code project}
     */
    public Map<String, Object> call(String tool, String project, Map<String, Object> args, Supplier<Object> body) {
        TokenPrincipal caller = currentPrincipal();
        String tokenName = caller == null ? "(none)" : caller.name();
        if (caller == null) {
            deny(tokenName, tool, args, "Access denied: no token");
        }
        if (project != null && !caller.canRead(project)) {
            deny(tokenName, tool, args,
                    "Access denied: token '" + caller.name() + "' cannot read project " + project.toUpperCase());
        }

        Instant calledAt = Instant.now();
        Map<String, Object> result;
        try {
            result = new LinkedHashMap<>(toMap(body.get()));
        } catch (RuntimeException e) {
            auditLog.record(tokenName, tool, args, "error: " + e.getMessage());
            throw e;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("tool", tool);
        meta.put("calledAt", calledAt.toString());
        meta.put("windowStart", result.get("windowStart"));
        meta.put("windowEnd", result.get("windowEnd"));
        result.put("meta", meta);
        auditLog.record(tokenName, tool, args, summary(result));
        if (!Guardrail.TOOL.equals(tool)) {
            // The guardrail's own output must never count as a source for later checks.
            auditLog.remember(tokenName, tool, result);
        }
        return result;
    }

    /** The token behind the current call, or null outside an authenticated request. */
    public static TokenPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof TokenPrincipal p ? p : null;
    }

    private void deny(String tokenName, String tool, Map<String, Object> args, String message) {
        auditLog.record(tokenName, tool, args, message);
        throw new AccessDenied(message);
    }

    /** The result's top-level scalar fields, e.g. "p85=2026-12-02, pByTarget=0.45", capped in length. */
    private static String summary(Map<String, Object> result) {
        String text = result.entrySet().stream()
                .filter(e -> !e.getKey().equals("meta") && !(e.getValue() instanceof Map) && !(e.getValue() instanceof List))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return text.length() <= SUMMARY_LIMIT ? text : text.substring(0, SUMMARY_LIMIT) + "...";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        Object tree = mapper.convertValue(value, Object.class);
        if (tree instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of("result", tree);
    }
}
