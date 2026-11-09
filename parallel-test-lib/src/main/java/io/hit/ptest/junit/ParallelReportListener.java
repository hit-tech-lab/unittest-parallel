package io.hit.ptest.junit;

import io.hit.ptest.context.ContextRegistry;
import io.hit.ptest.context.PtestSettings;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Đo mức song song THẬT của JVM và số context; ghi file khi đặt {@code -Dptest.report.dir=...}. Đăng ký qua
 * ServiceLoader, không làm gì nếu không có thuộc tính đó.
 */
public final class ParallelReportListener implements TestExecutionListener {

    private static final AtomicInteger RUNNING_TESTS = new AtomicInteger();
    private static final AtomicInteger MAX_TESTS = new AtomicInteger();
    private static final AtomicInteger RUNNING_CLASSES = new AtomicInteger();
    private static final AtomicInteger MAX_CLASSES = new AtomicInteger();
    private static final AtomicInteger TOTAL = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();
    private static final AtomicLong FIRST_START = new AtomicLong(Long.MAX_VALUE);
    private static final AtomicLong LAST_END = new AtomicLong();
    /** Bản chụp số liệu MỚI NHẤT (mỗi test plan ghi đè): shutdown hook ghi lại bản này chứ không phải bản của test plan đầu. */
    private static volatile Properties latest;
    private static volatile String latestDir;
    private static final AtomicBoolean HOOK_REGISTERED = new AtomicBoolean();
    private static final Set<String> THREADS = ConcurrentHashMap.newKeySet();

    @Override
    public void executionStarted(TestIdentifier id) {
        FIRST_START.accumulateAndGet(System.currentTimeMillis(), Math::min);
        if (id.isTest()) {
            THREADS.add(Thread.currentThread().getName());
            TOTAL.incrementAndGet();
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
            if (result.getStatus() == TestExecutionResult.Status.FAILED) {
                FAILED.incrementAndGet();
            }
        } else if (isClass(id)) {
            RUNNING_CLASSES.decrementAndGet();
        }
    }

    private static boolean isClass(TestIdentifier id) {
        return id.getSource().filter(ClassSource.class::isInstance).isPresent();
    }

    @Override
    public synchronized void testPlanExecutionFinished(TestPlan testPlan) {
        String dir = PtestSettings.reportDir();
        if (dir == null) {
            return;
        }
        Properties p = new Properties();
        p.setProperty("fork", PtestSettings.forkNumber());
        p.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        p.setProperty("tests", Integer.toString(TOTAL.get()));
        p.setProperty("failedTests", Integer.toString(FAILED.get()));
        p.setProperty("maxConcurrentTests", Integer.toString(MAX_TESTS.get()));
        p.setProperty("maxConcurrentClasses", Integer.toString(MAX_CLASSES.get()));
        p.setProperty("threads", Integer.toString(THREADS.size()));
        p.setProperty("startMillis", Long.toString(FIRST_START.get()));
        p.setProperty("endMillis", Long.toString(LAST_END.get()));
        for (Map.Entry<String, String> e : ContextRegistry.INSTANCE.snapshot().entrySet()) {
            p.setProperty(e.getKey(), e.getValue());
        }
        Path out = write(dir, p);
        latest = p;
        latestDir = dir;
        if (out != null && HOOK_REGISTERED.compareAndSet(false, true)) {
            // Spring đóng các context ở shutdown hook của nó; hook này chờ cho đến khi chúng đóng xong rồi ghi thêm trạng thái thoát.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Properties current = latest;
                if (current != null) {
                    current.putAll(ContextRegistry.INSTANCE.exitSnapshot(PtestSettings.exitWaitMillis()));
                    write(latestDir, current);
                }
            }, "ptest-exit-report"));
        }
    }

    private static Path write(String dir, Properties p) {
        try {
            Path out = Path.of(dir, "fork" + p.getProperty("fork") + "-" + p.getProperty("pid") + ".properties");
            Files.createDirectories(out.getParent());
            try (OutputStream os = Files.newOutputStream(out)) {
                p.store(os, "ptest report");
            }
            return out;
        } catch (IOException e) {
            System.err.println("ptest: cannot write report: " + e);
            return null;
        }
    }
}
