# Spring Boot: unit test + integration test chạy parallel, có kiểm chứng xung đột

Spring Boot 4.1 · Java 21 · JUnit 6 · Mockito · JPA/H2 · Maven Surefire (unit) + Failsafe (IT)

Mục tiêu: chạy test song song **và tự kiểm chứng được** rằng:
1. Test **thật sự** chạy song song (đo được, không phải giả định).
2. Không có xung đột về **Spring context, mock, database, singleton state, port**.
3. Nếu viết test sai cách thì xung đột **có xảy ra thật** và bộ kiểm tra **bắt được** (negative control).

## Chạy

```bash
mvn verify                                   # unit + IT, 1 JVM, class + method chạy song song
```

```bash
mvn verify -Dunit.forks=2 -Dit.forks=2       # nhiều JVM fork song song
```

```bash
mvn verify -Pconflict-demo                   # chỉ chạy các demo xung đột (dự kiến FAIL)
```

```bash
RUNS=5 scripts/verify-parallel.sh            # tự verify toàn bộ (xem bên dưới)
```

## Hai tầng parallel

| Tầng | Cấu hình | Ghi chú |
|---|---|---|
| Trong 1 JVM | `src/test/resources/junit-platform.properties`: class và method `concurrent`, 4 thread (`-Dit.parallelism=`) | Test dùng chung context, mock, DB **cùng lúc** → dễ lộ xung đột nhất |
| Nhiều JVM | `forkCount` = `-Dunit.forks` / `-Dit.forks` | Mỗi fork có DB và port riêng |

> **Phát hiện khi đo:** với `forkCount > 1`, Surefire/Failsafe đưa **từng class một** vào mỗi fork,
> nên trong một fork chỉ có **các method** chạy song song (`maxConcurrentClasses=1`).
> Vì vậy mặc định để `forks=1` cho parallel mức class, còn multi-fork dùng khi cần cách ly ở mức process.

## Cách tự verify (`scripts/verify-parallel.sh`)

- `support/ParallelismProbe`: JUnit Platform listener, đo số test/class chạy đồng thời tối đa, số thread và
  thời gian của từng fork. Kết quả ghi vào `target/parallel-reports/*.properties`.
- `support/ContextTrackingListener`: Spring TestExecutionListener, đếm số `ApplicationContext` được tạo
  và DB mà mỗi context dùng. Dùng để phát hiện context cache bị vô hiệu (bùng nổ số context) hoặc 2 context dùng chung DB.
- `UnitParallelismSanityTest`, `IsolationIT.parallelSanity*`: 2 test phải gặp nhau tại barrier. Nếu
  parallel bị tắt thì các test này **FAIL**, không im lặng pass.
- `scripts/check_reports.py`:
  - **single**: method và class có chồng thời gian chạy; số context ≤ 3; mỗi context một DB riêng.
  - **forked**: ≥ 2 process khác nhau, chạy chồng thời gian, DB khác nhau và gắn đúng số fork.
  - **demo**: cả 5 demo xung đột đều FAIL, và FAIL vì xung đột thật chứ không phải do lỗi lập lịch.

## Các xung đột và cách tránh

| Xung đột | Demo (cố tình sai → FAIL) | Cách đúng (pass khi chạy song song) |
|---|---|---|
| `@MockitoBean` là singleton trong context dùng chung; stub, verify và reset đè lên nhau | `SharedMockitoBeanConflictDemo`: `expected 20 but was 30`, `Wanted 1 time but was 2` | Nhiều class: `@PerTestMock` + `MockRouter` (`AbstractRoutedMockIT`). Ít class: `@ResourceLock` (`AbstractMockitoBeanIT`). Gọi từ thread khác: fake theo key (`FakeExchangeRateClient`). Xem mục bên dưới |
| DB dùng chung: `deleteAll()` hoặc assert trên cả bảng | `SharedDatabaseConflictDemo`: thấy cả row của class khác | Dữ liệu unique cho từng test (`TestData`), chỉ assert trên dữ liệu test tự tạo |
| Singleton bean có state (`AuditLog`) | `SharedSingletonStateConflictDemo`: `size 1 but was 2` | Lọc theo id của chính test (`TransferApiIT`) hoặc dùng `@ResourceLock` |
| `@Transactional` rollback **không** cách ly được unique constraint | `TransactionalRollbackConflictDemo`: `CannotAcquireLock` | Key unique ngay cả trong test có rollback (`AccountRepositoryIT`) |
| `@DirtiesContext` đóng context khi test khác còn đang dùng | `DirtiesContextConflictDemo` | Không dùng trong suite parallel, hoặc cho class đó context riêng + lock |
| Hai context cùng DB: `create-drop` của context này xoá bảng của context kia | (chặn bằng cấu hình) | `application-test.properties`: URL `fork${fork.number}-${random.uuid}` |
| Trùng port giữa các fork | (chặn bằng cấu hình) | `RANDOM_PORT` (`IsolationIT`) |
| `Mockito.mockStatic` | không xung đột: chỉ có hiệu lực trên thread hiện tại | `MockitoThreadConfinementTest` chứng minh điều này |
| Logic DB đồng thời của chính ứng dụng | | `ConcurrentTransferIT`: 8 thread × 120 transfer, tổng tiền không đổi, không deadlock |

