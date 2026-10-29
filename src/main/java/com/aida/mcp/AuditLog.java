package com.aida.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);

    @Value("${aida.data-dir:./data}")
    private String dataDir;

    private final ObjectMapper objectMapper;

    // In-memory recent entries for verify_answer guardrail
    private final java.util.concurrent.ConcurrentLinkedDeque<Map<String, Object>> recentEntries
        = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public AuditLog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(String tokenName, String tool, Map<String, Object> args, String resultSummary) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("tokenName", tokenName);
        entry.put("tool", tool);
        entry.put("args", args);
        entry.put("resultSummary", resultSummary);

        recentEntries.addFirst(entry);
        // keep last 1000
        while (recentEntries.size() > 1000) recentEntries.pollLast();

        try {
            Path dir = Path.of(dataDir);
            Files.createDirectories(dir);
            try (PrintWriter pw = new PrintWriter(new FileWriter(dir.resolve("audit.log").toFile(), true))) {
                pw.println(objectMapper.writeValueAsString(entry));
            }
        } catch (IOException e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }

    public java.util.List<Map<String, Object>> getRecentEntries(int minutes) {
        Instant cutoff = Instant.now().minusSeconds((long) minutes * 60);
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> entry : recentEntries) {
            Instant ts = Instant.parse((String) entry.get("ts"));
            if (ts.isAfter(cutoff)) result.add(entry);
        }
        return result;
    }
}
