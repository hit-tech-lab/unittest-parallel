package com.example.parallel.audit;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

/**
 * Singleton bean with mutable state. Every test sharing the same ApplicationContext
 * shares this instance -- a classic source of conflicts when tests run in parallel.
 */
@Component
public class AuditLog {

    public record Event(String type, Long fromAccountId, Long toAccountId, String detail) {
    }

    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();

    public void record(Event event) {
        events.add(event);
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
