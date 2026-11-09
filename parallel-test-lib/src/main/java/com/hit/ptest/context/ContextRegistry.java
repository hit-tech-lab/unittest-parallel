package com.hit.ptest.context;

import com.hit.ptest.ParallelTestException;
import com.hit.ptest.db.DbResource;
import com.hit.ptest.guard.GuardRule;
import com.hit.ptest.guard.GuardSettings;
import com.hit.ptest.intercept.InterceptStats;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Sổ sách context lib của JVM: đếm và ngưỡng tối đa. Một context chiếm "slot" từ lúc bắt đầu refresh đến khi bị
 * đóng (hoặc refresh lỗi), tức là đúng bằng số context lib đang nằm trong cache Spring Test. Context KHÔNG dùng lib
 * vẫn chiếm cache Spring nhưng không được đếm ở đây.
 */
public final class ContextRegistry {

    public static final ContextRegistry INSTANCE = new ContextRegistry();


    private final Map<ConfigurableApplicationContext, ManagedContext> active = new IdentityHashMap<>();
    private final List<String> created = new ArrayList<>();
    private final List<String> databases = new ArrayList<>();
    private final List<DbResource> resources = new ArrayList<>();
    private final Set<String> dbModes = new TreeSet<>();
    private final Set<String> dbModesRequested = new TreeSet<>();
    private int peakActive;
    /** DB còn sót đo ngay sau khi phiên JUnit đóng hết context (container còn sống); null nếu chưa đo hoặc sau đó lại có DB mới. */
    private volatile List<String> leftoversAtSessionEnd;

    synchronized void register(ManagedContext mc, int maxContexts) {
        if (active.containsKey(mc.context)) {
            return;
        }
        if (active.size() >= maxContexts) {
            String message = "CONTEXT_LIMIT_EXCEEDED: cannot create context '" + mc.key
                    + "': " + active.size() + " contexts are already active (ptest.max-contexts=" + maxContexts
                    + "). Active: " + activeNames() + budgetDetail(mc);
            switch (GuardSettings.severity(GuardRule.PT10)) {
                case FAIL -> throw new ParallelTestException.ContextLimit(message);
                case WARN -> LoggerFactory.getLogger(ContextRegistry.class).warn("(warning only, ptest.guard.PT10=warn) {}", message);
                case OFF -> { }
            }
        }
        leftoversAtSessionEnd = null;
        active.put(mc.context, mc);
        created.add(mc.key);
        dbModes.add(mc.dbMode);
        dbModesRequested.add(mc.dbModeRequested);
        peakActive = Math.max(peakActive, active.size());
    }

    synchronized void attachDatabase(ManagedContext mc, DbResource resource) {
        mc.attachDb(resource);
        leftoversAtSessionEnd = null;
        databases.add(mc.key + " -> " + resource.url());
        resources.add(resource);
    }

    synchronized void release(ManagedContext mc) {
        active.remove(mc.context);
    }

    /**
     * Đóng mọi context lib còn hoạt động, theo thứ tự ngược với thứ tự tạo. Gọi khi phiên JUnit kết thúc (mọi test đã xong): lúc đó
     * đóng chủ động thì DB/schema được dọn trong khi container Postgres còn sống. Nếu để shutdown hook của JVM đóng thì chạy đua với
     * hook của Testcontainers (kill container ngay khi JVM bắt đầu tắt), các context đóng sau đó bị chặn ở create-drop của Hibernate
     * vì không kết nối được.
     */
    public void closeAllActive() {
        List<ManagedContext> toClose;
        synchronized (this) {
            toClose = new ArrayList<>(inCreationOrder());
        }
        java.util.Collections.reverse(toClose);
        for (ManagedContext mc : toClose) {
            try {
                mc.context.close();
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(ContextRegistry.class).warn("Context {}: close at the end of the test session failed: {}", mc.key, e.toString());
            }
        }
        // Đo DB còn sót ngay bây giờ: ở shutdown hook container Postgres có thể đã bị Testcontainers kill giữa chừng khi đang đo
        leftoversAtSessionEnd = leftoverDbUrls();
    }

    private List<String> leftoverDbUrls() {
        List<DbResource> all;
        synchronized (this) {
            all = List.copyOf(resources);
        }
        return all.stream().filter(r -> r.exists().getAsBoolean()).map(DbResource::url).toList();
    }

    synchronized int activeCount() {
        return active.size();
    }

    public synchronized ManagedContext of(ApplicationContext ctx) {
        ManagedContext mc = active.get(ctx);
        if (mc == null) {
            throw new ParallelTestException.Config("CONTEXT_NOT_MANAGED: the ApplicationContext was not created by "
                    + "a class annotated with @ParallelSpringTest (or it has been closed)");
        }
        return mc;
    }

