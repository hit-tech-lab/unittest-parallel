package demo.core;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final AuditLog auditLog;

    public NotificationService(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /** Chạy trên executor của Spring; ghi audit từ thread của executor. */
    @Async
    public CompletableFuture<String> notifyAsync(String message) {
        auditLog.record(new AuditLog.Event("NOTIFY", message));
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }

    /** Như notifyAsync nhưng gọi nội bộ {@code this.decorate}; trả về tên thread đã chạy. */
    @Async
    public CompletableFuture<String> notifyDecorated(String message) {
        auditLog.record(new AuditLog.Event("NOTIFY", decorate(message)));
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }

    public String decorate(String message) {
        return "[notify] " + message;
    }
}
