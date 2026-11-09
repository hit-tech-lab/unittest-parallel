# Guard an toàn song song

Guard biến các quy tắc "test chạy song song phải an toàn" thành lỗi build có mã ổn định (`PTxx`), chỉ đúng lớp/dòng/test,
kèm lý do và cách sửa. Với lớp có `@ParallelSpringTest` guard tự chạy, không cần khai báo thêm. Chi tiết đầy đủ, giới hạn
heuristic và bằng chứng kiểm tra: [GUARD.md](../parallel-test-lib/GUARD.md).

Thông điệp có dạng `[ptest-guard] <tiêu đề> (n violations)` rồi từng khối `[PTxx] ... at / what / why / fix`; ngoại lệ là
`ParallelTestException.Guard` (`ruleCodes()` liệt kê mã luật). Các số PT02, PT04, PT05, PT09, PT14 không tồn tại.

## Bảng luật

| Mã | Loại | Mặc định | Bắt cái gì | Miễn trừ |
|---|---|---|---|---|
| PT01 | tĩnh | WARN | Spring test không có `@ParallelSpringTest` (chỉ qua quét source set) | `@SuppressGuard` |
| PT03 | tĩnh | FAIL | `@MockitoBean`/`@MockitoSpyBean`/`@MockBean`/`@SpyBean` trên lớp hoặc field | `@Isolated`, `@ResourceLock`, `@SuppressGuard` |
| PT06 | tĩnh | FAIL | field `static` không `final` | `@Isolated`, `@SuppressGuard` |
| PT07 | tĩnh (bytecode) | FAIL | gọi method cả bảng/toàn cục trong danh sách cấm (`deleteAll()`, `count()`, `findAll()`...) | `@Isolated`, `@SuppressGuard` |
| PT08 | tĩnh | FAIL | `webEnvironment = DEFINED_PORT` | `@SuppressGuard` |
| PT10 | runtime | FAIL | vượt `ptest.max-contexts`; kèm lớp tạo context, context gần nhất và diff cấu hình | tăng `ptest.max-contexts` sau review, hoặc `ptest.guard.PT10=warn` |
| PT11 | runtime | FAIL | pool của `DataSource` nhỏ hơn số thread song song của JUnit | `@SuppressGuard` |
| PT12 | runtime | FAIL | `DELETE`/`UPDATE` không `WHERE`, `TRUNCATE`, `DROP` ở tầng JDBC sau khi context khởi động | `@Isolated`; `@SuppressGuard` |
| PT13 | tĩnh | FAIL | `@TestInstance(PER_CLASS)` + field instance mutable + method chạy song song | `@Execution(SAME_THREAD)`, `@Isolated`, `@ResourceLock`, `@SuppressGuard` |

Danh sách cấm mặc định của PT07: `CrudRepository#deleteAll()`, `#count()`, `#findAll()`,
`PagingAndSortingRepository#findAll(Sort|Pageable)`, `JpaRepository#deleteAllInBatch()`,
`JdbcTestUtils#deleteFromTables|countRowsInTable|dropTables`. Receiver khớp theo cây kiểu nên `accountRepository.deleteAll()` khớp.

## Cấu hình `ptest.guard.*`

Đọc lại mỗi lần dùng qua `SpringProperties`: file `spring.properties` ở gốc classpath trước, rồi system property `-D`.

| Khóa | Giá trị | Mặc định | Tác dụng |
|---|---|---|---|
| `ptest.guard.enabled` | `true`/`false` | `true` | `false` tắt mọi luật; mock/spy theo test vẫn chạy |
| `ptest.guard.<PTxx>` | `fail`/`warn`/`off` | theo bảng | mức từng luật; giá trị sai là lỗi `CONFIG`, không âm thầm về mặc định |
| `ptest.guard.forbidden-calls` | `Type#method(params), ...` | rỗng | thêm vào danh sách cấm của PT07 (`#m()` không tham số, `#m(*)` mọi tham số) |
| `ptest.guard.scan-exclude` | `pkg.a, pkg.b` | rỗng | tiền tố tên lớp bị bỏ qua khi quét cả source set |

