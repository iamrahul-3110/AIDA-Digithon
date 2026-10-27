package com.aida.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * One JSON line per tool call in {@code <aida.data-dir>/audit.log}: {ts, tokenName, tool, args, resultSummary}.
 * Records the token's name, never the token.
 */
@Component
public class AuditLog {

    private final JsonMapper mapper;
    private final Path file;

    public AuditLog(JsonMapper mapper, @Value("${aida.data-dir}") Path dataDir) {
        this.mapper = mapper;
        this.file = dataDir.resolve("audit.log");
    }

    public synchronized void record(String tokenName, String tool, Map<String, Object> args, String resultSummary) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("ts", Instant.now().toString());
        line.put("tokenName", tokenName);
        line.put("tool", tool);
        line.put("args", args);
        line.put("resultSummary", resultSummary);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(line) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path file() {
        return file;
    }
}