## Khi có nhiều class test dùng cùng một bean bị mock

`@ResourceLock` đúng nhưng **không mở rộng được**: mọi class giữ cùng lock chạy tuần tự (cả các method bên trong).
Giải pháp mở rộng được là `@PerTestMock` + `MockRouter`:
- Trong context chỉ có **một bean router**, nên mọi class dùng chung **một** context.
- Trước mỗi test, `PerTestMockExtension` tạo **một Mockito mock mới** và gắn vào thread của test.
  Router chuyển mọi lời gọi tới mock của thread hiện tại, nên stub, verify và reset không bao giờ đụng nhau.
- `RoutedMockTransferIT` và `RoutedMockFailureIT` chạy **đúng kịch bản** làm `SharedMockitoBeanConflictDemo` fail:
  hai class cùng lúc stub 2 và 3 lên cùng một bean. Cả hai đều pass, và barrier chứng minh chúng chạy đồng thời.
- **Giới hạn đã được kiểm tra:** lời gọi phải chạy trên thread của test (gọi service trực tiếp, hoặc MockMvc với `webEnvironment=MOCK`).
  Lời gọi từ thread của server (`RANDOM_PORT`), `@Async` hay executor sẽ **ném lỗi rõ ràng**
  (`callFromAnotherThreadFailsLoudly`) chứ không âm thầm dùng nhầm mock của test khác. Với những trường hợp đó,
  dùng fake theo key riêng của từng test (`FakeExchangeRateClient`).

Đo với 20 class × 5 test, mỗi test chậm khoảng 200ms, trên máy 4 CPU:

| Cách | Cấu hình | Thời gian | Số test chạy đồng thời tối đa |
|---|---|---|---|
| `@MockitoBean` + `@ResourceLock` | 1 fork, 4 thread | 30.0s | 1 |
| `@MockitoBean` + `@ResourceLock` | 2 fork | 21.9s | 1 trong mỗi fork |
| `@MockitoBean` + `@ResourceLock` | 1 fork, 16 thread | 30.0s | 1 (thêm thread cũng không nhanh hơn) |
| `@PerTestMock` (router) | 1 fork, 4 thread | 14.1s | 4 |
| `@PerTestMock` (router) | 1 fork, 16 thread | **10.5s** | 16 |

Thứ tự nên chọn:
1. Phần lớn test chỉ cần mock thì viết **unit test** hoặc dùng slice test, không cần full context.
2. IT cần mock và lời gọi chạy trên thread của test: dùng `@PerTestMock`.
3. IT đi qua HTTP thật hoặc xử lý async: dùng fake thread-safe với dữ liệu riêng cho từng test.
4. Chỉ một vài class thì `@ResourceLock` là đủ. Có thể tăng `forkCount` để mỗi JVM chạy song song một class giữ lock.
5. Tránh cho mỗi class một context riêng (thêm property hoặc `@MockitoBean` khác nhau): N class sẽ thành N lần khởi động context.

Lưu ý khác:
- Mọi IT kế thừa `AbstractIntegrationTest` dùng **cùng một cấu hình**, nên cả suite chỉ cần 4 context
  (base, MockitoBean, RoutedMock, DataJpa). Mỗi `@MockitoBean`, `@TestPropertySource` hay cấu hình khác nhau sẽ tạo thêm một context.
- Với `RANDOM_PORT`, request chạy trên thread của server, nên `@Transactional` trong test **không** rollback dữ liệu.
- Môi trường này không có Docker nên dùng H2. Với PostgreSQL/Testcontainers thì áp dụng cùng nguyên tắc:
  một database/schema cho mỗi fork (`${surefire.forkNumber}`) và dữ liệu unique cho từng test.
