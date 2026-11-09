# Giới hạn đã biết của thư viện parallel test

Danh sách tạm các hành vi KHÔNG khắc phục được (hoặc không đáng khắc phục) và đã được kiểm chứng. Mỗi mục nêu nguyên nhân, mức ảnh hưởng và cách xử lý cho người dùng.

## 1. Spy chụp field một lần lúc tạo wrapper

- Wrapper của bean có spy (bean không bị proxy AOP) được tạo bằng `Mockito.mock(..., spiedInstance(beanThật))`, tức SAO CHÉP giá trị field của bean thật MỘT LẦN, lúc tạo wrapper.
- Hệ quả: nếu SAU đó code gán lại field `public`/package của bean thật (setter chạy muộn, `@PostConstruct` chạy sau khi wrapper đã tạo...), việc ĐỌC THẲNG field qua bean đã tiêm vẫn thấy giá trị cũ. Trong Spring thường bean tiêm chính là bean thật nên thấy giá trị mới.
- Gọi qua method (getter) không bị ảnh hưởng: lời gọi được định tuyến tới bản thật hoặc spy của test.
- Không cải thiện được: truy cập field trực tiếp không đi qua dispatch của Mockito nên không có điểm chặn nào để chuyển tiếp tới bản thật. Cách xử lý: đọc state qua method, không đọc field `public` đã bị gán lại sau khi context sẵn sàng.
- Có test ghi nhận: `WrapperFieldCopyTest.knownLimitFieldReassignedAfterTheWrapperWasBuiltIsNotSeenThroughDirectFieldReads`.

## 2. `ForkJoinPool.managedBlock` và số test chạy đồng thời

- Khi một test chờ bằng cơ chế được `ForkJoinPool` quản lý (`managedBlock`, ví dụ `CountDownLatch`/`CyclicBarrier` dùng qua cách này), pool JUnit được cấp thêm luồng bù: số test chạy chồng nhau có thể vượt `parallelism`.
- Hệ quả: một test chờ test khác KHI ĐANG giữ kết nối DB (vd `@Transactional` hoặc giữ `Connection` mở) cộng với luồng bù có thể làm số kết nối đang giữ vượt pool và gây timeout lấy kết nối (mặc định Hikari 30 giây). Nếu chỉ giữ kết nối trong lúc gọi JDBC và chờ ở ngoài giao dịch thì không gặp.
- Bài học cho người dùng:
  - Không chờ test khác (latch, barrier, điểm hẹn) bên trong giao dịch hoặc khi đang giữ kết nối; nếu bắt buộc, đặt `ptest.db.pool-size` >= số thành viên tối đa của điểm hẹn (mặc định của thư viện là `max(4, parallelism)`).
  - Điểm hẹn giữa các lần lặp / test sinh động (`@RepeatedTest`, `@ParameterizedTest`, `@TestFactory`) chỉ chạy được khi số thành viên <= parallelism; vượt thì các thành viên đầu chờ mãi các thành viên chưa được xếp lịch.
  - Ở parallelism thấp (vd 2), JUnit có thể chạy các lần lặp/test sinh động inline trên cùng thread nên điểm hẹn giữa chúng càng dễ kẹt; không nên viết test phụ thuộc chúng chạy đồng thời với nhau.

## 3. Thứ tự method làm test cùng lớp chạy tuần tự

- Bất kỳ `@TestMethodOrder` nào (`MethodName`, `Random`, `OrderAnnotation`, ...) làm các method của cùng một lớp chạy TUẦN TỰ, dù bật chạy song song theo method, trừ khi lớp có `@Execution(CONCURRENT)` tường minh. Đã đo thực nghiệm ở JUnit 5.10.5, 5.11.4 và 6.0.3.
- Hệ quả: test trong cùng lớp chờ nhau (latch, barrier, điểm hẹn) sẽ kẹt vì method đợi nằm trước method được chờ; test không chờ nhau chỉ mất độ song song trong lớp đó.
- Cách xử lý: không đặt `@TestMethodOrder` trên lớp cần song song theo method, hoặc thêm `@Execution(CONCURRENT)` vào lớp đó.

## 4. Mock cấp class so với `@MockBean`

- Giống: mock được tạo ngay lúc dựng context và đăng ký như singleton có sẵn kèm định nghĩa (`registerSingleton` sau `registerBeanDefinition`, như `@MockBean`), nên KHÔNG đi qua BeanPostProcessor nào: không bind `@ConfigurationProperties` (bean chỉ có getter không còn lỗi "No setter found"), không tiêm `@Autowired`, không chạy `@PostConstruct`, không bọc proxy AOP (`@Transactional`/`@Async`/`@Cacheable`). Định nghĩa giữ lại nên tra theo kiểu, `@Qualifier`, `@Primary`, `@Fallback` và alias vẫn đúng.
- Còn khác `@MockBean`: (1) bean mock tạo mới không có lớp trong định nghĩa nên `@ConditionalOnBean(MockedType)` của auto-configuration không thấy nó; (2) bean thay thế chỉ giữ tên, alias, `@Primary`, `@Fallback`, `@Qualifier`: `@DependsOn`, init/destroy-method và scope của bản thật bị bỏ; (3) bean tiêm vào test là một wrapper dùng chung cả context: stub qua handle `@PerTestMock` của từng test, còn `when(<field @Autowired>.x())` bị phát hiện bằng `BEAN_WRAPPER_STUBBED` (`@MockBean` thì stub thẳng và được reset sau mỗi test).
- Tests ghi nhận: `ClassLevelMockTest.classLevelMocksOfConfigurationPropertiesBeansSkipPostProcessing`, `ConfigPropsGetterOnly*IT`, `MockSkipsPostProcessing*IT`.

## Điều kiện cần theo dõi

Không phải hạn chế ở các phiên bản JUnit đã kiểm (5.10.5, 5.11.4, 6.0.3); ghi lại hành vi hiện tại và điều thư viện sẽ báo nếu JUnit đổi.

### `@BeforeAll` / `@AfterAll` với `@Timeout(threadMode = SEPARATE_THREAD)`

- Hiện tại: JUnit vẫn chạy `@BeforeAll`/`@AfterAll` trên thread của lớp, không chuyển sang thread riêng dù đặt `@Timeout(SEPARATE_THREAD)` hay `junit.jupiter.execution.timeout.thread.mode.default=SEPARATE_THREAD`. Binding cấp lớp luôn ở đúng thread nên thư viện không cần can thiệp `beforeAll`/`afterAll`.
- Nếu JUnit đổi và chạy lifecycle method toàn lớp ở thread khác: method đó chạy KHÔNG binding (chính sách thread không binding: `GUARDED` ném `UNBOUND_THREAD` chỉ khi bean đã bị test khác mock/spy). Khi đó cần thêm interceptor gắn lại binding cấp lớp, như đang làm cho method của từng test.

### Metadata của dynamic test

- Hiện tại: `DynamicBindings` dựng lại `DynamicTest`/`DynamicContainer` để gắn binding; API công khai của `DynamicNode` chỉ có tên hiển thị, URI nguồn và executable/con, và cả hai được giữ nguyên.
- Nếu JUnit thêm metadata cho node (vd execution mode theo node): `DynamicBindingsTest.junitDynamicNodeApiHasNoMetadataBeyondWhatDynamicBindingsPreserves` sẽ HỎNG để buộc cập nhật `DynamicBindings`, thay vì lặng lẽ làm mất metadata.
