# unittest-parallel v2: `parallel-test-guard`

Chạy integration test Spring Boot **song song** và **ép buộc** các quy tắc an toàn bằng code (build fail
và chỉ đúng class, dòng code, lý do, cách sửa), thay vì chỉ ghi chú.
v1 (hướng dẫn + demo, chưa có lib) nằm ở nhánh `v1` / file `README-v1.md`.

```
parallel-test-guard/   library (scope test), tự kích hoạt qua spring.factories + ServiceLoader
demo-app/              app mẫu dùng library: unit test, IT, negative controls
scripts/               verify-parallel.sh: tự kiểm chứng toàn bộ
```

## 1. Nghiên cứu: cách nào tối ưu?

### Kiểm chứng từ source code (Spring Test 7.0.10, JUnit Platform 6.0.3, Surefire 3.5.6)

| Sự thật | Trong code | Hệ quả |
|---|---|---|
| Context cache tối đa 32, đổi bằng `spring.test.context.cache.maxSize` | `ContextCache.DEFAULT_MAX_CONTEXT_CACHE_SIZE = 32` | Vượt 32 thì context bị loại khỏi cache **và đóng**, kể cả khi test khác còn đang dùng. Vì vậy cần **giới hạn số context (PT10)** |
| Việc tải context được khoá đồng bộ trên cả cache | `synchronized (this.contextCache)` trong `DefaultCacheAwareContextLoaderDelegate` | Càng nhiều loại context thì các class càng phải xếp hàng chờ nhau khởi động. Vì vậy cần **một context dùng chung** |
| `TestContextManager` giữ `TestContext` theo từng thread | `ThreadLocal<TestContext>` | Chạy method song song trong cùng một class là an toàn |
| `@MockitoBean` bị reset sau **mỗi** test method | `MockitoResetTestExecutionListener.afterTestMethod` | Reset luôn mock mà test khác đang dùng. Vì vậy **cấm (PT03)**, thay bằng `@PerTestMock` |
| `@ResourceLock` ép **mọi method con** chạy `SAME_THREAD` | `NodeTreeWalker.forceDescendantExecutionMode(SAME_THREAD)` | Dùng lock thì mất song song, không mở rộng được |
| Khi `forkCount > 1`, Surefire chạy **mỗi class một lần `launcher.execute`** | `JUnitPlatformProvider.execute` (nhánh không đọc trước) | Trong một fork các class không bao giờ chạy cùng lúc |
| JUnit `fixed`: mặc định `saturate=true`, `max-pool-size = parallelism + 256` | `DefaultParallelExecutionConfigurationStrategy` | Khi bị chặn, số thread thực tế có thể vượt `parallelism` (đo được 17 thread khi đặt 16) |

### Đo đạc: 200 IT (40 class × 5 test), máy 4 CPU

| Chiến lược | Chỉ CPU (H2) | Độ trễ I/O 50ms/test |
|---|---|---|
| Tuần tự, 1 JVM | 13.6s | 22.1s |
| Tuần tự, 4 fork (cách truyền thống) | **30.0s** | 23.4s |
| Song song, 1 JVM, 4 thread | 10.8s | 12.7s |
| Song song, 1 JVM, 8 thread | 10.5s | 11.3s |
| Song song, 1 JVM, 16 thread | 10.6s | **10.7s** |
| Song song, 2 fork × 4 thread | 14.6s | 15.9s |

(Thời gian đã gồm khoảng 9s cố định cho Maven và khởi động một context.)
Với `@MockitoBean` + `@ResourceLock` (20 class): 30.0s, so với 10.5s khi dùng `@PerTestMock` (xem v1).

### Kết luận

1. **Một JVM, class + method chạy song song, một context dùng chung.** Fork làm chậm Spring IT,
   vì mỗi fork phải khởi động lại context và chỉ nhận từng class một.
2. Số thread: CPU-bound thì khoảng bằng số core là đủ; IT có I/O (DB thật, HTTP) thì 2–4× số core.
   Connection pool phải lớn hơn hoặc bằng số thread (PT11).
3. **Không `@MockitoBean`, không `@DirtiesContext`, không đổi cấu hình ở class con.** Mỗi test có mock riêng
   (`@PerTestMock`) và dữ liệu riêng (`TestData`), và chỉ assert trên dữ liệu của chính nó.
