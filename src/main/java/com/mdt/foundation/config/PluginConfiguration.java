package com.mdt.foundation.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PluginConfiguration {
    private static final String FILE_NAME = "plugin-config.properties";

    private final Path configDirectory;
    private final Path configFile;
    private final boolean apiEnabled;
    private final String apiHost;
    private final int apiPort;
    private final boolean apiRequireToken;
    private final String apiToken;
    private final long actionTimeoutMillis;

    private PluginConfiguration(
        Path configDirectory,
        Path configFile,
        boolean apiEnabled,
        String apiHost,
        int apiPort,
        boolean apiRequireToken,
        String apiToken,
        long actionTimeoutMillis
    ) {
        this.configDirectory = configDirectory;
        this.configFile = configFile;
        this.apiEnabled = apiEnabled;
        this.apiHost = apiHost;
        this.apiPort = apiPort;
        this.apiRequireToken = apiRequireToken;
        this.apiToken = apiToken;
        this.actionTimeoutMillis = actionTimeoutMillis;
    }

    public static PluginConfiguration load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(FILE_NAME);

        Properties defaults = createDefaults();
        if (Files.notExists(file)) {
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                defaults.store(outputStream, "MDT Foundation World Control");
            }
        }

        Properties values = new Properties();
        try (InputStream inputStream = Files.newInputStream(file)) {
            values.load(inputStream);
        }

        return new PluginConfiguration(
            directory,
            file,
            parseBoolean(values.getProperty("api.enabled"), defaults.getProperty("api.enabled")),
            parseString(values.getProperty("api.host"), defaults.getProperty("api.host")),
            parseInt(values.getProperty("api.port"), Integer.parseInt(defaults.getProperty("api.port"))),
            parseBoolean(values.getProperty("api.requireToken"), defaults.getProperty("api.requireToken")),
            parseString(values.getProperty("api.token"), defaults.getProperty("api.token")),
            parseLong(values.getProperty("action.timeoutMillis"), Long.parseLong(defaults.getProperty("action.timeoutMillis")))
        );
    }

    private static Properties createDefaults() {
        Properties properties = new Properties();
        properties.setProperty("api.enabled", "true");
        properties.setProperty("api.host", "127.0.0.1");
        properties.setProperty("api.port", "7788");
        properties.setProperty("api.requireToken", "false");
        properties.setProperty("api.token", "change-me");
        properties.setProperty("action.timeoutMillis", "5000");
        return properties;
    }

    private static boolean parseBoolean(String rawValue, String fallback) {
        return Boolean.parseBoolean(parseString(rawValue, fallback));
    }

    private static int parseInt(String rawValue, int fallback) {
        try {
            return Integer.parseInt(rawValue == null ? String.valueOf(fallback) : rawValue.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String rawValue, long fallback) {
        try {
            return Long.parseLong(rawValue == null ? String.valueOf(fallback) : rawValue.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String parseString(String rawValue, String fallback) {
        if (rawValue == null) {
            return fallback;
        }
        String trimmed = rawValue.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public Path getConfigDirectory() {
        return configDirectory;
    }

    public Path getConfigFile() {
        return configFile;
    }

    public boolean isApiEnabled() {
        return apiEnabled;
    }

    public String getApiHost() {
        return apiHost;
    }

    public int getApiPort() {
        return apiPort;
    }

    public boolean isApiRequireToken() {
        return apiRequireToken;
    }

    public String getApiToken() {
        return apiToken;
    }

    public long getActionTimeoutMillis() {
        return actionTimeoutMillis;
    }
}
