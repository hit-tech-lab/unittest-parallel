# Web và thread nền

Mock/spy của test gắn với thread của test (qua `ThreadLocal`). Code chạy trên thread khác (server HTTP, `@Async`, executor
tự tạo) chỉ thấy mock/spy của test khi binding được truyền sang thread đó. Thư viện truyền tự động ở ba chỗ (executor
của Spring, header HTTP, `ParallelTest.wrap`); phần còn lại do chính sách thread không binding quyết định.

Các đoạn code là thân lớp/method của test, dùng lớp `demo.core.*` và `MainIT` như ở [Khai báo mock/spy](mock-spy-declaration.md);
import lấy từ `Assertions`, `Mockito`, `ArgumentMatchers`, `java.util.concurrent` và các lớp được nhắc tên.

## Test web: `MOCK` và `RANDOM_PORT`

`webEnvironment = MOCK`: không có server, code chạy trên chính thread test nên không cần làm gì thêm.

```java
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ParallelSpringTest(context = "web-mock")
class WebMockEnvIT {

    @Autowired RateController controller;
    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void routesToThisTestsMock() {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("5.5"));

        assertThat(controller.rate("USD", "EUR", "primary").getBody()).isEqualByComparingTo("5.5");
    }
}
```

`webEnvironment = RANDOM_PORT`: request chạy trên thread của server. Thư viện đăng ký `BindingFilter` (servlet filter, ưu tiên
cao nhất); filter đọc header `X-PTest-Binding` và gắn binding của test cho thread server trong lúc xử lý request. Client của test
phải gắn header đó. Với `RestClient` dùng `BindingHeaderInterceptor`:

```java
abstract class HttpBase extends MainIT {

    @Value("${local.server.port}") int port;

    protected RestClient withBinding() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestInterceptor(new BindingHeaderInterceptor())   // com.hit.ptest.propagation
                .build();
    }
}

class HttpMockIT extends HttpBase {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void serverThreadRoutesToThisTestsMock() {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("7.7"));

        BigDecimal rate = withBinding().get().uri("/rates/USD/EUR").retrieve().body(BigDecimal.class);

        assertThat(rate).isEqualByComparingTo("7.7");
        verify(mock).rate("USD", "EUR");
    }
}
```

Client khác (`RestTemplate`, `WebClient`...) tự gắn header bằng `ParallelTest`:

```java
String name = ParallelTest.bindingHeaderName();   // "X-PTest-Binding"
String id = ParallelTest.currentBindingId();      // null nếu thread không có binding
RestClient client = RestClient.builder().defaultHeader(name, id).build();
```

- Request KHÔNG có header: thread server không có binding, áp chính sách `ptest.unbound-policy` (bên dưới). Với bean đang
  bị test nào đó mock/spy, request nhận lỗi `UNBOUND_THREAD` (HTTP 500, nội dung nêu bean và mã lỗi).
- Header của test đã kết thúc: `BINDING_EXPIRED` (đừng để request còn chạy khi test đã xong).
- `@Transactional` trên test với `RANDOM_PORT` không rollback được vì request chạy ngoài transaction của test; thư viện
  báo `TRANSACTIONAL_RANDOM_PORT_UNSUPPORTED` (`ptest.unsupported=warn` để chỉ cảnh báo): dùng dữ liệu riêng từng test và dọn tường minh.
- `DEFINED_PORT` bị guard PT08 chặn: dùng `RANDOM_PORT` và `@LocalServerPort`.
- Hai kiểu môi trường chạy được cùng lúc, mỗi kiểu một context.

## `@Async`, executor của Spring, `TaskDecorator`

`TaskDecoratorInstaller` (`BeanPostProcessor` tự đăng ký) gắn `BindingTaskDecorator` cho executor Spring có
`setTaskDecorator`, và bọc (compose) decorator sẵn có của bạn thay vì đè. Nhờ đó `@Async` chạy trên executor của Spring vẫn thấy
mock/spy của test đã gọi:

```java
class NotifyMockIT extends MainIT {

    @Autowired NotificationService notificationService;
    @PerTestMock AuditLog audit;

    @Test
    void asyncThreadWritesToThisTestsMock() throws Exception {
        String tag = "t-" + UUID.randomUUID();

        String worker = notificationService.notifyAsync(tag).get(10, TimeUnit.SECONDS);   // chạy trên thread executor

        assertThat(worker).isNotEqualTo(Thread.currentThread().getName());
        verify(audit, timeout(5000)).record(argThat(e -> e.detail().equals(tag)));
    }
}
```