4. Tất cả được **ép buộc bằng library** bên dưới, không dựa vào việc mỗi người phải tự nhớ.

## 2. Dùng library trong project (3 bước)

**① Dependency** (scope test, không cần cấu hình gì thêm):
```xml
<dependency>
    <groupId>com.hittech</groupId>
    <artifactId>parallel-test-guard</artifactId>
    <version>2.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

**② Một base class cho mỗi loại test**, và **mọi** IT phải kế thừa nó:
```java
@SharedContextBase
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RoutedMocks(ExchangeRateClient.class)            // các bean cần mock
public abstract class AbstractIntegrationTest {
    @PerTestMock protected ExchangeRateClient exchangeRates;   // mock mới cho mỗi test
    protected RestClient http() {                               // mock đi theo request HTTP sang server
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .requestInterceptor(PerTestMockHttp.propagation()).build();
    }
}
```
Trong test vẫn dùng `when(...)` và `verify(...)` của Mockito như bình thường. Với executor hoặc `CompletableFuture`, bọc task bằng
`PerTestMocks.wrap(...)`, `wrapCallable(...)` hoặc `wrapSupplier(...)`.

**③ Một dòng để quét tĩnh** (chạy cùng unit test, fail trước khi chạy bất kỳ IT nào):
```java
class ParallelSafetyTest extends AbstractParallelSafetyTest {}
```

Cấu hình tuỳ chọn trong `src/test/resources/parallel-test-guard.properties` (mỗi key ghi đè được bằng `-Dparalleltest.<key>`):
```properties
max-contexts=2
forbidden-calls=com.example.parallel.audit.AuditLog#clear()
check-pool-size=true
sql-guard=true
enabled=true
```

## 3. Các rule bị ép buộc

| ID | Vi phạm | Bắt ở đâu | Ngoại lệ có kiểm soát |
|---|---|---|---|
| PT01 | Spring test không kế thừa `@SharedContextBase` | quét tĩnh + runtime | `@IsolatedContext(reason="...")` |
| PT02 | Class con đổi cấu hình (`@TestPropertySource`, `@Import`, `@ActiveProfiles`, `@MockitoBean`, `@DynamicPropertySource`, nested `@TestConfiguration`, `@AutoConfigure*`, ...) | quét tĩnh + runtime | `@IsolatedContext` |
| PT03 | `@MockitoBean` / `@MockitoSpyBean` | quét tĩnh + runtime | JUnit `@Isolated` / `@ResourceLock` |
| PT04 | `@DirtiesContext` | quét tĩnh + runtime | JUnit `@Isolated` |
| PT05 | `@PerTestMock` có type không nằm trong `@RoutedMocks` | quét tĩnh + runtime | không có |
| PT06 | Field `static` không `final` trong test class | quét tĩnh + runtime | JUnit `@Isolated` |
| PT07 | Gọi `deleteAll()`, `count()`, `findAll()`, `deleteAllInBatch()`, `JdbcTestUtils.*`, hoặc method trong `forbidden-calls`. Quét **bytecode**, kể cả lambda, class lồng nhau và class helper | quét tĩnh + runtime | JUnit `@Isolated` |
| PT08 | `webEnvironment = DEFINED_PORT` | quét tĩnh + runtime | không có |
| PT09 | `@TestExecutionListeners` thay thế listener mặc định (làm mất guard) | quét tĩnh | `mergeMode = MERGE_WITH_DEFAULTS` |
| PT10 | Số context vượt `max-contexts`. Thông báo in **diff cấu hình** so với context gần giống nhất | runtime | tăng `max-contexts` (cần review) |
| PT11 | Connection pool nhỏ hơn số thread JUnit | runtime | `check-pool-size=false` |
| PT12 | SQL `DELETE` không có `WHERE`, hoặc `TRUNCATE`, chạy khi test đang chạy song song. Chặn ở **tầng JDBC**, nên bắt được JPA, `JdbcTemplate`, `@Sql` script và JDBC thuần. SQL chạy lúc context khởi động (seed, migration) không bị chặn | runtime | JUnit `@Isolated` (lúc đó class chạy một mình) |
| PT13 | `@TestInstance(PER_CLASS)` (hoặc mặc định `per_class`) trong khi method chạy song song: mọi method dùng chung một instance, nên field và `@PerTestMock` bị ghi đè lẫn nhau | quét tĩnh + runtime | `@Execution(SAME_THREAD)`, `@Isolated`, `@ResourceLock` |
| PT14 | Bean thật của type có trong `@RoutedMocks` vẫn được inject ở đâu đó (inject bằng class cụ thể, `@Qualifier` hoặc dạng collection), khiến router bị bỏ qua mà không có lỗi nào | runtime (dò đồ thị dependency của bean) | inject bằng interface, hoặc thêm class đó vào `@RoutedMocks` |

`enabled=false` chỉ tắt việc kiểm tra rule. Mock vẫn được định tuyến như bình thường, nên hành vi của test không thay đổi.
Vi phạm xảy ra trên thread khác (thread của server, executor) được **gán lại cho đúng test** và làm test đó fail
với thông báo của guard, kể cả khi app đã nuốt lỗi hoặc test chỉ thấy HTTP 500.

Ví dụ thông báo lỗi thật:
```
[PT03] @MockitoBean/@MockitoSpyBean in a parallel test
    at:   com.example.parallel.demo.SharedMockitoBeanConflictDemo.exchangeRates
    what: @MockitoBean ExchangeRateClient exchangeRates
    why:  the mock is ONE singleton in the shared context: concurrent tests overwrite each other's stubbing, ...
    fix:  use @PerTestMock (+ @RoutedMocks on the base), or put the class under JUnit @Isolated / @ResourceLock

