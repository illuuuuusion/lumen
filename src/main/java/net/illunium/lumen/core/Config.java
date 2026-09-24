package net.illunium.lumen.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Non-sensitive configuration (guild, channel and role IDs) plus environment secrets.
 *
 * <p>IDs are looked up by dotted path so no Discord ID is ever hardcoded in a command
 * or event handler. Secrets never live in this file, only in the environment.
 */
public final class Config {

    private final Map<String, Object> root;
    private final String source;

    Config(Map<String, Object> root, String source) {
        this.root = root;
        this.source = source;
    }

    /** Loads the file from {@code LUMEN_CONFIG_PATH}, defaulting to {@code config.yml}. */
    public static Config load() {
        String path = System.getenv().getOrDefault("LUMEN_CONFIG_PATH", "config.yml");
        Path file = Path.of(path);
        if (!Files.isReadable(file)) {
            throw new IllegalStateException("Config file not readable: " + file.toAbsolutePath()
                    + " (set LUMEN_CONFIG_PATH or copy config.example.yml to config.yml)");
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return parse(reader, path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read config file: " + file.toAbsolutePath(), e);
        }
    }

    static Config parse(Reader reader, String source) {
        Map<String, Object> root = new Yaml().load(reader);
        if (root == null) {
            throw new IllegalStateException("Config file is empty: " + source);
        }
        return new Config(root, source);
    }

    /** Discord ID at the given dotted path, e.g. {@code channels.announcements}. */
    public long id(String path) {
        Object node = root;
        for (String key : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> map) || !map.containsKey(key)) {
                throw new IllegalStateException("Missing config key '" + path + "' in " + source);
            }
            node = map.get(key);
        }
        if (node instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Config key '" + path + "' is not an ID in " + source
                + " (got: " + node + ")");
    }

    public long guildId() {
        return id("guild.id");
    }

    /** Required environment variable, used for secrets. Never logged. */
    public static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
