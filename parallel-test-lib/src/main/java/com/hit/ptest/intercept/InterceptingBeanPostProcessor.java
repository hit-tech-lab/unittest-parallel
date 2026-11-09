package com.hit.ptest.intercept;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Bọc bean thuộc tập {@code wrap} (trừ {@code exclude}) bằng wrapper dùng chung (MOCK/REAL; SPY đi xuyên qua bean thật tới spy
 * bên trong proxy, xem {@link SpyHost}). KHÔNG implement Ordered/PriorityOrdered có chủ ý: Spring đăng ký mọi BPP không
 * có thứ tự SAU mọi BPP PriorityOrdered/Ordered, nên nó chạy sau AnnotationAwareAspectJAutoProxyCreator,
 * @Async, persistence translation... Bean thật bên trong đã là proxy AOP đầy đủ.
 *
 * <p>Giới hạn thứ tự: customizer đăng ký BPP này trước các bean ứng dụng, nên nó là BPP không thứ tự ĐẦU TIÊN; BPP
 * không thứ tự do người dùng khai báo (nếu có) chạy SAU và bọc bên ngoài wrapper. Cũng vì vậy bean lazy được khởi tạo
 * qua {@code @PerTestMock}/{@code @PerTestSpy} với binding rỗng; khởi tạo lazy kích hoạt ngẫu nhiên từ code ứng dụng
 * trong lúc test chạy thì {@code @PostConstruct} thấy binding của test đó (giới hạn đã biết).
 *
 * <p>Phạm vi bean hỗ trợ: xem {@link InterceptPlan}.
 */
public final class InterceptingBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private final InterceptPlan plan;
    private ConfigurableListableBeanFactory beanFactory;

    public InterceptingBeanPostProcessor(InterceptPlan plan) {
        this.plan = plan;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!plan.select(bean, beanName, beanFactory)) {
            return bean;
        }
        long start = System.nanoTime();
        try {
            BeanInterceptor bi = BeanInterceptor.create(plan.owner(), beanName, bean, plan.host(beanName));
            plan.owner().register(beanName, bi);
            plan.noteWrapped(beanName, System.nanoTime() - start);
            return bi.wrapper;
        } catch (RuntimeException e) {
            plan.wrapFailed(beanName, bean, e);
            return bean;
        }
    }
}
