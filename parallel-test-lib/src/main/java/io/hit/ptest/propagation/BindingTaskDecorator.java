package io.hit.ptest.propagation;

import io.hit.ptest.intercept.TestBinding;

import org.springframework.core.task.TaskDecorator;

/** Mang binding của thread nộp việc sang thread thực thi (executor/@Async của Spring). */
public final class BindingTaskDecorator implements TaskDecorator {

    private final TaskDecorator delegate;

    public BindingTaskDecorator(TaskDecorator delegate) {
        this.delegate = delegate;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        Runnable inner = delegate == null ? runnable : delegate.decorate(runnable);
        return TestBinding.wrap(inner);
    }
}
