# Giới thiệu

## Vấn đề

Spring Test cache `ApplicationContext` theo cấu hình của lớp test. `@MockBean`/`@SpyBean` (Spring Boot 3.3) và
`@MockitoBean`/`@MockitoSpyBean` (Spring Framework 6.2+) nằm trong khóa cache đó, nên mỗi tập mock khác nhau là một
context mới, tức là khởi động lại toàn bộ ứng dụng:

```java
class OrderATest { @MockBean ExchangeRateClient fx; }   // context 1
class OrderBTest { @MockBean AuditLog audit; }          // context 2
class OrderCTest { }                                    // context 3 (không mock)
```

Chạy song song còn thêm hai vấn đề:

- Mock của `@MockBean` là singleton của context. Hai test cùng stub một bean ghi đè lẫn nhau, `verify()` đếm cả lời gọi
  của test khác, và Spring reset mock sau mỗi method.
- Test dùng chung database/bảng, static state hoặc cổng cố định va chạm nhau.

## Cách giải quyết

- Các lớp có cùng cấu hình Spring dùng chung MỘT context. Mọi bean do ứng dụng định nghĩa được bọc một lớp wrapper.
- Mỗi test tạo mock/spy riêng (`@PerTestMock`, `@PerTestSpy`); wrapper chuyển lời gọi tới mock/spy của test đang chạy
  trên thread đó, các test khác vẫn thấy bean thật hoặc mock của chúng.
- Mỗi context có database riêng (H2 hoặc Postgres/Testcontainers).
- Guard biến các quy tắc "test song song phải an toàn" thành lỗi build có mã ổn định.

```java
class OrderATest extends MainIT { @PerTestMock @Qualifier("primaryFx") ExchangeRateClient fx; }
class OrderBTest extends MainIT { @PerTestMock AuditLog audit; }
class OrderCTest extends MainIT { }
// cả ba chạy song song trên cùng một context "main"
```

## Nguyên tắc

1. Thư viện chỉ thêm hai thứ: chạy song song và dùng chung context. Ngữ nghĩa Spring test giữ nguyên: spy là
   Mockito spy chuẩn, nằm bên trong proxy AOP (`this.x()` đi qua spy, `@Transactional`/`@Async`/`@Cacheable` vẫn bao quanh).
2. Hành vi khác Spring Test thường, ngoài các khác biệt đã liệt kê ở [Giới hạn đã biết](limitations.md), được coi là lỗi của thư viện.
3. Dùng sai (khai báo mơ hồ, bean không bọc được, thread nền mất binding...) báo lỗi ngay với mã ổn định, xem
   [Xử lý sự cố](troubleshooting.md); thư viện không âm thầm chạy bản thật khi test đã yêu cầu mock.

## Phạm vi API công khai

Người dùng chỉ dùng package `com.hit.ptest`: `@ParallelSpringTest`, `@PerTestMock`/`@PerTestMocks`,
`@PerTestSpy`/`@PerTestSpies`, `@SuppressGuard`, `ParallelTest`, `ParallelTestException`, `UnboundPolicy`; cùng
`com.hit.ptest.guard.AbstractParallelSafetyTest` và `com.hit.ptest.propagation.BindingHeaderInterceptor`. Các lớp public
khác là nội bộ, có thể đổi không báo trước.
