package com.hit.ptest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mock Mockito RIÊNG của từng test cho một bean của context. Có ba cách đặt, giống {@code @MockBean}/{@code @Autowired}:
 *
 * <ol>
 *   <li><b>Cấp class</b> (khai báo, cùng cú pháp {@code @MockBean}): {@code @PerTestMock(A.class)}, nhiều bean bằng
 *   {@code @PerTestMocks({...})} hoặc lặp trực tiếp {@code @PerTestMock(A.class) @PerTestMock(B.class)}.
 *   Bean đã có trong context thì ĐỊNH NGHĨA bị THAY bằng bean mock (bean thật không bao giờ được tạo, nên
 *   {@code @PostConstruct}/kết nối ngoài của nó không chạy); chưa có thì thư viện TẠO bean mock. Khai báo cấp class thuộc
 *   KHÓA CONTEXT: bean đó là mock cho CẢ context, không còn bản thật.</li>
 *   <li><b>Field</b> của lớp test: handle để stub/verify mock riêng của test (mỗi test một mock mới, chỉ test đó và các thread nó
 *   giao việc thấy; test khác cùng context vẫn gọi bean thật hoặc mock của chúng). Field không đổi khóa context.</li>
 *   <li><b>Tham số</b> của method test: ghi đè field cùng bean cho riêng method đó.</li>
 * </ol>
 *
 * <p><b>Ví dụ</b>
 * <pre>{@code
 * @PerTestMocks({
 *     @PerTestMock(PaymentGateway.class),                                                      // theo kiểu
 *     @PerTestMock({MongoTemplate.class, KafkaProducer.class}),                               // nhiều kiểu một dòng
 *     @PerTestMock(name = "backupPricing", classes = PricingClient.class),                 // theo tên + kiểu
 *     @PerTestMock(name = "primaryPricing", classes = PricingClient.class),
 * })
 * @ParallelSpringTest
 * abstract class ServiceTestBase { }
 *
 * class OrderTest extends ServiceTestBase {
 *     @PerTestMock PaymentGateway paymentGateway;                      // handle: stub được, riêng từng test
 *     @PerTestMock(name = "primaryPricing") PricingClient pricing; // chọn trong ba bean cùng kiểu
 *     @Autowired OrderService service;                               // bean thật, dùng mock ở trên
 * }
 * }</pre>
 *
 * <p><b>Chọn bean.</b> Cấp class: {@code value}/{@code classes} là kiểu; {@code name} (chỉ với đúng một kiểu) là tên bean (hoặc
 * {@code @Qualifier} của bean đã có) để chọn giữa nhiều bean cùng kiểu, và là tên của bean được TẠO nếu chưa có (không có
 * {@code name}: tên lớp viết thường đầu, hoặc tên lớp đầy đủ kèm {@code #n} nếu tên đó đã dùng cho bean khác kiểu). Không có {@code name} mà context có nhiều bean cùng kiểu thì theo
 * {@code @Primary}, nếu không {@code BEAN_AMBIGUOUS}. Field/tham số: kiểu của field/tham số, lọc bằng {@link #name()} hoặc
 * {@code @Qualifier}, rồi {@code @Primary}, rồi tên field; không xác định được là {@code BEAN_AMBIGUOUS}/{@code BEAN_NOT_FOUND}.
 * Với tham số, {@code @Qualifier} bị SpringExtension coi là tham số tiêm và JUnit báo hai ParameterResolver cạnh tranh, nên dùng
 * {@link #name()}.
 *
 * <p><b>Quy tắc "bean thật ở lớp này, mock ở lớp kia".</b> Bean khai cấp class luôn là mock cho cả context. Bean cần thật ở một số lớp
 * và mock ở lớp khác (vd {@code ErrorMessages}, {@code PricingProperties}) thì KHÔNG khai cấp class: chỉ đặt field
 * {@code @PerTestMock} ở lớp con cần mock; các lớp còn lại không khai field và gọi bean thật, cùng MỘT context. Một lớp con khai
 * {@link PerTestSpy} (field hoặc cấp class) trên bean đã bị khai cấp class là lỗi cấu hình {@code SPY_NO_BEAN}: không còn bản thật.
 * Muốn spy thì không khai bean đó ở cấp class.
 *
 * <p><b>Lỗi cấu hình rõ ràng</b> (mã ổn định): {@code INVALID_DECLARATION} (cấp class không có kiểu, {@code name} đi với nhiều
 * kiểu, {@code value}/{@code classes} đặt ở field/tham số), {@code MOCK_ONLY_CONFLICT} (cùng tên khác kiểu, tên đã dùng cho bean khác kiểu, hai
 * khai báo cùng thay một bean), {@code MODE_CONFLICT} (cùng bean vừa mock vừa spy), {@code BEAN_AMBIGUOUS} (nhiều bean cùng kiểu
 * không {@code @Primary}/tên), {@code BEAN_NOT_FOUND} (field/tham số trỏ tới bean không có: khai cấp class để thư viện tạo).
 *
 * <p><b>Khóa context.</b> Tập khai báo cấp class là một phần khóa context: hai lớp khai khác tập mock là hai context khác nhau
 * (thông điệp CONTEXT_LIMIT_EXCEEDED in diff chính xác bean nào khác); cùng tập (kể cả khai ở lớp cha) dùng chung một context. Field
 * và tham số KHÔNG vào khóa.
 *
 * <p><b>Giới hạn.</b> Mock cấp class được đăng ký ở BeanFactoryPostProcessor lập trình, sau {@code ConfigurationClassPostProcessor}: bean mock được
 * THAY định nghĩa có sẵn thì điều kiện vẫn thấy bean; bean mock TẠO MỚI thì {@code @ConditionalOnBean(MockedType)} KHÔNG thấy (khác {@code @MockBean}).
 * Lớp {@code @Nested} tự khai thêm mock cấp class có tập khai báo khác lớp bao ngoài nên chạy ở context KHÁC lớp bao ngoài.
 *
 * <p><b>Vòng đời.</b> Mock của field/tham số tạo trước mỗi test. {@code TestInstance.Lifecycle.PER_CLASS} được hỗ trợ khi các method của lớp
 * không chạy đồng thời ({@code @Execution(SAME_THREAD)}, {@code @Isolated}, {@code @ResourceLock}): mỗi method nhận handle mới; nếu method có thể
 * chạy đồng thời thì là lỗi cấu hình {@code CONFIG}. Test không khai field cho bean khai cấp class
 * nhận một mock Mockito dùng chung không trạng thái (trả mặc định null/0/rỗng, không ghi nhớ lời gọi). Một bean không thể vừa
 * {@code @PerTestMock} vừa {@link PerTestSpy} trong cùng phân cấp ({@code MODE_CONFLICT}).
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(PerTestMocks.class)
public @interface PerTestMock {

    /** Kiểu bean cần mock (CHỈ cấp class). Cùng nghĩa với {@link #classes()}; hai thuộc tính được gộp. */
    Class<?>[] value() default {};

    /** Kiểu bean cần mock (CHỈ cấp class), như {@code @MockBean(classes=...)}. */
    Class<?>[] classes() default {};

    /**
     * Cấp class: tên bean (đi với đúng một kiểu). Field/tham số: tên bean (hoặc {@code @Qualifier}) cần chọn, thay cho
     * {@code @Qualifier} và ưu tiên hơn; dùng cho THAM SỐ method test vì {@code @Qualifier} ở đó gây xung đột ParameterResolver.
     */
    String name() default "";
}
