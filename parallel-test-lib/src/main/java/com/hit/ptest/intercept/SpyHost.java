package com.hit.ptest.intercept;

import java.lang.reflect.Method;
import org.mockito.MockSettings;
import org.mockito.Mockito;

/**
 * Vị trí của spy trong chuỗi gọi, tái lập cách {@code @MockitoSpyBean} đặt spy: BÊN TRONG proxy AOP, ở vị trí target.
 * {@link SpyHostPostProcessor} chạy TRƯỚC các BPP AOP và thay bản thật bằng {@link #wrapper} (mock Mockito dùng chung,
 * không trạng thái, cùng lớp với bản thật); proxy AOP (transaction, async, cache...) bọc quanh wrapper này. Mọi lời gọi
 * vào wrapper được chuyển tới spy của test đang gắn với thread, hoặc tới bản thật khi không có spy.
 *
 * <p>Spy của một test là {@code Mockito.mock(lớp, spiedInstance(bản thật), CALLS_REAL_METHODS)} như {@code Mockito.spy}:
 * Mockito SAO CHÉP giá trị field tại lúc tạo, và method thật chạy trên chính spy, nên {@code this.x()} bên trong bean đi qua
 * spy (verify/doReturn/doThrow thấy nó). Hệ quả: field kiểu tham chiếu (collection, AtomicInteger, dependency đã tiêm) vẫn
 * trỏ cùng đối tượng với bean thật, còn field nguyên thủy hoặc field bị gán lại của spy tách khỏi bean thật và khỏi spy của test khác.
 *
 * <p>Thread không có binding gọi vào host (thread nền mất binding bên trong proxy AOP) bị xử lý theo {@code ptest.unbound-policy} như wrapper
 * ngoài: không bao giờ âm thầm chạy bản thật khi bean đang bị test nào đó spy (mặc định {@code guarded}).
 *
 * @apiNote internal: không phải API công khai của thư viện.
 */
final class SpyHost {

    private final InterceptOwner owner;
    private final String beanName;
    private final Object raw;
    final Object wrapper;

    private SpyHost(InterceptOwner owner, String beanName, Object raw) {
        this.owner = owner;
        this.beanName = beanName;
        this.raw = raw;
        this.wrapper = Mockito.mock(raw.getClass(), Mockito.withSettings().stubOnly().name("spyHost:" + beanName)
                .defaultAnswer(inv -> isToString(inv.getMethod()) ? "SpyHost[" + beanName + "]" : dispatch(inv.getMethod(), inv.getRawArguments())));
    }

    /** Bản thật của bean (nơi giữ trạng thái). */
    Object rawBean() {
        return raw;
    }

    static SpyHost create(InterceptOwner owner, String beanName, Object raw) {
        return new SpyHost(owner, beanName, raw);
    }

    /** Spy riêng của một test, sao chép field của bản thật lúc này. */
    Object newSpy() {
        MockSettings settings = Mockito.withSettings().name(beanName + "@test:SPY").spiedInstance(raw)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS);
        return Mockito.mock(raw.getClass(), settings);
    }

    /** True nếu {@code target} là spy do {@link #newSpy()} tạo cho bean này (phân biệt với mock cùng bean của test khác). */
    boolean isSpy(Object target) {
        var details = Mockito.mockingDetails(target);
        return details.isMock() && details.getMockCreationSettings().getSpiedInstance() == raw;
    }

    private static boolean isToString(Method method) {
        return method.getParameterCount() == 0 && "toString".equals(method.getName());
    }

    private Object dispatch(Method method, Object[] args) throws Throwable {
        Object target = raw;
        TestBinding binding = TestBinding.current();
        boolean closed = binding != null && binding.isClosed();
        if (binding != null && !closed) {
            Object t = binding.target(new TestBinding.TargetKey(owner.key(), beanName));
            if (t != null && isSpy(t)) {
                target = t;
            }
        } else if (owner.isReady()) {
            // Cùng chính sách thread không binding như wrapper ngoài: thread mất binding bên trong proxy AOP không được âm thầm bỏ qua spy
            BeanInterceptor.resolveUnbound(owner, beanName, method, closed ? " (its test binding " + binding + " is already closed)" : "", false);
        }
        return BeanInterceptor.invoke(method, target, args);
    }
}
