# Guard an toàn song song (`com.hit.ptest.guard`)

Guard biến các quy tắc "test chạy song song phải an toàn" thành lỗi build có mã ổn định, chỉ đúng lớp/dòng/test,
kèm lý do và cách sửa. Nó dùng được **độc lập với cơ chế can thiệp bean** (REAL/SPY/MOCK): chỉ phụ thuộc API gốc
`com.hit.ptest` (annotation, exception) cùng Spring/JUnit. Khi dùng cùng `@ParallelSpringTest`, guard tự chạy, không
cần khai báo thêm; khi dùng riêng, chỉ cần `AbstractParallelSafetyTest` (quét tĩnh) và `JdbcSqlGuardPostProcessor`
(chặn SQL).

Người đọc: người viết/duyệt test của dự án dùng thư viện. Mọi luật dưới đây đã được kiểm chứng bằng test của thư viện
và bằng `lib-tests` (xem mục 5).

## 1. Mã luật PT01..PT14 và các mã không có

Thiết kế của thư viện: nhiều context có tên, spy giữ ngữ nghĩa Mockito, mock/spy theo từng test bằng `@PerTestMock`/`@PerTestSpy`.
Vì vậy một số mã luật có điều kiện áp dụng riêng và các mã PT02, PT04, PT05, PT09, PT14 không tồn tại (lý do ở bảng).

| Mã | Ý nghĩa | Loại | Trạng thái | Lý do |
|---|---|---|---|---|
| PT01 | Spring test thiếu `@ParallelSpringTest` | tĩnh | **CÓ** (mặc định WARN) | Lớp Spring test ngoài cơ chế ptest không có DB riêng, mock/spy riêng, không bị đếm vào `max-contexts` |
| PT02 | Đổi cấu hình context dưới base | tĩnh | **KHÔNG CÓ** | Nhiều context có tên là thiết kế chủ đích; số context bị chặn bởi PT10 (kèm diff) |
| PT03 | `@MockitoBean`/`@MockitoSpyBean` | tĩnh | **CÓ** (PT03) | Giữ cấm vì mock là singleton của context; thông điệp gợi ý field `@PerTestMock`/`@PerTestSpy` (mock/spy riêng từng test, không tách context); miễn trừ bằng `@Isolated`, `@ResourceLock`, `@SuppressGuard` |
| PT04 | `@DirtiesContext` | tĩnh | **KHÔNG CÓ LUẬT TĨNH** (dùng cơ chế runtime) | Đã có `DIRTIES_CONTEXT_UNSUPPORTED` lúc chạy (`ptest.unsupported=fail\|warn`); không thêm luật trùng |
| PT05 | `@PerTestMock` thiếu router | tĩnh | **KHÔNG CÓ** | `@PerTestMock` của thư viện này không cần router: mọi bean ứng dụng đã có wrapper; khai báo sai báo lỗi cấu hình riêng (mục 6) |
| PT06 | Field `static` mutable | tĩnh | **CÓ** (PT06) | Quét mọi lớp test, không chỉ Spring test |
| PT07 | Gọi `deleteAll`/`count`/`findAll`/`JdbcTestUtils`... | tĩnh (bytecode) | **CÓ** (PT07) | Có cấu hình `ptest.guard.forbidden-calls`; lớp test con `static` độc lập được quét riêng để miễn trừ đúng |
| PT08 | `webEnvironment = DEFINED_PORT` | tĩnh | **CÓ** (PT08) | Cổng cố định đụng nhau giữa context/fork |
| PT09 | `@TestExecutionListeners` thay mặc định | tĩnh | **KHÔNG CÓ** | Guard của thư viện này không dựa vào `TestExecutionListener` (dùng `ContextCustomizer` + extension JUnit) nên không thể bị gỡ bằng cách đó |
| PT10 | Vượt ngân sách context, in diff | runtime | **CÓ** (PT10) | Đã có `max-contexts` (`CONTEXT_LIMIT_EXCEEDED`); bổ sung diff cấu hình với context gần nhất và mức FAIL/WARN/OFF |
| PT11 | Pool nhỏ hơn số thread | runtime | **CÓ** (PT11) | Giữ; kiểm tra theo từng cặp (lớp, context) để `@SuppressGuard` không phụ thuộc thứ tự chạy |
| PT12 | `DELETE` không WHERE / `TRUNCATE` ở tầng JDBC | runtime | **CÓ, mở rộng** (PT12) | Thêm `UPDATE` không WHERE và `DROP`; bỏ qua comment và literal chuỗi; tắt khi context đóng (Hibernate `create-drop`) |
| PT13 | `PER_CLASS` + method song song | tĩnh | **CÓ** (PT13) | Chỉ báo khi lớp có field instance mutable (không tính field chỉ được inject); field `@PerTestMock`/`@PerTestSpy` trên `PER_CLASS` đã có lỗi `CONFIG` riêng |
| PT14 | Bean thật của type có router vẫn được inject | runtime | **KHÔNG CÓ** | Không có router theo type; bean can thiệp được thay bằng wrapper ngay trong bean definition nên mọi nơi inject đều nhận wrapper |

