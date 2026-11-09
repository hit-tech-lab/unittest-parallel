package demo.core;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Bean có state: field nguyên thủy (tách khỏi spy) và field tham chiếu (dùng chung với spy): xem SpyStateIT. */
@Component
public class StatefulCounter {

    private int plain;
    private final AtomicInteger atomic = new AtomicInteger();
    private final List<String> log = new CopyOnWriteArrayList<>();

    public int incrementPlain() {
        return ++plain;
    }

    public int plain() {
        return plain;
    }

    public int incrementAtomic() {
        return atomic.incrementAndGet();
    }

    public int atomic() {
        return atomic.get();
    }

    public void note(String entry) {
        log.add(entry);
    }

    public List<String> log() {
        return List.copyOf(log);
    }
}
