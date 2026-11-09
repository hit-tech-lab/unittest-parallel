# Di chuyển từ `@MockBean`/`@SpyBean`

Bản Spring Boot 3 (Spring Framework 6.1): nguồn di chuyển là `@MockBean`/`@SpyBean`. Guard PT03 cũng bắt `@MockitoBean`/`@MockitoSpyBean`
nếu dự án đã dùng chúng.

## Bảng đối chiếu

| Trước | Sau | Khác biệt |
|---|---|---|
| `@MockBean ExchangeRateClient fx;` (field) | `@PerTestMock ExchangeRateClient fx;` | mock riêng từng test, không đổi khóa context |
| `@MockBean(name = "primaryFx") ExchangeRateClient fx;` | `@PerTestMock(name = "primaryFx") ExchangeRateClient fx;` hoặc `@PerTestMock @Qualifier("primaryFx") ...` | chọn bean giữa nhiều bean cùng kiểu |
| `@SpyBean AuditLog audit;` | `@PerTestSpy AuditLog audit;` | spy chuẩn Mockito riêng từng test, nằm trong proxy AOP |
| `@MockBean(Mailer.class)` trên lớp (cổng ngoài chưa có bean) | `@PerTestMock(Ports.Mailer.class)` trên lớp cơ sở | thư viện tạo bean mock cho cả context; vẫn thuộc khóa context |
| `@MockBeans({...})` | `@PerTestMocks({...})` hoặc lặp annotation | |
| `@SpyBean` trên lớp | `@PerTestSpy(AuditLog.class)` cấp class + field handle ở lớp con | không đổi khóa context |
| `@MockBean` tham số/field + `reset` giữa test | không cần | mỗi test một mock mới |
| `@DirtiesContext` | tách `@ParallelSpringTest(context = "...")` riêng | `@DirtiesContext` không hỗ trợ |
| `@SpringBootTest` (không gì khác) | thêm `@ParallelSpringTest(context = "...")` | đủ để chạy song song và dùng chung context |

Trước (Boot 3):

```java
@SpringBootTest
class TransferServiceTest {

    @MockBean @Qualifier("primaryFx") ExchangeRateClient fx;
    @SpyBean AuditLog audit;
    @Autowired TransferService transferService;

    @Test
    void usesTheStubbedRate() {
        when(fx.rate("USD", "EUR")).thenReturn(new BigDecimal("2"));
        // ...
        verify(audit).record(any());
    }
}
```

Sau:

```java
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "main")
abstract class MainIT {
    @Autowired protected TransferService transferService;
}

class TransferServiceIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient fx;
    @PerTestSpy AuditLog audit;

    @Test
    void usesTheStubbedRate() {
        when(fx.rate("USD", "EUR")).thenReturn(new BigDecimal("2"));
        // ...
        verify(audit).record(any());
    }
}
```

## Các bước

1. Tạo một lớp cơ sở có `@SpringBootTest` và `@ParallelSpringTest(context = "...")`; cho các lớp có cùng cấu hình kế thừa nó.
2. Đổi `@MockBean`/`@SpyBean` ở field thành `@PerTestMock`/`@PerTestSpy` (giữ `@Qualifier` hoặc dùng `name`).
3. Với bean mà context test không có cài đặt (cổng ra ngoài), khai `@PerTestMock(X.class)` ở lớp cơ sở; chỉ khai field ở lớp con cần stub.
4. Bean cần thật ở lớp này, mock ở lớp kia thì KHÔNG khai cấp class, chỉ đặt field ở lớp cần mock.
5. Gỡ `@DirtiesContext`, `reset`, và các thủ thuật dọn mock giữa test.
6. Bật song song ([Yêu cầu và cài đặt](requirements-and-setup.md)), chạy, rồi sửa các lỗi guard còn lại (PT03 chỉ đúng chỗ còn sót annotation cũ).

## Khác biệt ngữ nghĩa cần để ý

- Stub và verify trên handle (field `@PerTestMock`/`@PerTestSpy`), không trên field `@Autowired` của cùng bean: field đó là wrapper dùng chung
  (lỗi `BEAN_WRAPPER_STUBBED`).
- Handle được gán trước `@BeforeEach` của lớp test (callback của extension chạy trước method `@BeforeEach`), nên có thể stub trong `@BeforeEach`.
- Test không khai field cho bean khai cấp class nhận mock Mockito dùng chung không trạng thái (trả mặc định, không ghi nhớ lời gọi); muốn verify thì khai field.
- Mock cấp class tạo bean mới không được `@ConditionalOnBean(MockedType)` nhìn thấy (khác `@MockBean`).
- Code chạy trên thread khác của test (server, `@Async`, executor tự tạo) chỉ thấy mock của test khi binding được truyền:
  [Web và thread nền](web-and-background-threads.md).

## Số context

Mỗi tập `@MockBean`/`@SpyBean` khác nhau là một context. Với thư viện, mock theo test ở field không đổi khóa context nên mọi lớp có
cùng cấu hình dùng chung một context; chỉ tập `@PerTestMock` CẤP CLASS (và cấu hình Spring, tên `context`, `wrap`/`exclude`/`schemas`) tạo ra context khác.

Ví dụ trong bộ test mẫu: lớp cơ sở `ProjectBaseIT` khai 10 cổng ra ngoài và 3 gateway cấp class; các lớp con có bean `PricingService` ở ba chế độ
REAL/SPY/MOCK, `ExceptionsProvider` thật hoặc mock... đều dùng chung một context `project`. `ProjectExtraMockOnlyIT` khai thêm một mock cấp class nên
có context thứ hai `project-extra`.

Chưa có số đo trước/sau trên cùng một bộ test. Số đo có sẵn về chi phí mỗi context (dựng 5 context trên H2 mất 4,4 đến 5,2 giây,
tức khoảng 1 giây mỗi context, các context dựng gần như tuần tự; Postgres 6,8 đến 8,3 giây) nằm ở [Số đo](benchmarks.md), kèm điều kiện đo.