Vi phạm xảy ra trên thread khác (server, executor) được **gán lại cho đúng test** (xem 3.4).

## 2. Luật, mã lỗi, cấu hình

Mỗi thông điệp có dạng `[ptest-guard] <tiêu đề> (n violations)` rồi từng khối `[PTxx] ... at / what / why / fix`.
Ngoại lệ ném ra là `ParallelTestException.Guard` (`ruleCodes()` liệt kê mã luật).

| Mã | Loại | Mặc định | Bắt cái gì | Miễn trừ |
|---|---|---|---|---|
| PT01 | tĩnh | WARN | Spring test không có `@ParallelSpringTest` (chỉ qua quét source set) | `@SuppressGuard` |
| PT03 | tĩnh | FAIL | `@MockitoBean`/`@MockitoSpyBean`/`@MockBean`/`@SpyBean` trên lớp hoặc field | `@Isolated`, `@ResourceLock`, `@SuppressGuard` |
| PT06 | tĩnh | FAIL | field `static` không `final` | `@Isolated`, `@SuppressGuard` |
| PT07 | tĩnh (bytecode) | FAIL | lời gọi/method reference tới method trong danh sách cấm (kể cả trong lambda, lớp lồng, lớp ẩn danh, lớp tiện ích) | `@Isolated`, `@SuppressGuard` |
| PT08 | tĩnh | FAIL | `webEnvironment = DEFINED_PORT` | `@SuppressGuard` |
| PT10 | runtime | FAIL | vượt `ptest.max-contexts`; kèm lớp tạo context, context gần nhất và diff cấu hình | tăng `ptest.max-contexts` sau review, hoặc `ptest.guard.PT10=warn` |
| PT11 | runtime | FAIL | pool của `DataSource` nhỏ hơn số thread song song của JUnit | `@SuppressGuard` |
| PT12 | runtime | FAIL | `DELETE`/`UPDATE` không `WHERE`, `TRUNCATE`, `DROP` ở tầng JDBC sau khi context khởi động | `@Isolated`; `@SuppressGuard` (chỉ cho SQL chạy trên thread đã gắn với test của lớp đó) |
| PT13 | tĩnh | FAIL | `@TestInstance(PER_CLASS)` + field instance mutable + method chạy song song | `@Execution(SAME_THREAD)`, `@Isolated`, `@ResourceLock`, `@SuppressGuard` |

Danh sách gọi bị cấm mặc định của PT07: `CrudRepository#deleteAll()`, `#count()`, `#findAll()`,
`PagingAndSortingRepository#findAll(Sort|Pageable)`, `JpaRepository#deleteAllInBatch()`,
`JdbcTestUtils#deleteFromTables|countRowsInTable|dropTables`. Receiver khớp theo cây kiểu nên
`accountRepository.deleteAll()` khớp `CrudRepository#deleteAll()`.

### Cấu hình `ptest.guard.*`

Đọc lại mỗi lần dùng qua `SpringProperties`, cùng nguồn và thứ tự ưu tiên với `PtestSettings`: `spring.properties`
trước, rồi system property (`-D`).

| Khóa | Giá trị | Mặc định | Tác dụng |
|---|---|---|---|
| `ptest.guard.enabled` | `true`/`false` | `true` | `false` tắt mọi luật; cơ chế can thiệp mock/spy vẫn chạy như thường |
| `ptest.guard.<PTxx>` | `fail`/`warn`/`off` | theo bảng trên | mức của từng luật; giá trị sai là lỗi cấu hình `CONFIG`, không âm thầm về mặc định |
| `ptest.guard.forbidden-calls` | `Type#method(params), ...` | rỗng | thêm vào danh sách cấm của PT07 (`#m()` không tham số, `#m(*)` mọi tham số) |
| `ptest.guard.scan-exclude` | `pkg.a, pkg.b` | rỗng | tiền tố tên lớp bị bỏ qua khi quét cả source set |

