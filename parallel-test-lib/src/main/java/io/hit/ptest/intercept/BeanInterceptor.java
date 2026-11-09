package io.hit.ptest.intercept;

import io.hit.ptest.ParallelTestException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.mockito.Mockito;
import org.mockito.MockSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Đối tượng thay thế bean trong context: một "dispatcher" không trạng thái. Mỗi lời gọi được định tuyến tới
 * mock của test đang gắn với thread, hoặc tới bean thật (đã qua các proxy AOP của Spring; nếu test dùng SPY thì
 * spy nằm bên trong proxy, xem {@link SpyHost}).
 *
 * <p>Wrapper là Mockito mock (inline mock maker) của lớp người dùng, nên tiêm theo lớp cụ thể và lớp final đều
 * được; bean là JDK proxy (vd repository) được thay bằng mock của các interface của nó (cùng cơ chế phát hiện stub nhầm).
 */
public final class BeanInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(BeanInterceptor.class);

    private final InterceptOwner owner;
    private final String beanName;
    private final Object real;
    private final Class<?> mockType;
    private final Class<?>[] extraInterfaces;
    /** Host của spy bên trong proxy AOP; null nếu bean là JDK proxy (spy ủy quyền cho proxy). */
    private final SpyHost host;
    private final boolean mockOnly;
    /**
     * True nếu giữa wrapper ngoài và host của spy không có proxy AOP nào (bean thật chính là {@code host.wrapper}). Khi đó lời gọi tới
     * host chỉ lặp lại đúng quyết định của wrapper ngoài (cùng binding, cùng chính sách thread không binding) nên bỏ qua được một lần
     * dispatch Mockito (~3 us/lời gọi): gọi thẳng spy của test hoặc bản thật.
     */
    private final boolean hostIsDirectTarget;
    public final Object wrapper;

    private BeanInterceptor(InterceptOwner owner, String beanName, Object real, SpyHost host, Class<?> mockOnlyType) {
        this.owner = owner;
        this.beanName = beanName;
        this.real = real;
        this.host = host;
        this.hostIsDirectTarget = host != null && real == host.wrapper;
        this.mockOnly = mockOnlyType != null;
        if (mockOnly) {
            this.mockType = mockOnlyType;
            this.extraInterfaces = new Class<?>[0];
        } else if (AopUtils.isJdkDynamicProxy(real)) {
            Class<?>[] ifaces = AopProxyUtils.proxiedUserInterfaces(real);
            this.mockType = ifaces[0];
            this.extraInterfaces = java.util.Arrays.copyOfRange(ifaces, 1, ifaces.length);
        } else {
            this.mockType = AopProxyUtils.ultimateTargetClass(real);
            this.extraInterfaces = new Class<?>[0];
        }
        // Một cơ chế cho mọi loại bean (kể cả JDK proxy): wrapper là Mockito mock stubOnly nên "when(wrapper...)" luôn bị bắt.
        MockSettings settings = Mockito.withSettings().stubOnly().name("interceptor:" + beanName)
                .stubbingLookupListeners(event -> {
                    if (event.getStubbingFound() != null) {
                        throw stubbedAndReset(java.util.List.of(event.getStubbingFound()));
                    }
                })
                .defaultAnswer(inv -> isToString(inv.getMethod()) ? description() : dispatch(inv.getMethod(), inv.getRawArguments()));
        if (extraInterfaces.length > 0) {
            settings = settings.extraInterfaces(extraInterfaces);
        }
        if (host != null && !AopUtils.isAopProxy(real) && mockType.isInstance(host.rawBean())) {
            // Sao chép giá trị field của bean thật (nằm trong host của spy; `real` chỉ là vỏ rỗng của host) vào wrapper như Mockito.spy: code đọc
            // thẳng field public/package của bean đã tiêm (bean cấu hình, DTO dùng chung) thấy đúng giá trị như trong Spring thường thay vì null.
            // Lời gọi method vẫn được định tuyến. Bean bị proxy AOP không sao chép: proxy của Spring cũng không mang field của bean.
            // GIỚI HẠN (không khắc phục được): bản sao chụp MỘT LẦN lúc tạo wrapper. Field public/package của bean thật bị gán lại SAU lúc đó
            // (vd @PostConstruct muộn, setter chạy sau khi context sẵn sàng) thì đọc thẳng field qua bean đã tiêm vẫn thấy giá trị cũ, khác Spring
            // thường (nơi bean tiêm chính là bean thật). Truy cập qua method (getter) không bị ảnh hưởng vì được định tuyến tới bản thật. Xem LIMITS.md.
            settings = settings.spiedInstance(host.rawBean());
        }
        this.wrapper = Mockito.mock(mockType, settings);
    }

    /**
     * Phát hiện stub trên wrapper dùng chung (thường do {@code when(<field autowired>.x())} hoặc {@code when(handle.x())} ở
     * chế độ REAL thay vì stub handle MOCK/SPY). Mockito không có hook lúc {@code thenReturn} nên có hai điểm bắt, cả hai
     * đều ném {@code BEAN_WRAPPER_STUBBED} kèm vị trí dòng stub:
     * <ul>
     *   <li>cuối test đang thực hiện (afterEach), CHỈ với wrapper mà binding của test đó đã gọi, nên lỗi gán đúng test sai;</li>
     *   <li>lời gọi kế tiếp vào wrapper khớp stub: lời gọi đó nhận lỗi thay vì giá trị stub (lỗi rơi vào test đang gọi, có thể là test
     *   khác: vị trí dòng stub trong thông điệp chỉ ra nơi gây ra).</li>
     * </ul>
     * Test khác KHÔNG bao giờ nhận giá trị stub: lời gọi khớp stub luôn ném lỗi, lời gọi không khớp đi bình thường. Ngay khi phát hiện (ở
     * điểm nào trước), wrapper được {@code Mockito.reset} (giữ nguyên settings và listener) nên lỗi chỉ rơi vào MỘT lời gọi/test và không
     * lan sang mọi test sau.
     */
    void checkNotStubbed() {
        var stubbings = Mockito.mockingDetails(wrapper).getStubbings();
        if (!stubbings.isEmpty()) {
            throw stubbedAndReset(stubbings);
        }
    }

    /** Dựng lỗi từ stub (cần vị trí dòng stub nên dựng TRƯỚC) rồi xóa stub của wrapper. */
    private ParallelTestException.Config stubbedAndReset(java.util.Collection<? extends org.mockito.stubbing.Stubbing> stubbings) {
        ParallelTestException.Config error = stubbedError(stubbings);
        Mockito.reset(this.wrapper);
        return error;
    }

    private ParallelTestException.Config stubbedError(java.util.Collection<? extends org.mockito.stubbing.Stubbing> stubbings) {
        var first = stubbings.iterator().next().getInvocation();
        return new ParallelTestException.Config("BEAN_WRAPPER_STUBBED: the shared wrapper of bean '" + beanName
                + "' was stubbed at " + first.getLocation() + " (call " + first.getMethod().getName()
                + "): probably when(<@Autowired field>.x()) instead of stubbing the @PerTestMock/@PerTestSpy handle of this test. A stub on the wrapper would apply to every test of the context, so it was removed (Mockito.reset) after this error. Detected at the end of the "
                 + "offending test or at the next matching call to the wrapper (which may be in a different test).");
    }

    static BeanInterceptor create(InterceptOwner owner, String beanName, Object real, SpyHost host) {
        return new BeanInterceptor(owner, beanName, real, host, null);
    }

    /**
     * Wrapper cho bean "mock-only" (chưa có bản thật trong context). Bean thật là một mock Mockito {@code stubOnly} không
     * trạng thái (không ghi nhớ lời gọi nên không rò rỉ bộ nhớ và an toàn khi nhiều thread gọi): test không khai handle, thread
     * không binding trước khi nhận chính sách hoặc khởi động context đều nhận giá trị mặc định Mockito, không NPE.
     */
    public static BeanInterceptor createMockOnly(InterceptOwner owner, String beanName, Class<?> type) {
        Object defaults = Mockito.mock(type, Mockito.withSettings().stubOnly().name("mockOnly:" + beanName));
        return new BeanInterceptor(owner, beanName, defaults, null, type);
    }

    /** True nếu bean do thư viện đăng ký vì context không có bản thật (không thể spy). */
    public boolean isMockOnly() {
        return mockOnly;
    }

    /**
     * Tạo target riêng cho một test. MOCK: mock Mockito. SPY: nếu bean có host (bean lớp thường) thì là spy chuẩn
     * ({@code spiedInstance} + {@code CALLS_REAL_METHODS}) đặt bên trong proxy AOP nên {@code this.x()} đi qua spy; nếu bean là
     * JDK proxy thì là mock ủy quyền cho proxy (giống {@code @MockitoSpyBean}). REAL không có target.
     */
    public Object newTarget(InterceptMode mode) {
        MockSettings s = Mockito.withSettings().name(beanName + "@test:" + mode);
        if (extraInterfaces.length > 0) {
            s = s.extraInterfaces(extraInterfaces);
        }
        return switch (mode) {
            case MOCK -> Mockito.mock(mockType, s);
            case SPY -> host != null ? host.newSpy()
                    : Mockito.mock(mockType, s.defaultAnswer(inv -> invoke(inv.getMethod(), real, inv.getRawArguments())));
            case REAL -> throw new IllegalArgumentException("REAL has no target");
        };
    }

    private String description() {
        return "Interceptor[" + beanName + "]";
    }

    /** toString không định tuyến: log/message từ thread không binding không bao giờ ném. */
    private static boolean isToString(Method method) {
        return method.getParameterCount() == 0 && "toString".equals(method.getName());
    }

    private Object dispatch(Method method, Object[] args) throws Throwable {
        TestBinding binding = TestBinding.current();
        boolean closed = binding != null && binding.isClosed();
        if (binding != null && !closed) {
            binding.noteWrapperCall(this);
        }
        Object target = real;
        if (binding != null && !closed) {
            Object t = binding.target(new TestBinding.TargetKey(owner.key(), beanName));
            if (t != null) {
                // spy của host nằm BÊN TRONG proxy AOP: đi qua bean đầy đủ (advice) rồi host chuyển tới spy
                target = !hostIsDirectTarget && host != null && host.isSpy(t) ? real : t;
                InterceptStats.INTERCEPTED.increment();
            } else {
                InterceptStats.REAL.increment();
            }
        } else if (owner.isReady()) {
            resolveUnbound(owner, beanName, method, closed ? " (its test binding " + TestBinding.current() + " is already closed)" : "", true);
        }
        if (hostIsDirectTarget && target == real) {
            target = host.rawBean(); // host sẽ chọn bản thật (không spy): bỏ qua một lần dispatch
        }
        return invoke(method, target, args);
    }

    /**
     * Thread không có binding (hoặc binding đã đóng) gọi bean sau khi context khởi động xong: áp {@code ptest.unbound-policy}. Dùng chung cho
     * wrapper ngoài và cho host của spy ({@link SpyHost}, bên trong proxy AOP), để một thread nền mất binding giữa chừng (vd executor không
     * có TaskDecorator trong advice {@code @Async}) không âm thầm chạy bản thật thay vì spy của test.
     */
    static void resolveUnbound(InterceptOwner owner, String beanName, Method method, String closedNote, boolean countStats) {
        if (countStats) {
            InterceptStats.UNBOUND.increment();
        }
        String thread = Thread.currentThread().getName();
        boolean fail = switch (owner.policy()) {
            case FAIL -> true;
            case GUARDED -> TestBinding.hasEverBeenNonReal(owner.key(), beanName);
            case REAL -> false;
        };
        if (fail) {
            throw new ParallelTestException.UnboundThread("UNBOUND_THREAD: thread '" + thread + "' called "
                    + method.getDeclaringClass().getSimpleName() + "." + method.getName() + " on intercepted bean '"
                    + beanName + "' without a test binding" + closedNote + " (policy=" + owner.policy() + "). Propagate the binding: "
                    + "ParallelTest.wrap(...), a Spring TaskExecutor, or the " + TestBinding.HEADER + " HTTP header.");
        }
        if (owner.firstUnboundWarning(beanName + "@" + thread)) {
            LOG.warn("Unbound thread '{}' called intercepted bean '{}' in context '{}': using the REAL bean (policy={})",
                    thread, beanName, owner.key(), owner.policy());
        }
    }

    static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            ReflectionUtils.makeAccessible(method);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
