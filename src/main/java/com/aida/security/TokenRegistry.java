package com.aida.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * The tokens allowed to call AIDA, from {@code aida.security.tokens-file} (a path, or {@code classpath:...}).
 *
 * <p>The file holds only SHA-256 hashes: a YAML list of {@code {name, sha256, projects, scopes}}. A presented token
 * is hashed and compared with every entry in constant time. A missing file means no token is valid. A file on disk
 * is re-read when it changes, so new tokens work without a restart.
 */
@Component
public class TokenRegistry {

    private static final Logger log = LoggerFactory.getLogger(TokenRegistry.class);

    record Entry(byte[] sha256, TokenPrincipal principal) {
    }

    private final Resource resource;
    private List<Entry> entries = List.of();
    private long loadedModified = Long.MIN_VALUE;

    public TokenRegistry(ResourceLoader resourceLoader, @Value("${aida.security.tokens-file}") String location) {
        this.resource = resourceLoader.getResource(location.startsWith("classpath:") ? location : "file:" + location);
        reloadIfChanged();
        if (entries.isEmpty()) {
            log.warn("No tokens loaded from {}; every call to /mcp and /admin will get 401. "
                    + "Create one with scripts/new-token.sh.", location);
        }
    }

    /** The principal for a presented token, if its hash matches an entry. */
    public Optional<TokenPrincipal> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        reloadIfChanged();
        byte[] presented = sha256(token);
        TokenPrincipal match = null;
        for (Entry entry : entries) {
            // Compare against every entry so timing does not reveal which one matched.
            if (MessageDigest.isEqual(presented, entry.sha256()) && match == null) {
                match = entry.principal();
            }
        }
        return Optional.ofNullable(match);
    }

    public static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private synchronized void reloadIfChanged() {
        if (!resource.exists()) {
            entries = List.of();
            return;
        }
        long modified = lastModified();
        if (modified == loadedModified && loadedModified != Long.MIN_VALUE) {
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            entries = parse(new Yaml().load(in));
            loadedModified = modified;
            log.info("Loaded {} token(s) from {}", entries.size(), resource.getDescription());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long lastModified() {
        try {
            return resource.isFile() ? resource.lastModified() : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private static List<Entry> parse(Object yaml) {
        if (!(yaml instanceof List<?> list)) {
            return List.of();
        }
        List<Entry> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && map.get("name") != null && map.get("sha256") != null) {
                byte[] hash = HexFormat.of().parseHex(map.get("sha256").toString().trim().toLowerCase());
                result.add(new Entry(hash, new TokenPrincipal(map.get("name").toString(),
                        upper(map.get("projects")), lower(map.get("scopes")))));
            }
        }
        return List.copyOf(result);
    }

    private static Set<String> upper(Object values) {
        Set<String> set = new LinkedHashSet<>();
        if (values instanceof List<?> list) {
            list.forEach(v -> set.add(v.toString().trim().toUpperCase()));
        }
        return set;
    }

    private static Set<String> lower(Object values) {
        Set<String> set = new LinkedHashSet<>();
        if (values instanceof List<?> list) {
            list.forEach(v -> set.add(v.toString().trim().toLowerCase()));
        }
        return set;
    }
}
