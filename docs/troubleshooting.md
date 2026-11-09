# Xử lý sự cố

Lỗi cấu hình/sử dụng là `ParallelTestException.Config` (hoặc lớp con) với thông điệp bắt đầu bằng mã ổn định, ném ở `beforeEach` của
test, hoặc lúc dựng context với `MOCK_ONLY_CONFLICT`/`BEAN_AMBIGUOUS` của khai báo cấp class. Lỗi guard là `PTxx`, xem [Guard](guard.md).
Bảng đầy đủ: mục 6 của [GUARD.md](../parallel-test-lib/GUARD.md).

## Khai báo mock/spy

| Mã | Nguyên nhân | Cách sửa |
|---|---|---|
| `BEAN_AMBIGUOUS` | nhiều bean cùng kiểu, không `@Primary`, không `name`/`@Qualifier`, tên field không trùng tên bean (thông điệp liệt kê ứng viên); hoặc hai bean `@Primary` | thêm `name = "<tên bean>"` (bắt buộc trên tham số) hoặc `@Qualifier`, hoặc đặt tên field trùng tên bean; cấp class chỉ còn `@Primary` hoặc `name` |
| `BEAN_NOT_FOUND` | field/tham số `@PerTestMock` trỏ tới kiểu chưa có bean | khai cấp class `@PerTestMock(MyBean.class)` ở lớp mang `@ParallelSpringTest` hoặc lớp cha để thư viện tạo |
| `SPY_NO_BEAN` | `@PerTestSpy` trên bean không tồn tại, hoặc đã bị `@PerTestMock` cấp class thay/tạo (thông điệp nói "REPLACED"/"CREATED") | bỏ spy, hoặc bỏ khai báo mock cấp class (mock ở lớp cần bằng field) |
| `MODE_CONFLICT` | cùng bean vừa mock vừa spy ở field/tham số trong cùng phân cấp (kể cả `@Nested`) | giữ một chế độ; tham số method ghi đè field cùng bean chỉ cho method đó |
| `MOCK_ONLY_CONFLICT` | cùng `name` khai cấp class với hai kiểu; `name` đã dùng cho bean khác kiểu; hai khai báo cùng thay một bean; sau refresh có thêm bean cùng kiểu với bean mock được tạo | đổi `name`, khai một lần, hoặc đặt `name` đúng bean cần thay |
| `BEAN_NOT_WRAPPED` | bean có nhưng không được bọc (`exclude`, `@ConfigurationProperties`, bean hạ tầng, prototype/scope, `wrap` thu hẹp); thông điệp nêu lý do | thêm tên vào `wrap`, bỏ khỏi `exclude`, mock cấp class, hoặc không mock bean đó |
| `BEAN_NOT_INTERCEPTABLE` | tên tường minh trong `wrap` không tồn tại hoặc không bọc được (`BeanPostProcessor`, prototype) | sửa `wrap` |
| `INVALID_DECLARATION` | field `static`/`final`; cả hai annotation trên cùng field; cấp class không có kiểu hoặc `name` đi với nhiều kiểu; `value`/`classes` đặt ở field/tham số | sửa theo thông điệp |
| `MOCK_REPLACE_UNSUPPORTED` | bean cần thay chỉ là singleton đăng ký tay, không có bean definition | tạo bean bằng `@Bean`/`@Component` hoặc không khai cấp class |
| `BEAN_WRAPPER_STUBBED` | `when(<field @Autowired>.x())` stub wrapper dùng chung; thông điệp có dòng stub | khai field `@PerTestMock`/`@PerTestSpy` rồi stub handle đó |

Ví dụ `BEAN_AMBIGUOUS`: ba bean `PaymentGateway` (`gatewayA/B/C`):

```java
@PerTestMock PaymentGateway anyGateway;                  // BEAN_AMBIGUOUS
@PerTestMock(name = "gatewayA") PaymentGateway gatewayA; // đúng
```

## Context, thread và môi trường

