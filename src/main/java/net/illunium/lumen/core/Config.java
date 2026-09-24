package net.illunium.lumen.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;
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

    /** Parses config from any reader. {@code source} only names the origin in error messages. */
    public static Config parse(Reader reader, String source) {
        Map<String, Object> root = new Yaml().load(reader);
        if (root == null) {
            throw new IllegalStateException("Config file is empty: " + source);
        }
        return new Config(root, source);
    }

    /** Discord ID at the given dotted path, e.g. {@code channels.announcements}. */
    public long id(String path) {
        Object node = node(path);
        if (node == null) {
            throw new IllegalStateException("Missing config key '" + path + "' in " + source);
        }
        if (node instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Config key '" + path + "' is not an ID in " + source
                + " (got: " + node + ")");
    }

    /**
     * Discord ID at the given dotted path, empty if the key is absent or not a number.
     *
     * <p>For optional IDs, where a missing value is a valid state rather than a broken
     * config, so the caller can react instead of catching.
     */
    public OptionalLong findId(String path) {
        return node(path) instanceof Number number
                ? OptionalLong.of(number.longValue())
                : OptionalLong.empty();
    }

    /** Raw value at the dotted path, or {@code null} if any segment is missing. */
    private Object node(String path) {
        Object node = root;
        for (String key : path.split("\\.")) {
            if (!(node instanceof Map<?, ?> map) || !map.containsKey(key)) {
                return null;
            }
            node = map.get(key);
        }
        return node;
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
