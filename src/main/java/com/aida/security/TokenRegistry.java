package com.aida.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public class TokenRegistry {
    private static final Logger log = LoggerFactory.getLogger(TokenRegistry.class);

    @Value("${aida.security.tokens-file:./tokens.yml}")
    private String tokensFile;

    private final Map<String, TokenEntry> entries = new HashMap<>();

    @PostConstruct
    public void load() {
        Path path = Path.of(tokensFile);
        if (!Files.exists(path)) {
            log.warn("Tokens file not found: {}. No tokens loaded.", path.toAbsolutePath());
            return;
        }
        try (InputStream is = new FileInputStream(path.toFile())) {
            Yaml yaml = new Yaml();
            List<Map<String, Object>> list = yaml.load(is);
            if (list == null) return;
            for (Map<String, Object> entry : list) {
                String name = (String) entry.get("name");
                String sha256 = (String) entry.get("sha256");
                @SuppressWarnings("unchecked")
                List<String> projects = (List<String>) entry.getOrDefault("projects", List.of());
                @SuppressWarnings("unchecked")
                List<String> scopes = (List<String>) entry.getOrDefault("scopes", List.of());
                entries.put(sha256.toLowerCase(), new TokenEntry(name, new HashSet<>(projects), new HashSet<>(scopes)));
            }
            log.info("Loaded {} token(s) from {}", entries.size(), path.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to load tokens file: {}", e.getMessage());
        }
    }

    public Optional<TokenPrincipal> authenticate(String rawToken) {
        String hash = sha256hex(rawToken);
        return entries.entrySet().stream()
            .filter(e -> MessageDigest.isEqual(e.getKey().getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8)))
            .map(e -> new TokenPrincipal(e.getValue().name(), e.getValue().projects(), e.getValue().scopes()))
            .findFirst();
    }

    private static String sha256hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public record TokenEntry(String name, Set<String> projects, Set<String> scopes) {}
}
