package com.hittech.paralleltest.guard;

import java.io.InputStream;
import java.util.Properties;

/** Effective number of JUnit worker threads, read like JUnit does (system property > junit-platform.properties). */
final class JUnitParallelism {

    private static final String PREFIX = "junit.jupiter.execution.parallel.";

    private JUnitParallelism() {
    }

    static int effective() {
        Properties file = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("junit-platform.properties")) {
            if (in != null) {
                file.load(in);
            }
        } catch (Exception ignored) {
            // treat as absent
        }
        if (!Boolean.parseBoolean(get(file, "enabled", "false"))) {
            return 1;
        }
        String strategy = get(file, "config.strategy", "dynamic");
        int cores = Runtime.getRuntime().availableProcessors();
        return switch (strategy) {
            case "fixed" -> Integer.parseInt(get(file, "config.fixed.parallelism", "1"));
            case "dynamic" -> Math.max(1, (int) (cores * Double.parseDouble(get(file, "config.dynamic.factor", "1"))));
            default -> 1; // custom strategy: unknown
        };
    }

    private static String get(Properties file, String key, String def) {
        String sys = System.getProperty(PREFIX + key);
        return (sys != null ? sys : file.getProperty(PREFIX + key, def)).trim();
    }
}
