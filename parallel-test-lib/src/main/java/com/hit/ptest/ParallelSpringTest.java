package com.hit.ptest;

import com.hit.ptest.junit.ParallelTestExtension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Đánh dấu lớp test Spring Boot chạy song song: khai báo "context profile" có tên, tập bean được bọc wrapper và các schema
 * DB tùy chỉnh. Dùng cùng {@code @SpringBootTest}. Thư viện chỉ lo chạy song song và dùng chung context; test viết như Spring test
 * thường: mock CẤP CLASS bằng {@link PerTestMock} (như {@code @MockBean}, thuộc khóa context), stub theo từng test bằng field
 * {@link PerTestMock}/{@link PerTestSpy} (không đổi khóa context).
 *
 * <pre>{@code
 * @PerTestMocks({@PerTestMock(MongoTemplate.class), @PerTestMock(name = "primaryPricing", classes = PricingClient.class)})
 * @SpringBootTest(classes = AppConfig.class)
 * @ParallelSpringTest(context = "app")
 * abstract class AppBase { }
 *
 * class OrderTest extends AppBase {
 *     @PerTestMock(name = "primaryPricing") PricingClient pricing; // stub riêng từng test
 *     @PerTestSpy OrderService orders;                           // spy riêng từng test trên bean thật
 *     @Autowired CheckoutUsecase usecase;                       // bean thật dùng chung, mock/spy ở trên chỉ có hiệu lực trong test này
 * }
 * }</pre>
 *
 * <p>Hai lớp có cùng cấu hình Spring, cùng giá trị ở đây và cùng tập {@code @PerTestMock} cấp class (xem {@link PerTestMock}; field và
 * tham số không thuộc khóa) dùng chung MỘT ApplicationContext. Cùng {@link #context() tên} nhưng khác
 * {@code wrap}/{@code exclude}/{@code schemas} là lỗi (CONTEXT_NAME_CONFLICT); khác tập mock cấp class chỉ là context khác (PT10 in
 * diff từng bean khác nhau). Bean cần "thật ở lớp này, mock ở lớp kia" thì KHÔNG khai cấp class, chỉ khai field ở lớp cần mock.
 *
 * <p><b>Yêu cầu phiên bản.</b> Mockito &gt;= 5 (mock maker inline); {@code ptest.db.mode=postgres} cần Testcontainers &gt;= 2.0 (artifact
 * {@code testcontainers-postgresql}, lớp {@code org.testcontainers.postgresql.PostgreSQLContainer}; 1.x không được hỗ trợ). WebFlux không có cơ chế
 * truyền binding qua HTTP (chỉ Servlet).
 *
 * <p><b>DB riêng cho mỗi context ({@code ptest.db.mode}: auto|h2|postgres|none; mặc định auto = H2 nếu có trên classpath,
 * KHÔNG bao giờ tự chọn Postgres).</b> Thư viện chỉ cấp DB khi context hỏi {@code spring.datasource.url} và không ai khác
 * đã cung cấp url:
 * <ul>
 *   <li>người dùng đặt url (properties, {@code @DynamicPropertySource}) hoặc khai báo kết nối bằng {@code @ServiceConnection}/
 *   Docker Compose ({@code JdbcConnectionDetails}) là sở hữu CẢ bộ kết nối: thư viện không cấp DB và không trả
 *   username/password/pool;</li>
 *   <li>chỉ đặt username/password mà không đặt url: thư viện vẫn cấp DB và giá trị của người dùng ghi đè giá trị của nó
 *   (thường gây lỗi đăng nhập; hãy đặt cả url);</li>
 *   <li>lỗi khởi động container Postgres được cache: mọi context sau đó thất bại ngay với DB_PROVISION_FAILED, không thử lại.</li>
 * </ul>
 *
 * <p><b>SPY giữ nguyên ngữ nghĩa Mockito.</b> Thư viện chỉ thêm việc chạy song song: mỗi test có spy riêng
 * ({@code spiedInstance} + {@code CALLS_REAL_METHODS}) đặt ở vị trí {@code @MockitoSpyBean} đặt spy, tức BÊN TRONG proxy AOP.
 * Vì vậy {@code this.x()} trong bean đi qua spy, {@code verify/doReturn/doThrow/ArgumentCaptor/InOrder} hoạt động như thường,
 * và advice (@Transactional, @Async, @Cacheable) vẫn bao quanh spy (lời gọi thứ hai vào method @Cacheable được cache trả lời
 * nên spy không thấy, như {@code @MockitoSpyBean}). Khác biệt được chấp nhận và có test: (1) spy sao chép field lúc tạo cho
 * mỗi test (như {@code Mockito.spy}), nên field nguyên thủy/bị gán lại tách khỏi bean thật và khỏi test khác, còn collection
 * hay dependency dùng chung còn trỏ cùng đối tượng; (2) bean là JDK proxy (repository) không có lớp thật: spy ủy quyền cho
 * proxy (giống {@code @MockitoSpyBean}); (3) không hỗ trợ tham chiếu vòng mà Spring lộ sớm bản thật của bean được bọc
 * (Spring báo lỗi "raw version"); (4) thread không binding gọi bean theo {@code ptest.unbound-policy} (mặc định {@code guarded}: chỉ bean từng bị test nào mock/spy mới bị coi là lỗi).
 *
 * <p><b>Giới hạn đã biết.</b> Stub nhầm vào bean dùng chung ({@code when(<field @Autowired>.x())})
 * không thể chặn lúc {@code thenReturn}: phát hiện (BEAN_WRAPPER_STUBBED, kèm vị trí dòng stub) ở CUỐI test đã gọi wrapper
 * đó hoặc ở lời gọi kế tiếp KHỚP stub. Test khác không bao giờ nhận giá trị stub: lời gọi khớp nhận lỗi (một lần), lời gọi không khớp
 * đi bình thường; ngay khi phát hiện wrapper được {@code Mockito.reset} (giữ settings) nên lỗi không lan sang các test sau.
 * {@code toString()} của bean được bọc là {@code Interceptor[<tên bean>]}; wrapper mang giá trị field của bean thật nên đọc thẳng field vẫn đúng.
 * Thread không binding được định tuyến theo {@code ptest.unbound-policy} chỉ sau khi
 * context sẵn sàng; nếu {@code ApplicationReadyEvent} không đến thì test đầu tiên dùng context bật công tắc đó.
 *
 * <p><b>Phiên bản.</b> Spring Boot 3.3+ (Framework 6.1+, JDK 17+) và 4.x; khác biệt theo phiên bản (TaskDecorator của scheduler,
 * {@code @Fallback}, package của {@code JdbcConnectionDetails}) ghi ở GUARD.md mục 7.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(ParallelTestExtension.class)
public @interface ParallelSpringTest {

    /** Tên context profile; dùng để đếm context, đặt tên tài nguyên DB và báo cáo. */
    String context() default "default";

    /**
     * Mẫu tên bean (wildcard kiểu {@code *Client}) được bọc wrapper để test chọn REAL/SPY/MOCK theo từng test. Mặc định
     * {@code "*"}: mọi bean singleton do ứng dụng định nghĩa, kể cả bean ứng dụng tự khai báo bằng {@code @Bean} dù kiểu thuộc
     * framework (RestTemplate, ObjectMapper, executor...); bỏ bean hạ tầng do framework định nghĩa, kiểu hạ tầng (DataSource,
     * EntityManagerFactory, transaction manager, MessageSource, ConversionService), bean {@code @ConfigurationProperties},
     * BeanPostProcessor, FactoryBean, prototype/scope khác, vì một context dùng chung phải sẵn sàng cho BẤT KỲ test nào trong nó mock/spy
     * bean nào mà không biết trước. Muốn mock theo từng test một bean bị bỏ qua thì liệt kê tên tường minh (vd {@code {"*", "exceptionsProvider"}}). Tên không có wildcard là bắt buộc: phải tồn tại và bọc được, nếu không là
     * {@code BEAN_NOT_INTERCEPTABLE}. Thu hẹp bằng cách liệt kê mẫu cụ thể; {@code {}} tắt hẳn việc bọc.
     */
    String[] wrap() default {"*"};

    /** Mẫu tên bean không bao giờ được bọc (thắng {@link #wrap()}); test mock/spy bean này nhận {@code BEAN_NOT_WRAPPED}. */
    String[] exclude() default {};

    /** Schema DB tùy chỉnh phải tạo trước khi chạy ddl của Hibernate (vd {@code audit}). */
    String[] schemas() default {};
}
