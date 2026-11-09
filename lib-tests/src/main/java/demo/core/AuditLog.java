package demo.core;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

/** Singleton có state: mỗi test dùng chung bản thật thấy state của nhau (giới hạn đã biết). */
@Component
public class AuditLog {
    public record Event(String type, String detail) {
    }

    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();

    public void record(Event event) {
        events.add(event);
    }

    public List<Event> events() {
        return List.copyOf(events);
    }
}