[PT10] Context budget exceeded
    what: ...ContextBudgetBBudgetDemo started context #2 (max-contexts=1). Existing contexts were started by:
          - ...ContextBudgetABudgetDemo
          Closest is the one of ...ContextBudgetABudgetDemo; it differs only in:
            * properties:
                existing: [demo.variant=A, ...]
                this:     [demo.variant=B, ...]
```

## 4. Tự kiểm chứng

```bash
RUNS=3 scripts/verify-parallel.sh
```

| Bước | Kiểm tra |
|---|---|
| Library | 126 self-test: mỗi rule bắt đúng fixture vi phạm và không báo oan fixture hợp lệ; mock routing được kiểm tra với 50 lần lặp chạy đồng thời; Spring context thật (JDBC guard trên mọi đường, SQL lúc khởi động được cho qua); **end-to-end qua JUnit TestKit** (vi phạm bị nuốt trên thread khác vẫn làm test fail, PT14, `@Nested`) |
| Tắt guard (chống tái phát B1) | các IT dùng `@PerTestMock` vẫn xanh, vì mock vẫn được định tuyến |
| Build mặc định × RUNS | unit + IT xanh; probe đo được các class **và** method chạy chồng lên nhau; đúng 2 context; mỗi context một DB |
| Multi-fork | các fork chạy chồng thời gian; DB khác nhau giữa các fork |
| Demo xung đột, **tắt guard** | cả 5 xung đột đều xảy ra thật (sai dữ liệu, lock timeout, context bị đóng giữa chừng) |
| Demo xung đột, **bật guard** | mỗi class bị chặn **trước khi kịp xung đột**, đúng các mã rule mong đợi |
| Guard demo | PT12 qua JPQL, `@Sql` và `JdbcTemplate`; PT11 (pool = 2); PT10 (vượt số context cho phép); probe xác nhận không có vi phạm nào bị "mồ côi" |

Đã thử thêm: tạo một `CarelessIT` vi phạm 4 rule. Build fail ở `ParallelSafetyTest` sau **0,2 giây**, liệt kê 5 vi phạm
kèm số dòng, trước khi bất kỳ IT nào được khởi động.

## 5. Giới hạn (nói rõ để không tin nhầm)

Guard **không** thay thế được kỷ luật dữ liệu. Những điểm sau **không** được tự động phát hiện:

- **Key cố định trùng nhau** (vd. hai test cùng insert `owner = "alice"`, hoặc `@Sql` seed dữ liệu với id cố định).
  Hãy dùng `TestData.unique(...)`. Demo `TransactionalRollbackConflictDemo` chỉ bị chặn vì lý do khác (PT01).
- **Trạng thái dùng chung ngoài DB**: Kafka topic, Redis key, cache, file, và WireMock server dùng chung
  (stub của WireMock dùng chung cũng xung đột y như `@MockitoBean`). Quy tắc: mỗi test dùng tên/key riêng.
  PT06 chỉ bắt field `static` **không** `final`; object mutable khai báo `static final` không bị phát hiện.
- PT07 chỉ bắt lời gọi **trực tiếp** trong test class và class helper, không lần theo lời gọi qua nhiều tầng
  hay qua code trong `src/main`.
- PT14 chỉ thấy được dependency mà Spring đã ghi nhận lúc inject, không thấy `context.getBean(...)` gọi lúc runtime.
- `@PerTestMock` chỉ định tuyến được lời gọi trên thread của test, request có gắn `PerTestMockHttp.propagation()`,
  và task đã `PerTestMocks.wrap*`. Lời gọi khác (lúc app khởi động, `@Scheduled`) **ném lỗi rõ ràng**.
- Router là `@Primary`, nên nếu app đã có một bean `@Primary` cùng type thì sẽ xung đột.
  Inject `HikariDataSource` bằng class cụ thể sẽ fail lúc khởi động, vì DataSource được bọc proxy theo interface.
- Môi trường này không có Docker nên demo dùng H2. Với PostgreSQL/Testcontainers: dùng một container chung,
  một schema hoặc database cho mỗi fork, và đặt pool lớn hơn hoặc bằng số thread.

## 6. Đây có phải cách tốt nhất?

| Cách | Cách ly | Chi phí | Kết luận |
|---|---|---|---|
| Mỗi fork một JVM (chạy tuần tự) | process | chậm nhất (30.0s so với 10.6s) | loại |
| Rollback `@Transactional` cho mỗi test | chỉ trên thread của test | rẻ | không áp dụng được cho HTTP/async; unique key vẫn đụng nhau |
| **Một context dùng chung + chạy song song + dữ liệu riêng cho từng test + mock riêng cho từng test + guard** | theo dữ liệu | nhanh nhất | **chọn**: cân bằng tốt nhất, và được ép buộc bằng guard |
| Mỗi test một database/schema (vd. clone từ template DB của Postgres) | hoàn toàn | khoảng 50–200ms mỗi test, cần Docker | đáng dùng nếu team **không** đảm bảo được dữ liệu riêng cho từng test; có thể làm thành v3 |

## 7. Changelog review (v2.1)

| # | Vấn đề tìm thấy khi review | Sửa |
|---|---|---|
| B1 | `enabled=false` tắt luôn router, khiến `@PerTestMock` âm thầm gọi bean thật (đã tái hiện: cả 14 test gọi `fx.invalid`) | việc định tuyến không còn phụ thuộc `enabled`; thêm bước chống tái phát vào verify |
| B2 | Bỏ lọt method reference (`repo::deleteAll`, biên dịch thành `invokedynamic`) | quét cả method handle |
| B3 | Bỏ lọt local class | quét qua JVM nest members |
| B4 | `PER_CLASS` + chạy song song làm `@PerTestMock` bị ghi đè | rule PT13 |
| B5 | Đếm context dùng `identityHashCode`, có thể trùng nên đếm thiếu | so sánh theo identity thực |
| B6 | Task đã `wrap` chạy sau khi test kết thúc vẫn dùng mock của test đã chết | fail với thông báo rõ ràng |
| B7 | `@Nested` không nhận field `@PerTestMock` của class ngoài (JDK 18+ còn bỏ luôn field `this$0`) | inject khi lấy được instance bên ngoài, nếu không thì vẫn gắn mock cho router |
| B8 | Vi phạm trên thread của server chỉ hiện ra thành HTTP 500 | gán lại cho đúng test và làm test đó fail |
| G1 | SQL guard chỉ có cho Hibernate; `@Sql`/`JdbcTemplate` lọt qua | guard ở tầng JDBC, bỏ dependency Hibernate |
| G2 | Inject bằng class cụ thể hoặc `@Qualifier` âm thầm bỏ qua router | rule PT14 |
| G3 | Số thread bỏ sót `configurationParameters` của Surefire | đọc từ `TestPlan` thực tế |
