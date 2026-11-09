package io.hit.ptest;

/** Hành vi khi một thread KHÔNG có binding của test gọi một bean can thiệp sau khi context đã khởi động xong. */
public enum UnboundPolicy {
    /** Luôn gọi bean thật (có log cảnh báo một lần). */
    REAL,
    /**
     * Luôn ném {@link ParallelTestException.UnboundThread}: xác định, không phụ thuộc thời điểm; mọi thread gọi bean được bọc
     * sau khi context khởi động phải có binding (wrap/TaskDecorator/header). Chặt nhất nhưng dễ vướng với thread nền khi
     * bọc rộng (mặc định {@code wrap="*"}) vì mọi bean ứng dụng đều được bọc.
     */
    FAIL,
    /**
     * Đơn điệu theo (context, bean) (MẶC ĐỊNH vì mọi bean ứng dụng đều được bọc, phần lớn không bao giờ bị mock/spy): gọi bean
     * thật cho đến khi BẤT KỲ test nào trong context từng khai MOCK/SPY cho bean, từ đó trở đi LUÔN ném lỗi (kể cả khi test
     * đó đã kết thúc). Vẫn phụ thuộc thời điểm ở chính lần gọi đầu (trước/sau test đầu tiên khai báo).
     */
    GUARDED
}