| Mã / dấu hiệu | Nguyên nhân | Cách sửa |
|---|---|---|
| `CONTEXT_NAME_CONFLICT` | cùng tên `@ParallelSpringTest(context=...)` nhưng khác `wrap`/`exclude`/`schemas` (thường gõ nhầm tên) | một khai báo cho mỗi tên, hoặc đặt tên khác |
| `CONTEXT_LIMIT_EXCEEDED` (PT10) | vượt `ptest.max-contexts` (mặc định 8); thông điệp có lớp tạo context, context gần nhất và diff | cho lớp dùng lại cấu hình có sẵn, hoặc tăng `ptest.max-contexts` sau review (phải <= `spring.test.context.cache.maxSize`) |
| `CONFIG: ptest.max-contexts=N exceeds spring.test.context.cache.maxSize=M` | ngưỡng lớn hơn cache của Spring | tăng `spring.test.context.cache.maxSize` (mặc định 32) hoặc giảm `ptest.max-contexts` |
| `CONTEXT_NOT_MANAGED` | `ApplicationContext` không do lớp `@ParallelSpringTest` tạo (hoặc đã đóng) mà được hỏi binding/wrapper | chỉ dùng `ParallelTest.contextKey/contextName` với context của lớp `@ParallelSpringTest` |
| `UNBOUND_THREAD` | thread không binding gọi bean được bọc khi `ptest.unbound-policy=fail`, hoặc `guarded` và bean từng bị test nào mock/spy | `ParallelTest.wrap(...)`, executor Spring có `TaskDecorator`, header `X-PTest-Binding`: [Web và thread nền](web-and-background-threads.md) |
| `BINDING_EXPIRED` | request HTTP mang header binding của test đã kết thúc (`ServletException` bọc `Config`) | chờ request xong trước khi test kết thúc |
| `DIRTIES_CONTEXT_UNSUPPORTED` | `@DirtiesContext` trên lớp/method test | tách `@ParallelSpringTest(context=...)` riêng; `ptest.unsupported=warn` để chỉ cảnh báo |
| `TRANSACTIONAL_RANDOM_PORT_UNSUPPORTED` | `@Transactional` với `RANDOM_PORT`/`DEFINED_PORT` | dữ liệu riêng từng test và dọn tường minh; `ptest.unsupported=warn` |
| `CONFIG` ... `PER_CLASS` ... may run concurrently | field `@PerTestMock`/`@PerTestSpy` trên lớp `PER_CLASS` mà method chạy đồng thời | `PER_METHOD`, `@Execution(SAME_THREAD)`, `@Isolated` hoặc `@ResourceLock` |
| `CONFIG` ... needs Mockito >= 5 | không mock được lớp `final` (Mockito 4 hoặc mock maker subclass) | dùng Mockito 5+ hoặc cấu hình mock maker inline |
| `CONFIG` ... unknown ptest.unbound-policy / ptest.db.mode / ptest.guard.PTxx | giá trị sai | dùng `fail|guarded|real`, `auto|h2|postgres|none`, `fail|warn|off` |
| `CONFIG` ... `@SuppressGuard` | `reason` rỗng | ghi lý do |
| `DB_PROVISION_FAILED` | không cấp được DB (không khởi động được container Postgres, `CREATE DATABASE/SCHEMA` lỗi); lỗi container được cache | sửa Docker/image/quyền, hoặc `ptest.db.mode=none` |
| `CONFIG: ptest.db.mode=postgres needs Testcontainers >= 2.0` | thiếu `testcontainers-postgresql` 2.0+ hoặc driver `postgresql` | thêm dependency (Testcontainers 1.x không hỗ trợ) |

## Cảnh báo trong log

| Log | Ý nghĩa | Xử lý |
|---|---|---|
| WARN `Executor bean '<tên>' ... has no setTaskDecorator` | binding không truyền tới thread của executor | `ParallelTest.wrap` khi nộp việc |
| WARN `ptest.max-contexts (..) x pool size (..) = .. may exceed Postgres max_connections=..` | trường hợp xấu nhất vượt `max_connections` | tăng `ptest.db.postgres.max-connections` hoặc giảm pool/số context |
| WARN `Mockito is not loaded with -javaagent` | Mockito tự attach động; JDK 21+ cảnh báo | nạp `-javaagent` nếu jar Mockito hỗ trợ ([Yêu cầu và cài đặt](requirements-and-setup.md)) |
| INFO `spring.datasource.url is provided by ...; ptest does NOT provision a database` | url do nguồn khác cung cấp; các context cùng url dùng chung một DB | chủ ý thì bỏ qua; không thì xóa url để thư viện cấp DB |
| WARN `ptest.db.postgres.image=... is IGNORED` | container singleton đã khởi động với image khác | đặt cùng image cho mọi context |
| WARN `Unbound thread '..' called intercepted bean '..'` | thread không binding gọi bean thật: policy `real`, hoặc `guarded` với bean chưa từng bị mock/spy | truyền binding nếu muốn thấy mock/spy của test |

## Khác

- `TESTS_DID_NOT_RUN_CONCURRENTLY`: không phải mã của thư viện; do helper `Rendezvous` của bộ test mẫu ném khi các test hẹn nhau không chạy cùng lúc
  (parallelism nhỏ hơn số bên, hoặc `@TestMethodOrder`/`@Isolated` làm chúng tuần tự). Xem [Giới hạn đã biết](limitations.md).
- Báo cáo surefire sai tên lớp/số test: surefire 3.2.x với JUnit song song; dùng 3.5.x. Bằng chứng đúng nằm ở `target/ptest-reports/*.properties` (cần `ptest.report.dir`).