`@SuppressGuard(rules = {"PT07"}, reason = "...")` miễn trừ có kiểm soát cho một lớp (và lớp con, lớp `@Nested`);
`reason` bắt buộc không rỗng, nếu rỗng là lỗi `CONFIG`.

Cấu hình sẵn có không đổi: `ptest.max-contexts` (ngưỡng PT10), `ptest.db.pool-size` (mặc định `max(4, N)` với `N` là số thread song song của JUnit, cho DB do thư viện cấp).

## 3. Cách hoạt động

1. **Quét tĩnh theo lớp** (`ClassRules`): `ParallelTestExtension.beforeAll` chạy PT03/PT06/PT07/PT08/PT13 cho từng
   lớp `@ParallelSpringTest` trước khi khởi động context.
2. **Quét tĩnh cả source set** (`AbstractParallelSafetyTest`): kế thừa một lần (`class ParallelSafetyIT extends
   AbstractParallelSafetyTest {}`) để liệt kê MỌI vi phạm một lần, thêm PT01 và lớp tiện ích. Dùng
   `ptest.guard.scan-exclude` để loại các package negative control.
3. **Chặn SQL** (`JdbcSqlGuardPostProcessor`): bọc mỗi `DataSource` thành proxy theo interface; mọi chuỗi SQL tới
   `Connection.prepareStatement/prepareCall/nativeSQL` và `Statement.execute*/addBatch` đi qua `SqlGuard`. Chỉ kiểm tra
   từ `ContextRefreshedEvent` đến `ContextClosedEvent`, nên script khởi tạo, seed, migration và `create-drop` không bị chặn.
4. **Gán vi phạm trên thread khác cho đúng test** (`GuardScope`): extension mở một phạm vi cho mỗi test; khi `SqlGuard`
   thấy vi phạm trên thread khác thread của test (ví dụ thread server, executor đã `ParallelTest.wrap`), nó vừa ném lỗi
   vừa ghi cho test sở hữu; `afterEach` ném `ParallelTestException.Guard` kể cả khi ứng dụng đã nuốt lỗi hay test chỉ thấy
   HTTP 500. Guard không biết "test hiện tại là ai": id test lấy qua `GuardScope.useOwnerResolver` (ptest nối vào binding).
5. **Pool** (`PoolSizeCheck`): kiểm tra khi lớp dùng context lần đầu, với số thread từ cấu hình JUnit thực tế
   (`ExtensionContext.getConfigurationParameter`, gồm `configurationParameters` của Surefire).
6. **Diff ngân sách context** (`ContextRegistry` + `ContextConfigDescription`): mô tả cấu hình Spring của mỗi context
   (lớp cấu hình, profile, property, customizer...) và so với context đang chạy gần nhất.

Ví dụ PT10 (rút gọn):

```
CONTEXT_LIMIT_EXCEEDED: cannot create context 'over-limit@4bd57b9e#11': 10 contexts are already active (ptest.max-contexts=1). Active: [...]
  new context was requested by demo.demo.ContextLimitConflictDemo
  closest active context is 'dup@75ca3e09#2' (created by demo.demo.NameConflictBDemo); the new one differs in:
    * context customizers (...):
        existing: [..., ptest[context=dup, wrap=[backupFx], exclude=[], schemas=[]]]
        new:      [..., ptest[context=over-limit, wrap=[*], exclude=[], schemas=[]]]
    * class-level @PerTestMock (the bean is a mock for the whole context: org.springframework.data.mongodb.core.MongoTemplate):
        existing: (not declared)
        new:      declared        (mỗi mock cấp class khác nhau là một mục riêng)
  [PT10] fix: make the class reuse an existing configuration (see the diff) or raise ptest.max-contexts after review
```

## 4. Giới hạn (heuristic, nói rõ để không tin nhầm)

- **PT07 chỉ thấy lời gọi trực tiếp** trong bytecode của lớp test, lớp lồng/ẩn danh/lambda của nó, lớp cha do người dùng
  viết và lớp tiện ích cấp cao trong source set test. Không lần theo lời gọi qua nhiều tầng, qua code ở `src/main`,
  qua reflection, hay receiver khai báo bằng kiểu không liên quan (ví dụ `Object`). Lớp `static` lồng trong lớp khác mà
  tự có method test được quét như lớp độc lập.
