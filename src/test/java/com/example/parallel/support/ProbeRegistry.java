package com.example.parallel.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** JVM-wide registry filled by {@link ContextTrackingListener}, reported by {@link ParallelismProbe}. */
public final class ProbeRegistry {

    /** identityHashCode(ApplicationContext) -> JDBC URL of its DataSource ("none" if no DataSource). */
    static final Map<Integer, String> CONTEXT_DATABASES = new ConcurrentHashMap<>();

    private ProbeRegistry() {
    }
}
