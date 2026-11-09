package com.hit.ptest;

import com.hit.ptest.context.ContextRegistry;
import com.hit.ptest.intercept.TestBinding;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import org.springframework.context.ApplicationContext;

/** Cửa sổ API tĩnh: truyền binding sang thread khác và đọc thông tin context. */
public final class ParallelTest {

    private ParallelTest() {
    }

    /** Tên header cần gắn vào request HTTP tới server thật để thread HTTP nhận binding. */
    public static String bindingHeaderName() {
        return TestBinding.HEADER;
    }

    /** Giá trị header của test hiện tại, hoặc null nếu thread không có binding. */
    public static String currentBindingId() {
        TestBinding b = TestBinding.current();
        return b == null ? null : b.id();
    }

    public static boolean hasBinding() {
        return TestBinding.current() != null;
    }

    /** Bọc Runnable để chạy với binding của thread nộp việc. */
    public static Runnable wrap(Runnable task) {
        return TestBinding.wrap(task);
    }

    /** Bọc Callable để chạy với binding của thread nộp việc. */
    public static <T> Callable<T> wrap(Callable<T> task) {
        return TestBinding.wrap(task);
    }

    /** Bọc Executor bất kỳ (ExecutorService tự tạo, ForkJoinPool...) để mọi task mang binding của người nộp. */
    public static Executor wrap(Executor executor) {
        return task -> executor.execute(wrap(task));
    }

    /** Tên context profile của ApplicationContext. */
    public static String contextName(ApplicationContext context) {
        return ContextRegistry.INSTANCE.of(context).name;
    }

    /** Khóa duy nhất của context (tên + số thứ tự tạo). */
    public static String contextKey(ApplicationContext context) {
        return ContextRegistry.INSTANCE.of(context).key;
    }
}