- **PT06** chỉ bắt field `static` không `final` và bỏ qua field `static` do JUnit/Testcontainers tiêm (`@TempDir`, `@RegisterExtension`, `@Container`); `static final` giữ object mutable (`List`, `AtomicInteger`, `Map`) không bị phát hiện.
- **PT13** coi field instance không `final` và không gắn annotation inject (`@Autowired`, `@Value`, `@Resource`,
  `@Inject`, `@LocalServerPort`, `@TempDir`...) là state mutable; không phân tích xem field có thực sự bị ghi hay không.
- **PT03** chỉ nhìn annotation khai báo trên lớp/field của người dùng; mock tạo bằng `Mockito.mock` trong `static` không bị bắt (PT06 chỉ bắt nếu field không `final`).
- **PT12** phân tích văn bản SQL: nhận `DELETE`/`UPDATE` không `WHERE` kể cả trong câu `WITH ...` (thân CTE như `DELETE ... RETURNING`) và `MERGE ... WHEN MATCHED THEN DELETE` không điều kiện; không hiểu mệnh đề đặc biệt (`WHERE 1=1` được coi là có `WHERE`, một `WHERE` trong subquery của chính câu đó cũng được tính); không bắt SQL chạy
  lúc context khởi động hay đóng (có chủ đích); `@SuppressGuard("PT12")` chỉ có hiệu lực trên thread đã gắn với test
  (thread test, thread đã `ParallelTest.wrap`, request HTTP có header binding).
- **DataSource bị bọc proxy theo interface**: tiêm `DataSource` thì dùng được (kể cả `unwrap(HikariDataSource.class)`);
  tiêm kiểu cụ thể (`HikariDataSource`) sẽ lỗi lúc khởi động.
- **PT11** chỉ đọc được kích thước pool qua getter của Hikari/DBCP2/Tomcat; `DataSource` khác (ví dụ routing) bị bỏ qua; chiến lược song song tự viết (`custom`) coi là 1 thread.
- **Truyền binding qua HTTP chỉ hỗ trợ stack Servlet** (`BindingFilter` là `OncePerRequestFilter`, header binding). WebFlux/Netty không có cơ chế
  truyền binding: bean bị mock/spy bị gọi từ thread reactor rơi vào chính sách `ptest.unbound-policy`; dùng `ParallelTest.wrap` ở ranh giới đó.
- **Mockito >= 5** (mock maker inline mặc định): wrapper và host của spy là mock của lớp cụ thể kể cả `final`. Thư viện kiểm tra khi tạo context đầu tiên và ném
  `CONFIG` nếu không mock được lớp final (Mockito 4 chưa bật inline, hoặc mock maker subclass).
- **Phạm vi API công khai**: người dùng chỉ dùng `com.hit.ptest` (`@ParallelSpringTest`, `@PerTestMock`/`@PerTestSpy`, `@SuppressGuard`, `ParallelTest`,
  `ParallelTestException`, `UnboundPolicy`) và `com.hit.ptest.guard.AbstractParallelSafetyTest`. Mọi lớp/phương thức public khác (ví dụ `ContextRegistry.INSTANCE`,
  `PostgresProvisioner.setStarterForTest/resetForTest`, `GuardScope.useOwnerResolver`, `InterceptMode`) là NỘI BỘ (public chỉ vì các package nội bộ gọi nhau,
  có ArchUnit khóa chiều phụ thuộc), có thể đổi không báo trước.
- Việc gán vi phạm cho test chỉ hoạt động khi thread mang binding của ptest; thread không binding chỉ được ghi log "belongs to no running test".
- Guard **không** thay thế kỷ luật dữ liệu: khóa cố định trùng nhau giữa hai test, trạng thái ngoài DB (Kafka, Redis, file, cache)
  không được tự động phát hiện.
- Mức WARN/OFF và `@SuppressGuard` làm giảm bảo đảm: mọi miễn trừ nên có lý do và được review.

## 5. Bằng chứng kiểm tra

- Test đơn vị của thư viện (`parallel-test-lib/src/test/java/com/hit/ptest/guard`, `context/ContextBudgetDiffTest`,
  `arch/PackageArchitectureTest`): mỗi luật có lớp mẫu vi phạm (`Bad*`) và lớp đúng tương ứng (`Good*`); SQL kiểm tra trên H2 thật;
  ArchUnit khóa chiều phụ thuộc (guard chỉ phụ thuộc API gốc; chỉ `context` và `junit` được gọi guard).
