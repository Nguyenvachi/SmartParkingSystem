package com.parking.smartparking.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal .env loader for local runs (mvn/IDE).
 *
 * Docker Compose loads root .env automatically, but local Spring Boot does not.
 * This helper reads .env and exposes needed values via System properties.
 */
public final class DotenvSupport {

    private DotenvSupport() {
    }

    public static void loadLocalDotenvIfPresent() {
        // Walk up from current working directory to find a .env (max 6 levels).
        Path dir = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                try {
                    Map<String, String> env = parseDotenv(candidate);
                    applyMailMappings(env);
                    // Also expose raw APP_* values so placeholders like ${APP_MAIL_ENABLED} resolve.
                    copyIfAbsent(env, "APP_MAIL_ENABLED");
                    copyIfAbsent(env, "APP_MAIL_FROM");
                    copyIfAbsent(env, "APP_PASSWORD_RESET_EXPOSE_TOKEN");
                    return;
                } catch (Exception ignored) {
                    // Best-effort only. Diagnostics are shown via MailStartupDiagnostics.
                    return;
                }
            }
            dir = dir.getParent();
        }
    }

    private static void applyMailMappings(Map<String, String> env) {
        // Map env-style keys to canonical spring.mail.* keys for auto-config
        mapIfAbsent(env, "SPRING_MAIL_HOST", "spring.mail.host");
        mapIfAbsent(env, "SPRING_MAIL_PORT", "spring.mail.port");
        mapIfAbsent(env, "SPRING_MAIL_USERNAME", "spring.mail.username");
        mapIfAbsent(env, "SPRING_MAIL_PASSWORD", "spring.mail.password");

        // Also accept already-canonical keys (some users may put dots in .env for local runs)
        copyFromEnvMapIfAbsent(env, "spring.mail.host");
        copyFromEnvMapIfAbsent(env, "spring.mail.port");
        copyFromEnvMapIfAbsent(env, "spring.mail.username");
        copyFromEnvMapIfAbsent(env, "spring.mail.password");

        mapIfAbsent(env, "SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH", "spring.mail.properties.mail.smtp.auth");
        mapIfAbsent(env, "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE", "spring.mail.properties.mail.smtp.starttls.enable");

        copyFromEnvMapIfAbsent(env, "spring.mail.properties.mail.smtp.auth");
        copyFromEnvMapIfAbsent(env, "spring.mail.properties.mail.smtp.starttls.enable");

        // Sensible Gmail defaults
        setIfAbsent("spring.mail.properties.mail.smtp.starttls.required", "true");

        String host = firstNonBlank(env.get("SPRING_MAIL_HOST"), env.get("spring.mail.host"));
        if (host != null) {
            setIfAbsent("spring.mail.properties.mail.smtp.ssl.trust", host);

            // Helpful defaults for Gmail
            if ("smtp.gmail.com".equalsIgnoreCase(host.trim())) {
                setIfAbsent("spring.mail.port", "587");
                setIfAbsent("spring.mail.properties.mail.smtp.auth", "true");
                setIfAbsent("spring.mail.properties.mail.smtp.starttls.enable", "true");
            }
        }

        // App mail flags (used by services)
        mapIfAbsent(env, "APP_MAIL_ENABLED", "app.mail.enabled");
        mapIfAbsent(env, "APP_MAIL_FROM", "app.mail.from");

        copyFromEnvMapIfAbsent(env, "app.mail.enabled");
        copyFromEnvMapIfAbsent(env, "app.mail.from");
    }

    private static void copyFromEnvMapIfAbsent(Map<String, String> env, String key) {
        String v = env.get(key);
        if (v == null || v.isBlank()) {
            return;
        }
        setIfAbsent(key, v);
    }

    private static void copyIfAbsent(Map<String, String> env, String key) {
        String v = env.get(key);
        if (v == null || v.isBlank()) {
            return;
        }
        setIfAbsent(key, v);
    }

    private static void mapIfAbsent(Map<String, String> env, String fromKey, String toKey) {
        String v = env.get(fromKey);
        if (v == null || v.isBlank()) {
            return;
        }
        setIfAbsent(toKey, v);
    }

    private static void setIfAbsent(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        if (System.getProperty(key) != null) {
            return;
        }
        System.setProperty(key, value);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static Map<String, String> parseDotenv(Path file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                int eq = t.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = t.substring(0, eq).trim();
                String value = t.substring(eq + 1).trim();
                value = unquote(value);
                out.put(key, value);
            }
        }
        return out;
    }

    private static String unquote(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.length() >= 2) {
            char first = v.charAt(0);
            char last = v.charAt(v.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }
}
