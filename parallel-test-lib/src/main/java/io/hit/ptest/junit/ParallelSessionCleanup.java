package io.hit.ptest.junit;

import io.hit.ptest.context.ContextRegistry;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Đóng các context lib khi phiên JUnit kết thúc (mọi test của JVM đã chạy xong), trước khi JVM bắt đầu tắt. Nhờ đó DB/schema của
 * từng context được dọn khi container Postgres còn sống, không chạy đua với shutdown hook của Testcontainers. Đăng ký qua ServiceLoader.
 * <p>
 * Chỉ phiên ngoài cùng (phiên cuối còn mở) mới dọn: {@code LauncherFactory.create()} mở và đóng một phiên cho MỖI lần {@code execute}, nên
 * một test chạy launcher lồng bên trong (hoặc IDE mở nhiều phiên) sẽ đóng context của các test khác đang chạy nếu dọn ở mọi phiên.
 * Mỗi JVM fork có registry riêng nên các fork không ảnh hưởng nhau. Đóng context đã đóng là không có tác dụng nên shutdown hook của
 * Spring sau đó không bị ảnh hưởng.
 */
public final class ParallelSessionCleanup implements LauncherSessionListener {

    /** Số phiên đang mở trong JVM này (static vì mỗi phiên tạo một thể hiện listener riêng). */
    private static final AtomicInteger OPEN_SESSIONS = new AtomicInteger();

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        OPEN_SESSIONS.incrementAndGet();
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        if (OPEN_SESSIONS.decrementAndGet() <= 0) {
            OPEN_SESSIONS.set(0);
            ContextRegistry.INSTANCE.closeAllActive();
        }
    }
}