- `lib-tests` (consumer chỉ dùng API công khai): `ParallelSafetyIT` (quét sạch source set), các `Guard*Demo` trong profile
  `conflict-demo` (mỗi demo PHẢI fail đúng mã luật, đúng vị trí; control hợp lệ PHẢI pass), và `scripts/check_reports.py`
  (số vi phạm FAIL chính xác, số vi phạm gán sang thread khác chính xác, bộ đếm guard trong lần chạy sạch).
- Cấu hình DB trên Postgres thật: `DbPoolConfigIT` (`SHOW max_connections` bằng `ptest.db.postgres.max-connections`; Hikari `minimumIdle=1`), `DbPoolOverrideIT`
  (giá trị người dùng thắng mặc định), `PostgresWarnIT` (WARN khi `max-contexts x pool` vượt `max_connections`, im lặng khi đủ).
- Ma trận khai báo/vòng đời: `RepeatedMockIT` (`@RepeatedTest`), `DynamicFactoryIT` (`@TestFactory`), `NestedDeclarationIT` (`@Nested` chỉ field và `@Nested` tự khai),
  `Tier*IT` (kế thừa 3 tầng khai báo), `Set*IT` (3 base khác tập mock), `WebMockEnvIT` + `WebRandomPortEnvIT` (MOCK song song RANDOM_PORT): mỗi nhóm dùng rào chắn để chứng minh chạy đồng thời,
  `check_reports.py` kiểm số context chính xác; thêm demo âm `DeclInvalidDeclarationDemo`, `DeclMockReplaceUnsupportedDemo`.
- Chạy: `scripts/verify.sh` (JDK 17; xem biến `MVN`, `RUNS`, `SKIP_PG` ở đầu script).

## 6. Khai báo `@PerTestMock`/`@PerTestSpy` và lỗi cấu hình (mã ổn định)

### Ba vị trí đặt, một quy tắc chọn bean

| Vị trí | Ví dụ | Tác dụng | Khóa context |
|---|---|---|---|
| Cấp class (`@PerTestMock`, `@PerTestMocks`, lặp trực tiếp) | `@PerTestMock(PaymentGateway.class)`, `@PerTestMock(name = "primaryPricing", classes = PricingClient.class)` | như `@MockBean`: bean đã có thì **thay định nghĩa** bằng mock (bean thật không bao giờ được tạo), chưa có thì **tạo** bean mock; mock cho cả context | có |
| Field | `@PerTestMock PaymentGateway m;` | handle: mock/spy riêng của từng test (stub/verify) | không |
| Tham số method test | `void t(@PerTestMock(name = "primaryPricing") PricingClient m)` | ghi đè field cùng bean cho riêng method | không |
| Cấp class (`@PerTestSpy`, `@PerTestSpies`) | `@PerTestSpy(PaymentGateway.class)` | mỗi test có sẵn một spy riêng của bean thật đã có | không |

Chọn bean ở field/tham số: theo kiểu; đúng một bean thì lấy; nhiều bean thì lọc bằng `name` hoặc `@Qualifier`, rồi `@Primary`
(như `DefaultListableBeanFactory.determinePrimaryCandidate`), rồi tên field trùng tên bean; `@Priority` không được xét. Trên tham số
phải dùng `name` vì `@Qualifier` tham số bị SpringExtension giành. Cấp class: `value`/`classes` là kiểu (gộp), `name` đi với đúng một kiểu.

### Quy tắc "thật ở lớp này, mock ở lớp kia"

Bean khai cấp class **luôn là mock cho cả context**, không còn bản thật. Bean cần thật ở một số lớp và mock ở lớp khác thì **không** khai
cấp class: chỉ lớp con cần mock đặt field `@PerTestMock`; các lớp còn lại không khai gì và gọi bản thật, cùng một context. Lớp con khai
`@PerTestSpy` (field hoặc cấp class) trên bean đã bị khai cấp class nhận `SPY_NO_BEAN` (không còn bản thật để spy).

Hai cạm bẫy của bean thật được mock theo từng test: (1) bean phải được bọc (`wrap`, mặc định `*`): bean `@ConfigurationProperties`, bean kho dữ liệu (kiểu `Map`/`Collection`) và bean kiểu
hạ tầng (`DataSource`, `EntityManagerFactory`, transaction manager, `MessageSource`, `ConversionService`) mặc định KHÔNG được bọc, muốn mock
theo từng test thì liệt kê tên trong `wrap` (hoặc khai mock cấp class để thay bean cho cả context; mock đó không đi qua bộ bind `@ConfigurationProperties`); (2) wrapper mang giá trị field của bean thật tại lúc bọc (như `Mockito.spy`) nên đọc thẳng field
của bean đã tiêm vẫn thấy dữ liệu, nhưng `toString()` của bean được bọc là `Interceptor[<tên bean>]` (không phải `toString()` của bean thật).

