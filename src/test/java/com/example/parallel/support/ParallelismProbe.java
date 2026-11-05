package com.example.parallel.support;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * JUnit Platform listener (registered via META-INF/services) that measures how parallel the run
 * REALLY was in this JVM, and writes target/parallel-reports/{phase}-fork{n}-{pid}.properties.
 * scripts/verify-parallel.sh reads those files: "parallel" is verified, not assumed.
 */
public class ParallelismProbe implements TestExecutionListener {

    private static final AtomicInteger RUNNING_TESTS = new AtomicInteger();
    private static final AtomicInteger MAX_TESTS = new AtomicInteger();
    private static final AtomicInteger RUNNING_CLASSES = new AtomicInteger();
    private static final AtomicInteger MAX_CLASSES = new AtomicInteger();
    private static final AtomicInteger TOTAL_TESTS = new AtomicInteger();
    private static final AtomicInteger FAILED_TESTS = new AtomicInteger();
    private static final AtomicLong FIRST_START = new AtomicLong(Long.MAX_VALUE);
    private static final AtomicLong LAST_END = new AtomicLong();
    private static final Set<String> THREADS = ConcurrentHashMap.newKeySet();

    @Override
    public void executionStarted(TestIdentifier id) {
        FIRST_START.accumulateAndGet(System.currentTimeMillis(), Math::min);
        if (id.isTest()) {
            THREADS.add(Thread.currentThread().getName());
            TOTAL_TESTS.incrementAndGet();
            MAX_TESTS.accumulateAndGet(RUNNING_TESTS.incrementAndGet(), Math::max);
        } else if (isClass(id)) {
            MAX_CLASSES.accumulateAndGet(RUNNING_CLASSES.incrementAndGet(), Math::max);
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        LAST_END.accumulateAndGet(System.currentTimeMillis(), Math::max);
        if (id.isTest()) {
            RUNNING_TESTS.decrementAndGet();
            if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
                FAILED_TESTS.incrementAndGet();
            }
        } else if (isClass(id)) {
            RUNNING_CLASSES.decrementAndGet();
        }
    }

    private static boolean isClass(TestIdentifier id) {
        return id.getSource().filter(ClassSource.class::isInstance).isPresent();
    }

    /** Surefire may run several test plans per fork: stats are cumulative, the file is rewritten each time. */
    @Override
    public synchronized void testPlanExecutionFinished(TestPlan testPlan) {
        String dir = System.getProperty("probe.dir");
        if (dir == null) {
            return; // not running under the Maven build (e.g. from the IDE)
        }
        String phase = System.getProperty("probe.phase", "unknown");
        String fork = System.getProperty("fork.number", "0");
        long pid = ProcessHandle.current().pid();

        Properties p = new Properties();
        p.setProperty("phase", phase);
        p.setProperty("fork", fork);
        p.setProperty("pid", Long.toString(pid));
        p.setProperty("tests", Integer.toString(TOTAL_TESTS.get()));
        p.setProperty("failedTests", Integer.toString(FAILED_TESTS.get()));
        p.setProperty("maxConcurrentTests", Integer.toString(MAX_TESTS.get()));
        p.setProperty("maxConcurrentClasses", Integer.toString(MAX_CLASSES.get()));
        p.setProperty("threads", Integer.toString(THREADS.size()));
        p.setProperty("startMillis", Long.toString(FIRST_START.get()));
        p.setProperty("endMillis", Long.toString(LAST_END.get()));
        p.setProperty("springContexts", Integer.toString(ProbeRegistry.CONTEXT_DATABASES.size()));
        Set<String> dbs = new TreeSet<>(ProbeRegistry.CONTEXT_DATABASES.values());
        dbs.remove("none");
        long ctxWithDb = ProbeRegistry.CONTEXT_DATABASES.values().stream().filter(v -> !"none".equals(v)).count();
        p.setProperty("contextsWithDatabase", Long.toString(ctxWithDb));
        p.setProperty("distinctDatabases", Integer.toString(dbs.size()));
        p.setProperty("databases", String.join(" ", dbs));

        try {
            Path out = Path.of(dir, phase + "-fork" + fork + "-" + pid + ".properties");
            Files.createDirectories(out.getParent());
            try (OutputStream os = Files.newOutputStream(out)) {
                p.store(os, "ParallelismProbe");
            }
        } catch (IOException e) {
            System.err.println("ParallelismProbe: cannot write report: " + e);
        }
    }
}