    /** Context mới được tạo bởi lớp nào, và nó khác context đang chạy GIỐNG NHẤT ở những mục nào (cách sửa cụ thể). */
    private String budgetDetail(ManagedContext created) {
        StringBuilder sb = new StringBuilder("\n  new context was requested by ").append(created.createdBy);
        ManagedContext closest = inCreationOrder().stream()
                .min(java.util.Comparator.comparingInt(m -> ContextConfigDescription.distance(m.config, created.config)))
                .orElse(null);
        if (closest == null) {
            return sb.toString();
        }
        List<String> diff = ContextConfigDescription.differences(closest.config, created.config);
        sb.append("\n  active contexts were created by: ")
                .append(inCreationOrder().stream().map(m -> m.createdBy).toList())
                .append("\n  closest active context is '").append(closest.key).append("' (created by ").append(closest.createdBy)
                .append("); the new one ").append(diff.isEmpty() ? "has the same Spring configuration (differs only in the ptest context name/declaration)"
                        : "differs in:\n" + String.join("\n", diff));
        sb.append("\n  [PT10] fix: make the class reuse an existing configuration (see the diff) or raise ptest.max-contexts after review");
        return sb.toString();
    }

    /** Context đang hoạt động theo thứ tự tạo (IdentityHashMap không có thứ tự): thông điệp và chọn "gần nhất" xác định. */
    private List<ManagedContext> inCreationOrder() {
        return active.values().stream().sorted(java.util.Comparator.comparingInt(m -> created.indexOf(m.key))).toList();
    }

    private String activeNames() {
        return inCreationOrder().stream().map(m -> m.key).toList().toString();
    }

    /**
     * Trạng thái lúc JVM thoát (sau khi Spring đóng các context ở shutdown hook): chờ tối đa {@code waitMillis} đến khi hết
     * context hoạt động, rồi đếm DB còn tồn tại thật (hỏi lại tài nguyên, không tin bộ đếm).
     */
    public Map<String, String> exitSnapshot(long waitMillis) {
        long deadline = System.currentTimeMillis() + waitMillis;
        while (activeCount() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (activeCount() > 0) {
            System.err.println("ptest: " + activeCount() + " context(s) still active " + waitMillis + " ms into JVM shutdown: " + activeNames()
                    + "\n" + shutdownThreads());
        }
        List<String> measured = leftoversAtSessionEnd;
        List<String> left = measured != null && activeCount() == 0 ? measured : leftoverDbUrls();
        Map<String, String> s = new TreeMap<>();
        s.put("exitContextsActive", Integer.toString(activeCount()));
        s.put("exitLeftoverDbs", Integer.toString(left.size()));
        s.put("exitLeftoverDbUrls", String.join(" | ", left));
        return s;
    }

    /** Chẩn đoán lúc thoát: các thread đóng context (shutdown hook của Spring/Boot) đang làm gì. */
    private static String shutdownThreads() {
        StringBuilder sb = new StringBuilder();
        Thread.getAllStackTraces().forEach((t, frames) -> {
            if (t.getName().toLowerCase(java.util.Locale.ROOT).contains("shutdown") && t != Thread.currentThread()) {
                sb.append("  thread '").append(t.getName()).append("' ").append(t.getState()).append('\n');
                for (int i = 0; i < Math.min(frames.length, 15); i++) {
                    sb.append("      at ").append(frames[i]).append('\n');
                }
            }
        });
        return sb.length() == 0 ? "  (no shutdown thread alive: the contexts are not being closed by any shutdown hook)" : sb.toString();
    }

    public synchronized Map<String, String> snapshot() {
        Map<String, String> s = new TreeMap<>();
        s.put("contextsCreated", Integer.toString(created.size()));
        s.put("contextsActive", Integer.toString(active.size()));
        s.put("contextsPeakActive", Integer.toString(peakActive));
        s.put("contexts", String.join(" ", created));
        s.put("dbMode", dbModes.isEmpty() ? "none" : String.join(",", dbModes));
        s.put("dbModeRequested", dbModesRequested.isEmpty() ? "auto" : String.join(",", dbModesRequested));
        s.put("databases", String.join(" | ", databases));
        s.put("interceptedCalls", Long.toString(InterceptStats.INTERCEPTED.sum()));
        s.put("realCalls", Long.toString(InterceptStats.REAL.sum()));
        s.put("unboundCalls", Long.toString(InterceptStats.UNBOUND.sum()));
        s.putAll(com.hit.ptest.guard.GuardStats.snapshot());
        return s;
    }
}