### Khóa context

Tập khai báo cấp class là một phần khóa context. Hai lớp khai khác tập mock là hai context khác nhau; PT10 (vượt ngân sách context) in diff
mỗi bean khác nhau thành một dòng `class-level @PerTestMock (...)` kèm `existing`/`new`. Cùng tập (kể cả khai ở lớp cha) thì dùng chung.
Khai báo cấp class lấy từ mọi lớp trong phân cấp của lớp test (cả lớp bao ngoài của `@Nested`); khai trùng hệt nhau gộp một.
Một lớp `@Nested` tự khai thêm `@PerTestMock`/`@PerTestSpy` cấp class có tập khai báo KHÁC lớp bao ngoài nên nằm ở context KHÁC lớp bao ngoài
(tốn thêm một context so với ngân sách `ptest.max-contexts`); muốn dùng chung context thì khai ở lớp bao ngoài, hoặc dùng field trong lớp `@Nested`.

Mock cấp class được đăng ký ở BeanFactoryPostProcessor lập trình, SAU `ConfigurationClassPostProcessor`: bean mock được THAY định nghĩa có sẵn nên
điều kiện thấy bean cũ; còn bean mock TẠO MỚI thì `@ConditionalOnBean(MockedType)` của auto-configuration KHÔNG thấy (khác `@MockBean`). Cần điều kiện đó
thì đăng ký bean bằng `@Bean`/`@Import` trong cấu hình test để thư viện chỉ việc thay.

### Mã lỗi

Không thuộc guard PTxx (không có mức `warn`/`off`): ném `ParallelTestException.Config` với thông điệp bắt đầu bằng mã, ở `beforeEach` của test
(hoặc lúc dựng context với `MOCK_ONLY_CONFLICT`/`BEAN_AMBIGUOUS` của khai báo cấp class).

