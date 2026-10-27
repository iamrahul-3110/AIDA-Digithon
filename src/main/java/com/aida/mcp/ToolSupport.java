package com.aida.mcp;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * The single path every tool goes through. FROZEN signature.
 *
 * <p>M0 stub: runs the body and adds the meta block. M4 (Person A) adds the project access check
 * against the caller's token and the audit line; tools must never do either themselves.
 */
@Component
public class ToolSupport {

    private final JsonMapper mapper;

    public ToolSupport(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param args the tool's arguments, as they will appear in the audit log
     * @return the body's result as a JSON object plus a "meta" block {tool, calledAt, windowStart, windowEnd}
     */
    public Map<String, Object> call(String tool, String project, Map<String, Object> args, Supplier<Object> body) {
        Instant calledAt = Instant.now();
        Map<String, Object> result = new LinkedHashMap<>(toMap(body.get()));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("tool", tool);
        meta.put("calledAt", calledAt.toString());
        meta.put("windowStart", result.get("windowStart"));
        meta.put("windowEnd", result.get("windowEnd"));
        result.put("meta", meta);
        return result;
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