Đã kiểm tra tác vụ thường và tác vụ lên lịch một lần của `ThreadPoolTaskExecutor`, `SimpleAsyncTaskExecutor`,
`SimpleAsyncTaskScheduler`, `ConcurrentTaskExecutor`, `ConcurrentTaskScheduler`, `ThreadPoolTaskScheduler`. Tác vụ lặp (fixed-rate/cron) chưa kiểm tra.

Cảnh báo (log WARN, một lần mỗi bean) khi binding KHÔNG được truyền:

| Cảnh báo | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Executor bean '<tên>' ... has no setTaskDecorator` | executor không có `setTaskDecorator` (vd `Executors.newFixedThreadPool` khai báo thành bean, executor tự viết; `ThreadPoolTaskScheduler` trên Spring 6.1) | bọc lúc nộp việc bằng `ParallelTest.wrap` (bên dưới) |
| `Cannot read the current TaskDecorator of '<tên>'` | có setter nhưng không đọc lại được decorator hiện tại; thư viện không đụng vào để khỏi đè decorator của bạn | như trên |

## Chính sách thread không binding: `ptest.unbound-policy`

Áp dụng khi một thread không có binding (hoặc binding đã đóng) gọi bean được bọc SAU khi context khởi động xong.

| Giá trị | Hành vi |
|---|---|
| `guarded` (mặc định) | gọi bean thật cho đến khi BẤT KỲ test nào trong context từng khai mock/spy bean đó; từ đó trở đi luôn ném `UNBOUND_THREAD` (kể cả khi test đó đã kết thúc) |
| `fail` | luôn ném `UnboundThread`, mọi bean được bọc; chặt nhất, dễ vướng thread nền vì mặc định bọc rộng |
| `real` | luôn gọi bean thật (WARN một lần); mock của test không thấy lời gọi đó |

Đặt theo context:

```java
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "ptest.unbound-policy=fail")
@ParallelSpringTest(context = "policy-fail")
class UnboundThreadFailPolicyIT {

    @Autowired @Qualifier("primaryFx") ExchangeRateClient primaryFx;

    @Test
    void unboundThreadAlwaysFails() {
        assertThatThrownBy(() -> CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR")).get())
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(ParallelTestException.UnboundThread.class);
        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("1.10");   // thread test có binding
    }
}
```

`guarded` có phụ thuộc thời điểm ở chính lần gọi đầu (trước hay sau test đầu tiên khai báo bean). Cần kết quả xác định
thì dùng `fail` và truyền binding đầy đủ. Chính sách chỉ áp sau khi context sẵn sàng (`ApplicationReadyEvent`);
trước đó, kể cả `@PostConstruct`, bean là bản thật. Nếu sự kiện đó không đến thì test đầu tiên dùng context bật công tắc.

## `ParallelTest.wrap` cho thread tự tạo

`ParallelTest.wrap` trả về task/executor chạy với binding của thread NỘP việc (không có binding thì trả nguyên task).

```java
@Test
void wrappedThreadIsRoutedToThisTestsMock() throws Exception {
    when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("6.6"));
    ExecutorService pool = Executors.newFixedThreadPool(1);   // executor tự tạo
    try {
        Callable<BigDecimal> call = () -> primaryFx.rate("USD", "EUR");
        assertThat(pool.submit(ParallelTest.wrap(call)).get()).isEqualByComparingTo("6.6");   // wrap(Callable)

        assertThat(CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR"), ParallelTest.wrap(pool)).get())
                .isEqualByComparingTo("6.6");                                                   // wrap(Executor)
    } finally {
        pool.shutdownNow();
    }
}
```

Không bọc thì thread đó không có binding và `primaryFx.rate` ném `UNBOUND_THREAD` (bean đang bị mock):

```java
Thread raw = new Thread(() -> primaryFx.rate("USD", "EUR"));   // UnboundThread: không binding
CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR"));   // ForkJoin common pool cũng không binding
```

Các hàm tiện ích khác của `ParallelTest`: `hasBinding()`, `currentBindingId()`, `bindingHeaderName()`,
`contextName(ApplicationContext)`, `contextKey(ApplicationContext)`. `wrap` có ba quá tải (`Runnable`, `Callable`,
`Executor`); với lambda, khai báo biến kiểu `Runnable` hoặc `Callable` trước khi truyền để chọn đúng quá tải.
