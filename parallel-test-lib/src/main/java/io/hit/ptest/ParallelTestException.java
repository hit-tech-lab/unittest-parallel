package io.hit.ptest;

/** Gốc các lỗi cấu hình/sử dụng thư viện; thông điệp bắt đầu bằng mã lỗi ổn định để script kiểm tra. */
public class ParallelTestException extends RuntimeException {

    public ParallelTestException(String message) {
        super(message);
    }

    /** Cấu hình sai (bean không can thiệp được, trùng tên context khác cấu hình, vòng đời không hỗ trợ). */
    public static class Config extends ParallelTestException {
        public Config(String message) {
            super(message);
        }
    }

    /** Thread không binding gọi bean can thiệp theo {@link UnboundPolicy}. */
    public static class UnboundThread extends ParallelTestException {
        public UnboundThread(String message) {
            super(message);
        }
    }

    /** Tính năng Spring Test không an toàn khi chạy song song (DirtiesContext, rollback với cổng thật). */
    public static class Unsupported extends ParallelTestException {
        public Unsupported(String message) {
            super(message);
        }
    }

    /** Vượt ngưỡng số context tối đa. */
    public static class ContextLimit extends ParallelTestException {
        public ContextLimit(String message) {
            super(message);
        }
    }

    /** Vi phạm luật guard (PT..): thông điệp liệt kê từng vi phạm với vị trí, lý do và cách sửa. */
    public static class Guard extends ParallelTestException {
        private final java.util.List<String> ruleCodes;

        public Guard(String message, java.util.List<String> ruleCodes) {
            super(message);
            this.ruleCodes = java.util.List.copyOf(ruleCodes);
        }

        /** Mã các luật bị vi phạm (theo thứ tự xuất hiện, có thể lặp). */
        public java.util.List<String> ruleCodes() {
            return ruleCodes;
        }
    }
}
