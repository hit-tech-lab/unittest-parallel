package demo.project;

/**
 * Cổng ra ngoài của một ứng dụng "thật" (database tài liệu, cache, mail...): không có cài đặt nào trong context test, nên
 * test khai chung bằng {@code @PerTestMock(Ports.Mailer.class)} cấp class ở lớp cơ sở để thư viện tạo bean mock (như {@code @MockBean}).
 */
public interface Ports {

    interface Mailer {
        String send(String to);
    }

    interface SmsSender {
        boolean send(String to, String text);
    }

    interface DocumentDb {
        String find(String id);

        void save(String id, String document);
    }

    interface Cache {
        String get(String key);
    }

    interface SearchIndex {
        int count(String query);
    }

    interface FileStore {
        String read(String path);
    }

    interface FeatureFlags {
        boolean enabled(String flag);
    }

    interface MetricsSink {
        void count(String metric);
    }

    interface AuditSink {
        void write(String event);
    }

    interface MessageQueue {
        void publish(String topic, String message);
    }
}
