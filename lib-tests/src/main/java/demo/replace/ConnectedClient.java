package demo.replace;

import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Bean quét được kết nối ra ngoài trong {@code @PostConstruct}: ném lỗi nếu được khởi tạo thật. */
@Component
public class ConnectedClient {

    public static final AtomicInteger CONNECTED = new AtomicInteger();

    @PostConstruct
    void connect() {
        CONNECTED.incrementAndGet();
        throw new IllegalStateException("real client connected at startup");
    }

    public String ping() {
        return "pong";
    }
}
