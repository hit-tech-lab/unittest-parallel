package io.hit.ptest.intercept;

import io.hit.ptest.UnboundPolicy;

/**
 * Thông tin mà phần chặn bean cần từ context sở hữu nó. Giao diện này thuộc package {@code intercept} để package này
 * không phụ thuộc ngược vào {@code context}; {@code ManagedContext} cài đặt.
 *
 * @apiNote internal: không phải API công khai của thư viện.
 */
public interface InterceptOwner {

    /** Khóa duy nhất của context. */
    String key();

    /** Tên context dành cho người đọc (khai báo ở {@code @ParallelSpringTest(context = ...)}); dùng trong thông báo lỗi thay cho {@link #key()}. */
    String name();

    UnboundPolicy policy();

    /** True khi context đã khởi động xong; trước đó lời gọi từ thread không binding chưa bị coi là vi phạm. */
    boolean isReady();

    /** Ghi nhận cảnh báo thread không binding; true nếu đây là lần đầu cho cặp (bean, thread) này. */
    boolean firstUnboundWarning(String warningKey);

    /** Tên chính tắc của bean (alias -> tên thật). */
    String canonical(String beanName);

    void register(String beanName, BeanInterceptor interceptor);
}