| Mã | Khi nào | Cách sửa |
|---|---|---|
| `BEAN_AMBIGUOUS` | nhiều bean cùng kiểu, không `@Primary`, không `name`/`@Qualifier`, tên field không trùng tên bean (liệt kê ứng viên) | thêm `name = "<tên bean>"` (bắt buộc trên tham số) hoặc `@Qualifier`, hoặc đặt tên field trùng tên bean; khai báo cấp class không có field nên chỉ còn `@Primary` hoặc `name` (thông điệp nêu đúng cách này) |
| `BEAN_NOT_FOUND` | field/tham số trỏ tới kiểu chưa có bean | khai cấp class `@PerTestMock(MyBean.class)` ở lớp mang `@ParallelSpringTest` hoặc lớp cha để thư viện tạo |
| `SPY_NO_BEAN` | `@PerTestSpy` trên bean không tồn tại, hoặc đã bị `@PerTestMock` cấp class thay/tạo (thông điệp nói rõ "REPLACED"/"CREATED"); cùng bean vừa mock vừa spy cấp class | bỏ spy, hoặc bỏ khai báo mock cấp class (mock ở lớp cần bằng field) |
| `MODE_CONFLICT` | cùng bean vừa mock vừa spy ở field/tham số trong cùng phân cấp (kể cả `@Nested`) | giữ một chế độ; tham số method ghi đè field cùng bean chỉ cho method đó |
| `MOCK_ONLY_CONFLICT` | cùng `name` khai cấp class với hai kiểu; `name` đã dùng cho bean khác kiểu; hai khai báo cùng thay một bean; sau refresh có thêm bean cùng kiểu với bean mock được tạo (sản phẩm FactoryBean không đoán được kiểu: dùng `name` của chính bean đó để thay) | đổi `name`, khai một lần, hoặc đặt `name` đúng bean cần thay |
| `BEAN_NOT_WRAPPED` | bean có nhưng không được bọc (`exclude`, `@ConfigurationProperties`, bean kiểu `Map`/`Collection`, bean hạ tầng, prototype/scope, `wrap` thu hẹp); thông điệp nêu lý do | thêm tên vào `wrap`, bỏ khỏi `exclude`, mock cấp class, hoặc không mock bean đó |
| `BEAN_NOT_INTERCEPTABLE` | tên tường minh trong `wrap` không tồn tại hoặc không bọc được (BPP, prototype) | sửa `wrap` |
| `INVALID_DECLARATION` | field `static`/`final`; cả hai annotation trên cùng field; cấp class không có kiểu hoặc `name` đi với nhiều kiểu; `value`/`classes` đặt ở field/tham số | sửa theo thông điệp |
| `MOCK_REPLACE_UNSUPPORTED` | bean cần thay chỉ là singleton đăng ký tay, không có bean definition | tạo bean bằng `@Bean`/`@Component` hoặc không khai cấp class |
| `BEAN_WRAPPER_STUBBED` | stub (`when(...)`) vào wrapper dùng chung của bean (xem mục "Stub nhầm vào bean dùng chung") | khai field `@PerTestMock`/`@PerTestSpy` rồi stub handle đó |
| `CONTEXT_NAME_CONFLICT` | cùng tên `@ParallelSpringTest(context=...)` nhưng khác `wrap`/`exclude`/`schemas` (thường gõ nhầm tên) | một khai báo cho mỗi tên, hoặc đặt tên khác |
| `CONTEXT_LIMIT_EXCEEDED` | vượt `ptest.max-contexts` (là thông điệp của guard PT10, kèm diff cấu hình) | xem bảng PT10 ở mục 2 |
| `CONTEXT_NOT_MANAGED` | `ApplicationContext` không do lớp `@ParallelSpringTest` tạo (hoặc đã đóng) mà được hỏi binding/wrapper | chỉ dùng API của thư viện với context của lớp `@ParallelSpringTest` |
| `UNBOUND_THREAD` | thread không mang binding của test gọi bean bị bọc khi `ptest.unbound-policy=fail`, hoặc `guarded` và bean đã từng bị mock/spy | truyền binding: `ParallelTest.wrap(...)`, `TaskExecutor` của Spring, header HTTP binding |
| `BINDING_EXPIRED` | request HTTP mang header binding của test đã kết thúc (`ServletException` bọc `Config`) | chờ request xong trước khi test kết thúc |
| `DB_PROVISION_FAILED` | không cấp được DB (không khởi động được container Postgres, `CREATE DATABASE/SCHEMA` lỗi); lỗi khởi động container được cache, không thử lại cho context khác | sửa Docker/image/quyền, hoặc `ptest.db.mode=none` |
| `DIRTIES_CONTEXT_UNSUPPORTED` | `@DirtiesContext` trên lớp/method test (đóng context đang được test khác dùng) | tách `@ParallelSpringTest(context=...)` riêng; `ptest.unsupported=warn` để chỉ cảnh báo |
| `TRANSACTIONAL_RANDOM_PORT_UNSUPPORTED` | `@Transactional` với `RANDOM_PORT`/`DEFINED_PORT`: request chạy trên thread server, ngoài transaction của test nên không rollback | dữ liệu riêng từng test và dọn tường minh; `ptest.unsupported=warn` để chỉ cảnh báo |

Metadata giữ khi thay định nghĩa: tên, alias, `@Primary`, `@Fallback` (Spring 6.2+), autowire-candidate, `@Qualifier` ở lớp/method `@Bean`/metadata.
Không giữ: `@DependsOn`, init/destroy-method, scope, lazy (mock không cần).

Bean mock mà test không khai handle (gọi lúc khởi động, hoặc từ thread không binding khi chính sách là `real`) nhận mặc định Mockito (null/0/false).

Mock cấp class chạy qua `ptest.unbound-policy` như mọi bean bọc; spy bên trong proxy AOP cũng theo chính sách đó: thread nền mất binding giữa chừng
(vd advice `@Async` với executor không có TaskDecorator) không âm thầm chạy bản thật khi bean đang bị test nào đó spy (mặc định `guarded`).

`@Timeout(threadMode = SEPARATE_THREAD)` được hỗ trợ cho `@Test`, `@TestTemplate`, `@BeforeEach`, `@AfterEach` và `@TestFactory`: thư viện gắn lại binding trên thread chạy method (có IT). Từng dynamic test cũng được bọc để tự bind lại binding của lần gọi factory, vì JUnit có thể chạy chúng trên thread khác thread gọi factory khi chạy song song (`DynamicBindings`; IT `DynamicFactoryIT`, unit test `DynamicBindingsTest`). `@BeforeAll`/`@AfterAll` không có binding của test nào nên không thuộc phạm vi. `@ParameterizedTest`: tham số
`@PerTestMock` phải đặt SAU các tham số do nguồn đối số cấp (JUnit chỉ giao cho resolver của nó các chỉ số có đối số); đặt chồng chỉ số là lỗi cạnh tranh
ParameterResolver của JUnit.

