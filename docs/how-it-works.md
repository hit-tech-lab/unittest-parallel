# Cách hoạt động

Trang này giải thích đủ để đoán đúng hành vi khi gặp tình huống ngoài ví dụ; không phải API.

## Khi context được tạo

`META-INF/spring.factories` đăng ký `ParallelContextCustomizerFactory`: với lớp có `@ParallelSpringTest` Spring Test thêm
`ParallelContextCustomizer` vào cấu hình. Customizer chạy một lần mỗi context mới (cache miss) và đăng ký:

- vòng đời context (đếm theo `ptest.max-contexts`, dọn DB khi đóng) và nguồn thuộc tính DB riêng (xem [Cơ sở dữ liệu](database.md));
- ba `BeanPostProcessor`: `TaskDecoratorInstaller`, `SpyHostPostProcessor`, `InterceptingBeanPostProcessor`;
- `BindingFilter` (khi có Servlet), bộ chặn SQL (PT12) và đăng ký mock cấp class.

## Wrapper của bean

```
bean thật
   |
   v  SpyHostPostProcessor (TRƯỚC proxy AOP): bọc bean thật bằng "host" của spy
host
   |
   v  Spring AOP (@Transactional, @Async, @Cacheable ...)
proxy AOP
   |
   v  InterceptingBeanPostProcessor (SAU proxy AOP): bọc bean đầy đủ thành WRAPPER dùng chung
wrapper  ----->  nơi nào tiêm bean cũng nhận wrapper
```

Wrapper là Mockito mock của lớp cụ thể (kể cả `final`, nên cần Mockito inline) với câu trả lời mặc định là "định tuyến". Mỗi lời gọi:

```
wrapper.method()
   |
   v  TestBinding.current()  (ThreadLocal của thread đang chạy)
   |
   +-- binding có target cho (context, bean):
   |      MOCK -> gọi mock của test
   |      SPY  -> đi qua proxy AOP rồi host chuyển tới spy của test   (this.x() cũng qua spy)
   +-- binding không có target cho bean -> bean thật
   +-- không binding (hoặc binding đã đóng) -> ptest.unbound-policy
```

Spy nằm BÊN TRONG proxy AOP nên advice vẫn bao quanh; bean là JDK proxy (repository) không có host nên spy là mock ủy quyền cho proxy.
Chỉ bean khớp `wrap` và không bị `exclude`, là singleton do ứng dụng định nghĩa mới được bọc ([Khai báo mock/spy](mock-spy-declaration.md)).
Mock cấp class đăng ký bean mock trực tiếp, không qua hai post-processor này.

## Binding của test

`TestBinding` là bản đồ `(khóa context, tên bean) -> mock/spy` của MỘT test, gắn vào thread qua `ThreadLocal`. Vòng đời trong `ParallelTestExtension`:

| Bước | Việc làm |
|---|---|
| `beforeAll` | chặn tính năng không hỗ trợ (`@DirtiesContext`, `@Transactional` + cổng thật), chạy luật tĩnh của guard, gắn binding cấp lớp rỗng (mọi bean REAL) |
| `beforeEach` | lấy context, chờ sẵn sàng, kiểm pool (PT11), chọn bean cho field/tham số, tạo mock/spy mới cho từng bean, mở binding, gắn vào thread, tiêm vào field |
| `afterEach` | khôi phục binding trước đó, đóng phạm vi guard (ném vi phạm gán từ thread khác), kiểm tra stub nhầm, đóng binding |
| `afterAll` | khôi phục/đóng binding cấp lớp |

Binding sang thread khác: `BindingTaskDecorator` (executor Spring), header `X-PTest-Binding` + `BindingFilter` (HTTP), `ParallelTest.wrap` (thread tự tạo),
`DynamicBindings` (dynamic test). Binding đã đóng được coi như không có binding, nên task rò rỉ chạy sau khi test kết thúc không bao giờ vào mock cũ.

## Khóa context

```
<tên context>@<hex của MergedContextConfiguration.hashCode()>#<số thứ tự tạo>
vd: main@4bd57b9e#3
```

`ParallelContextCustomizer.equals/hashCode` dựa trên khai báo (`context`, `wrap`, `exclude`, `schemas`, tập `@PerTestMock` cấp class), nên khai báo
nằm trong khóa cache của Spring Test cùng cấu hình Spring (lớp cấu hình, profile, property...). Hai lớp cùng khóa dùng chung một `ApplicationContext`;
số thứ tự làm khóa duy nhất cho mỗi context được tạo. Một context chiếm "slot" của `ptest.max-contexts` từ lúc bắt đầu refresh đến khi đóng.