```properties
# src/test/resources/spring.properties
ptest.guard.PT01=fail
ptest.guard.PT10=warn
ptest.guard.forbidden-calls=com.acme.OrderRepository#deleteAllByStatus(*)
ptest.max-contexts=12
```

`@SuppressGuard` miễn trừ có kiểm soát cho một lớp (và lớp con, lớp `@Nested`); `reason` bắt buộc, rỗng là lỗi `CONFIG`:

```java
@SuppressGuard(rules = {"PT11"}, reason = "một test method; DataSource của người dùng giữ pool mặc định")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "single")
class SingleMethodIT { }
```

PT10 không thuộc một lớp: dùng `ptest.guard.PT10=warn|off`. `@SuppressGuard("PT12")` chỉ có hiệu lực với SQL chạy trên
thread đã gắn với test của lớp đó (thread test, thread đã `ParallelTest.wrap`, request HTTP có header binding).

## Quét cả source set

Kế thừa MỘT lần để liệt kê mọi vi phạm tĩnh của toàn bộ code test (thêm PT01 và lớp tiện ích). Test này không cần Spring:

```java
package demo.arch;

import com.hit.ptest.guard.AbstractParallelSafetyTest;

class ParallelSafetyIT extends AbstractParallelSafetyTest {
}
```

Loại package negative control bằng `ptest.guard.scan-exclude`.

## Viết test an toàn song song

Các test cùng context dùng chung database và bean singleton; mọi thứ dùng chung phải được phân biệt theo test.

```java
// KHÔNG (PT07): đọc/xóa dữ liệu của test khác đang chạy
accounts.deleteAll();
assertThat(accounts.count()).isEqualTo(1);

// ĐÚNG: khóa riêng cho mỗi test, chỉ động tới dòng của mình
Account a = accounts.save(new Account("a-" + UUID.randomUUID(), "USD", BigDecimal.TEN));
assertThat(accounts.findById(a.getId())).isPresent();
accounts.deleteById(a.getId());
```

```java
// KHÔNG (PT12): SQL cả bảng
jdbc.update("delete from account");

// ĐÚNG
jdbc.update("delete from account where id = ?", a.getId());
```

```java
// KHÔNG (PT06): static state mutable dùng chung mọi test
static int sharedCounter;

// ĐÚNG: field instance (mỗi method một instance), hoặc static final bất biến
int counter;
static final List<String> NAMES = List.of("ok");
```

| Tình huống | Làm thay vì |
|---|---|
| `@MockitoBean`/`@MockBean` trên lớp song song (PT03) | field `@PerTestMock`/`@PerTestSpy`, xem [Khai báo mock/spy](mock-spy-declaration.md) |
| `DEFINED_PORT` (PT08) | `RANDOM_PORT`, lấy cổng bằng `@Value("${local.server.port}")` |
| `@TestInstance(PER_CLASS)` có field mutable (PT13) | `PER_METHOD` mặc định, hoặc `@Execution(SAME_THREAD)` |
| Pool nhỏ hơn số thread (PT11) | `ptest.db.pool-size` (hoặc `spring.datasource.hikari.maximum-pool-size`) >= parallelism |
| Quá nhiều context (PT10) | cho lớp dùng lại cấu hình có sẵn (xem diff trong thông điệp) hoặc tăng `ptest.max-contexts` sau review |
| Khóa cố định trùng giữa hai test | sinh khóa riêng (`UUID`, `System.nanoTime()`) |

Guard KHÔNG thay thế kỷ luật dữ liệu: khóa cố định trùng nhau giữa hai test và trạng thái ngoài DB (Kafka, Redis, file, cache)
không được tự động phát hiện. Vi phạm xảy ra trên thread khác (server, executor đã `ParallelTest.wrap`) được gán lại cho
đúng test và làm test đó fail ở `afterEach`, kể cả khi ứng dụng nuốt lỗi và test chỉ thấy HTTP 500.

PT07 chỉ thấy lời gọi trực tiếp trong bytecode của code test, không lần theo qua `src/main`, reflection hay receiver khai báo
bằng kiểu không liên quan. Danh sách đầy đủ ở mục 4 của [GUARD.md](../parallel-test-lib/GUARD.md).
