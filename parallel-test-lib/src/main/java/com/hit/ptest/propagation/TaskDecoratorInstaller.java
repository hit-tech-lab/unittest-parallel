package com.hit.ptest.propagation;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.ReflectionUtils;

/**
 * Gắn {@link BindingTaskDecorator} cho executor Spring có {@code setTaskDecorator}, bọc (compose) decorator sẵn có của
 * người dùng thay vì đè lên. Nếu không đọc được decorator hiện tại thì KHÔNG đụng vào executor và WARN (đè lên có thể
 * làm mất decorator của người dùng); executor không có {@code setTaskDecorator} cũng WARN và nên dùng
 * {@code ParallelTest.wrap(Executor)}. Phụ thuộc phiên bản Spring: {@code ThreadPoolTaskScheduler} chỉ có {@code setTaskDecorator}
 * từ Spring 6.2; trên Spring 6.1 (Boot 3.3) bean đó bị WARN và tác vụ của nó phải bọc bằng {@code ParallelTest.wrap}.
 *
 * <p>Đã kiểm tra (test) tác vụ thường và tác vụ lên lịch một lần của: {@code ThreadPoolTaskExecutor},
 * {@code SimpleAsyncTaskExecutor}, {@code SimpleAsyncTaskScheduler}, {@code ConcurrentTaskExecutor},
 * {@code ConcurrentTaskScheduler}, {@code ThreadPoolTaskScheduler}. KHÔNG hỗ trợ: executor không có setter (vd
 * {@code Executors.newFixedThreadPool} khai báo thẳng thành bean, executor tự viết) và executor có setter nhưng không
 * đọc lại được decorator hiện tại; cả hai chỉ WARN, cần {@code ParallelTest.wrap}. Chưa kiểm tra tác vụ lặp
 * (fixed-rate/cron).
 */
public final class TaskDecoratorInstaller implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(TaskDecoratorInstaller.class);
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        var setter = ReflectionUtils.findMethod(bean.getClass(), "setTaskDecorator", TaskDecorator.class);
        if (setter == null) {
            // Executor của Spring chỉ được bỏ qua khi chạy ngay trên thread gọi (SyncTaskExecutor). Spring cũ thiếu setter ở lớp khác
            // (vd ThreadPoolTaskScheduler trước Spring 6.2) thì binding KHÔNG truyền: phải cảnh báo như executor tự viết.
            if (bean instanceof Executor && !(bean instanceof SyncTaskExecutor)) {
                warnOnce(beanName, "Executor bean '" + beanName + "' (" + bean.getClass().getName() + ") has no setTaskDecorator: "
                        + "test bindings are NOT propagated to its threads. Wrap submissions with ParallelTest.wrap(...) or the executor itself.");
            }
            return bean;
        }
        TaskDecorator existing;
        try {
            DirectFieldAccessor accessor = new DirectFieldAccessor(bean);
            if (!accessor.isReadableProperty("taskDecorator")) {
                throw new IllegalStateException("no readable field 'taskDecorator'");
            }
            existing = (TaskDecorator) accessor.getPropertyValue("taskDecorator");
        } catch (RuntimeException e) {
            warnOnce(beanName, "Cannot read the current TaskDecorator of '" + beanName + "' (" + bean.getClass().getName() + "): " + e
                    + ". Left untouched so a user decorator is not overwritten; test bindings are NOT propagated to its threads. "
                    + "Wrap submissions with ParallelTest.wrap(...).");
            return bean;
        }
        if (existing instanceof BindingTaskDecorator) {
            return bean;
        }
        ReflectionUtils.invokeMethod(setter, bean, new BindingTaskDecorator(existing));
        return bean;
    }

    private void warnOnce(String beanName, String message) {
        if (warned.add(beanName)) {
            LOG.warn(message);
        }
    }
}
