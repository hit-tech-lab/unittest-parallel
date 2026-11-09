# Giới hạn đã biết

Danh sách các hành vi khác Spring Test thường hoặc không khắc phục được. Chi tiết nguyên nhân và test ghi nhận:
[LIMITS.md](../parallel-test-lib/LIMITS.md) và mục 4 của [GUARD.md](../parallel-test-lib/GUARD.md).

## Mock và spy

| Giới hạn | Mức ảnh hưởng và cách xử lý |
|---|---|
| Spy sao chép giá trị field của bean thật MỘT lần lúc tạo (như `Mockito.spy`) | field nguyên thủy/bị gán lại tách khỏi bean thật và khỏi test khác; collection hay dependency dùng chung vẫn trỏ cùng đối tượng. Đọc state qua method; đừng đọc thẳng field `public` bị gán lại sau khi context sẵn sàng |
| Field `public` của bean có spy bị gán lại SAU khi wrapper được tạo | đọc thẳng field qua bean đã tiêm vẫn thấy giá trị cũ (truy cập field không đi qua Mockito). Gọi qua method không bị ảnh hưởng |
| Stub nhầm vào bean dùng chung: `when(<field @Autowired>.x())` | không chặn được lúc `thenReturn`; phát hiện `BEAN_WRAPPER_STUBBED` kèm dòng stub ở cuối test đã gọi wrapper hoặc ở lời gọi kế tiếp khớp stub. Test khác không bao giờ nhận giá trị stub |
| `toString()` của bean được bọc là `Interceptor[<tên bean>]` | log/thông điệp lỗi thấy chuỗi này; đọc field của wrapper vẫn đúng giá trị bean thật |
| Bean là JDK proxy (repository) không có lớp thật | spy ủy quyền cho proxy (giống `@MockitoSpyBean`) |
| Tham chiếu vòng mà Spring lộ sớm bản thật của bean được bọc | không hỗ trợ (Spring báo lỗi "raw version") |
| Mock cấp class tạo bean mới | `@ConditionalOnBean(MockedType)` của auto-configuration không thấy bean đó (khác `@MockBean`); đăng ký bằng `@Bean`/`@Import` trong cấu hình test để thư viện chỉ thay |
| `@PerTestSpy` trên bean đã bị `@PerTestMock` cấp class thay/tạo | `SPY_NO_BEAN`, không còn bản thật |
| Lời gọi thứ hai vào method `@Cacheable` | được cache trả lời nên spy không thấy (như `@MockitoSpyBean`) |
| Bean không được bọc mặc định (hạ tầng, `@ConfigurationProperties`, `FactoryBean`, prototype...) | không mock được theo test, trừ khi liệt kê tên trong `wrap` |

## JUnit và song song

- Rendezvous/rào chắn giữa các lần lặp hoặc test sinh động (`@RepeatedTest`, `@ParameterizedTest`, `@TestFactory`) chỉ đảm
  bảo khi số bên tham gia <= `parallelism`; nếu không JUnit có thể chạy lần kế tiếp ngay trên thread đang bị chặn. Ở
  parallelism 2, test mẫu `RepeatedMockIT` (4 bên) lỗi vì lý do này, không liên quan tới pool. Rào chắn chỉ là công cụ của bộ test
  mẫu (`demo.support.Rendezvous`), thư viện không cung cấp.
- `ForkJoinPool.managedBlock`: pool JUnit được cấp thêm thread bù khi một test chờ bằng `managedBlock`, nên số test chạy chồng nhau có
  thể vượt `parallelism` (đo ở bộ mẫu: 21 đến 29 test đồng thời ở parallelism 4, 25 đến 34 ở parallelism 8). Test chờ điểm hẹn KHI ĐANG giữ kết nối
  (`@Transactional`, giữ `Connection` mở) có thể làm số kết nối vượt pool và timeout lấy kết nối (mặc định Hikari 30 giây): không chờ trong giao dịch, hoặc
  đặt `ptest.db.pool-size` >= số bên tối đa của rào chắn.
- Pool mặc định của DB do thư viện cấp là `max(4, parallelism)`; parallelism 1 nghĩa là không có song song thật.
- `MethodOrderer` (vd `@TestMethodOrder(OrderAnnotation.class)`): JUnit chạy các method của lớp đó tuần tự thay vì đồng thời; các lớp
  khác vẫn chạy song song với nó. Muốn chạy đồng thời thì bỏ orderer.
- `TestInstance.Lifecycle.PER_CLASS`: chỉ hỗ trợ khi method không chạy đồng thời; xem [Khai báo mock/spy](mock-spy-declaration.md).
- `@Timeout(threadMode = SEPARATE_THREAD)`: được hỗ trợ cho `@Test`, `@TestTemplate`, `@BeforeEach`, `@AfterEach`, `@TestFactory`.
  `@BeforeAll`/`@AfterAll` không có binding của test nào (binding cấp lớp rỗng, mọi bean là REAL); JUnit 5.10.5 và 6.0.3 vẫn chạy chúng
  trên thread của lớp kể cả khi có `SEPARATE_THREAD`.
- Metadata của dynamic test: `DynamicBindings` dựng lại `DynamicTest`/`DynamicContainer` để gắn binding; JUnit 5.10.5 và 6.0.3 chỉ có tên hiển thị,
  URI nguồn và executable/node con, cả hai được giữ nguyên. Bản JUnit mới thêm metadata cho node thì test của thư viện hỏng có chủ ý để buộc cập nhật.
- `@DirtiesContext` không hỗ trợ (`DIRTIES_CONTEXT_UNSUPPORTED`): nó đóng context test khác đang dùng. Tách `@ParallelSpringTest(context = "...")` riêng.
- `webEnvironment = DEFINED_PORT` không hỗ trợ (guard PT08); `@Transactional` + `RANDOM_PORT` không rollback (`TRANSACTIONAL_RANDOM_PORT_UNSUPPORTED`).

## Context và hạ tầng

- Chỉ stack Servlet: `BindingFilter` là `OncePerRequestFilter` đọc header binding. WebFlux/Netty không có cơ chế truyền binding; bean bị mock/spy bị gọi từ
  thread reactor rơi vào `ptest.unbound-policy`; dùng `ParallelTest.wrap` ở ranh giới đó.
- Executor không có `setTaskDecorator` (executor tự viết, `Executors.newFixedThreadPool` khai báo thành bean) hoặc có setter nhưng không đọc lại được
  decorator hiện tại: chỉ WARN, cần `ParallelTest.wrap`. Tác vụ lặp (fixed-rate/cron) chưa kiểm tra.
- Số context lib bị chặn bởi `ptest.max-contexts`; Spring giữ khóa cache trong lúc dựng context nên các context được dựng gần như tuần tự ([Số đo](benchmarks.md)).
- Lớp `@Nested` tự khai thêm mock cấp class chạy ở context khác lớp bao ngoài.
- `DataSource` bị bọc proxy theo interface: tiêm kiểu cụ thể (`HikariDataSource`) lỗi lúc khởi động.
- Database do thư viện cấp: lỗi khởi động container Postgres được cache và không thử lại.
- Guard không phát hiện: khóa cố định trùng giữa hai test, trạng thái ngoài DB (Kafka, Redis, file, cache). PT07/PT03/PT06/PT13/PT12 là heuristic, xem
  [GUARD.md](../parallel-test-lib/GUARD.md) mục 4.
- Phạm vi API công khai: xem [Giới thiệu](introduction.md); lớp public khác là nội bộ.
