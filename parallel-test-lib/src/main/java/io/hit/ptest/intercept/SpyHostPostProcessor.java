package io.hit.ptest.intercept;

import java.lang.reflect.Proxy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

/**
 * Đặt {@link SpyHost} làm target của bean, TRƯỚC mọi BPP tạo proxy AOP (PriorityOrdered, thứ tự cao nhất: cùng cách
 * {@code @MockitoSpyBean} của Spring dùng để spy nằm trong proxy). Nhờ đó spy chạy method thật trên chính nó, và
 * advice (@Transactional, @Async, @Cacheable...) vẫn áp dụng quanh spy.
 *
 * <p>Không làm gì với JDK proxy (vd repository Spring Data): không có bản thật để spy, spy của loại này ủy quyền cho proxy
 * (giống {@code @MockitoSpyBean}). Không hỗ trợ tham chiếu vòng mà Spring lộ sớm bản thật (early reference): Spring sẽ báo
 * lỗi "raw version" vì bean bị đổi thành host sau khi đã lộ.
 *
 * @apiNote internal: không phải API công khai của thư viện.
 */
public final class SpyHostPostProcessor implements BeanPostProcessor, BeanFactoryAware, PriorityOrdered {

    private final InterceptPlan plan;
    private ConfigurableListableBeanFactory beanFactory;

    public SpyHostPostProcessor(InterceptPlan plan) {
        this.plan = plan;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (Proxy.isProxyClass(bean.getClass()) || !plan.select(bean, beanName, beanFactory)) {
            return bean;
        }
        long start = System.nanoTime();
        try {
            SpyHost host = SpyHost.create(plan.owner(), beanName, bean);
            plan.registerHost(beanName, host);
            plan.noteWrapped(beanName, System.nanoTime() - start);
            return host.wrapper;
        } catch (RuntimeException e) {
            plan.wrapFailed(beanName, bean, e);
            return bean;
        }
    }
}
