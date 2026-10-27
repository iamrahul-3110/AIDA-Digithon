package com.aida.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * One JSON line per tool call in {@code <aida.data-dir>/audit.log}: {ts, tokenName, tool, args, resultSummary}.
 * Records the token's name, never the token.
 *
 * <p>Also keeps each token's full tool results in memory for {@link #RETENTION}, so the guardrail can check a draft
 * answer against exactly what that token was told.
 */
@Component
public class AuditLog {

    /** How long full results are kept per token. */
    public static final Duration RETENTION = Duration.ofMinutes(30);

    /** A tool result as returned to a token. */
    public record Entry(Instant at, String tool, Map<String, Object> result) {
    }

    private final JsonMapper mapper;
    private final Path file;
    private final Clock clock;
    private final Map<String, Deque<Entry>> results = new ConcurrentHashMap<>();

    public AuditLog(JsonMapper mapper, @Value("${aida.data-dir}") Path dataDir, Clock clock) {
        this.mapper = mapper;
        this.file = dataDir.resolve("audit.log");
        this.clock = clock;
    }

    public synchronized void record(String tokenName, String tool, Map<String, Object> args, String resultSummary) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("ts", clock.instant().toString());
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

    /** Keeps a full result for this token; drops its results older than {@link #RETENTION}. */
    public void remember(String tokenName, String tool, Map<String, Object> result) {
        Deque<Entry> entries = results.computeIfAbsent(tokenName, k -> new ArrayDeque<>());
        synchronized (entries) {
            entries.addLast(new Entry(clock.instant(), tool, result));
            prune(entries);
        }
    }

    /** This token's results from the last {@link #RETENTION}, oldest first. */
    public List<Entry> recentResults(String tokenName) {
        Deque<Entry> entries = results.get(tokenName);
        if (entries == null) {
            return List.of();
        }
        synchronized (entries) {
            prune(entries);
            return List.copyOf(entries);
        }
    }

    private void prune(Deque<Entry> entries) {
        Instant cutoff = clock.instant().minus(RETENTION);
        while (!entries.isEmpty() && entries.peekFirst().at().isBefore(cutoff)) {
            entries.removeFirst();
        }
    }

    public Path file() {
        return file;
    }
}