### Stub nhầm vào bean dùng chung

`when(<field @Autowired>.x()).thenReturn(...)` stub wrapper dùng chung: bị chặn bằng `BEAN_WRAPPER_STUBBED` kèm dòng stub, ở cuối test đã gọi wrapper hoặc ở lời gọi kế
tiếp KHỚP stub (lời gọi đó nhận lỗi, không nhận giá trị stub; lời gọi không khớp đi bình thường). Ngay khi phát hiện, wrapper được `Mockito.reset` (giữ settings/listener)
nên lỗi chỉ rơi vào một lời gọi/test, không lan sang các test sau.

## 7. Phiên bản và cấu hình DB

Hỗ trợ Spring Boot 3.3+ (Spring Framework 6.1+, JDK 17+) và Spring Boot 4.x (Framework 7). Khác biệt theo phiên bản:

| Hành vi | Spring Framework < 6.2 (Boot 3.3) | Spring Framework 6.2+ / 7 |
|---|---|---|
| `ThreadPoolTaskScheduler` nhận `TaskDecorator` | không (WARN, bọc bằng `ParallelTest.wrap`) | có |
| `@Fallback` giữ khi thay định nghĩa | không có khái niệm này | giữ |
| `JdbcConnectionDetails` (nhận `@ServiceConnection`) | package `...boot.autoconfigure.jdbc` | package `...boot.jdbc.autoconfigure` (thư viện dò cả hai) |

`ptest.db.mode=postgres` BẮT BUỘC Testcontainers >= 2.0: artifact `org.testcontainers:testcontainers-postgresql`, lớp
`org.testcontainers.postgresql.PostgreSQLContainer` (Testcontainers 1.x dùng package `org.testcontainers.containers` nên không được hỗ trợ).

`@MockitoBean`/`@MockitoSpyBean` của Spring (6.2+) chỉ dùng trong vài demo/IT đối chiếu, không bắt buộc.

DB do thư viện cấp (`ptest.db.mode`): cỡ pool mặc định `ptest.db.pool-size` = `max(4, số thread song song của JUnit)` để PT11 không hỏng khi chạy song song
nhiều hơn 4 (đặt `ptest.db.pool-size` để ghi đè). H2: `ptest.db.h2.options` thêm vào URL (vd `MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`) cho code viết cho Postgres.
Hikari mặc định `minimumIdle` = `maximumPoolSize`, nghĩa là mỗi context giữ trọn pool kết nối (20 context x 24 = 480). Khi thư viện cấp DB nó đặt thêm
`spring.datasource.hikari.minimum-idle=1` (mức ưu tiên thấp nhất, ghi đè bằng property của bạn). Postgres: container singleton khởi động với
`max_connections` = `ptest.db.postgres.max-connections` (mặc định 300; context đầu tiên của JVM quyết định); thư viện ghi WARN nếu
`ptest.max-contexts` x cỡ pool vượt giá trị đó (trường hợp xấu nhất khi mọi pool đầy). Nhiều fork surefire = nhiều container, mỗi cái có `max_connections` riêng.
`ptest.report.exit-wait-millis` (mặc định 20000) là thời gian tối đa hook cuối JVM chờ context đóng để ghi báo cáo thoát.

Thư viện chỉ lo `spring.datasource.*` mặc định. Nếu `spring.datasource.url` đến từ nguồn khác (file `application.yml`, `@DynamicPropertySource`, `@ServiceConnection`),
thư viện KHÔNG cấp DB và ghi INFO tên nguồn đó: các context cùng url dùng chung MỘT database (mất cô lập dữ liệu giữa context). Multi-datasource tự đặt
(`@Bean DataSource` khác, `spring.datasource.<tên>.*`) hoàn toàn do ứng dụng quản lý.
Postgres cô lập theo schema dùng `currentSchema=<tên>,public` (hàm/extension trong `public` vẫn tìm thấy; bảng mới nằm ở schema riêng).

Báo cáo surefire: bản 3.2.x (mặc định của Boot 3.3) gán sai tên lớp/số test vào file XML khi JUnit chạy song song (lỗi của surefire, không phải của thư viện); dùng
maven-surefire-plugin 3.5.x (pom cha ghim 3.5.6). Bằng chứng đúng của thư viện là `target/ptest-reports/*.properties`.
