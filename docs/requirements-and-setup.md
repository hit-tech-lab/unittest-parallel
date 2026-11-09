# Yêu cầu và cài đặt

## Yêu cầu

| Thành phần | Bản Spring Boot 3 (bản này) | Bản Spring Boot 4 |
|---|---|---|
| Spring Boot | 3.3+ (đã kiểm với 3.3.5) | 4.1 |
| Spring Framework | 6.1+ | 7 |
| Java | 17+ | 25 |
| JUnit | 5.x (đã kiểm với 5.10.5) | 6 |
| Mockito | 5 trở lên, mock maker inline (mặc định của Mockito 5) | giống bản Boot 3 |
| Testcontainers | 2.0+ (chỉ khi `ptest.db.mode=postgres`) | giống bản Boot 3 |
| Web | chỉ Servlet (không WebFlux) | giống bản Boot 3 |

- Mockito: wrapper của bean và host của spy là mock của lớp cụ thể, kể cả lớp `final`. Thư viện kiểm tra lúc tạo
  context đầu tiên và ném `CONFIG` nếu không mock được lớp `final` (Mockito 4 hoặc mock maker subclass).
- Testcontainers 2.0+: artifact `org.testcontainers:testcontainers-postgresql`, lớp
  `org.testcontainers.postgresql.PostgreSQLContainer`. Testcontainers 1.x không được hỗ trợ.
- WebFlux không có cơ chế truyền binding qua HTTP; xem [Giới hạn đã biết](limitations.md).

### Khác biệt theo phiên bản Spring

| Hành vi | Spring Framework < 6.2 (Boot 3.3) | Spring Framework 6.2+ / 7 |
|---|---|---|
| `ThreadPoolTaskScheduler` nhận `TaskDecorator` | không (WARN, bọc bằng `ParallelTest.wrap`) | có |
| `@Fallback` được giữ khi thay định nghĩa bean | không có khái niệm này | giữ |
| `@MockitoBean`/`@MockitoSpyBean` của Spring | không có | có |
| `JdbcConnectionDetails` (nhận `@ServiceConnection`) | package `org.springframework.boot.autoconfigure.jdbc` | package `org.springframework.boot.jdbc.autoconfigure` (thư viện dò cả hai) |

Bản Spring Boot 3 không có `@MockitoBean`/`@MockitoSpyBean`/`@Fallback` (Spring < 6.2); tài liệu này chỉ nhắc tới chúng
khi so sánh.

## Dependency (bản Spring Boot 3)

```xml
<dependency>
    <groupId>io.hit.research</groupId>
    <artifactId>parallel-test-lib</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

Thư viện kéo theo `spring-boot-test`, `spring-test`, `spring-context`, `mockito-core`, `junit-jupiter-api`,
`junit-platform-launcher`, `slf4j-api`. Các phần tùy chọn, consumer tự khai báo khi cần:

| Cần cho | Dependency |
|---|---|
| Database riêng bằng H2 (mặc định `auto`) | `com.h2database:h2` |
| Database riêng bằng Postgres | `org.postgresql:postgresql`, `org.testcontainers:testcontainers-postgresql` (khai báo version, Boot 3.3 chưa quản lý Testcontainers 2.x; mẫu dùng `2.0.5`) |
| Truyền binding qua HTTP | `spring-boot-starter-web` (Servlet) |
| `@ServiceConnection` | `org.springframework.boot:spring-boot-testcontainers` |

## Bật JUnit song song

`src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.config.strategy=fixed
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

- `mode.default=concurrent` làm các method trong cùng lớp chạy đồng thời; thiếu dòng này chỉ các lớp chạy song song.
- Bộ test mẫu `lib-tests` dùng `parallelism=24` để nhiều test hẹn nhau ở rào chắn cùng lúc; bộ test thật nên dùng
  số nhỏ (4 đến 8, xem [Số đo](benchmarks.md)).
- Surefire truyền `configurationParameters` cho JUnit; guard đọc đúng giá trị JUnit thực sự thấy.

## Surefire

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.6</version>
    <configuration>
        <argLine>-Xshare:off -Dfork.number=${surefire.forkNumber}</argLine>
        <includes>
            <include>**/*IT.java</include>
        </includes>
        <systemPropertyVariables>
            <ptest.report.dir>${project.build.directory}/ptest-reports</ptest.report.dir>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

- Surefire 3.2.x (mặc định của Boot 3.3) gán sai tên lớp/số test vào báo cáo XML khi JUnit chạy song song; dùng 3.5.x.
- `-Dfork.number=${surefire.forkNumber}`: tên database có số fork, cần khi `forkCount` > 1 (mặc định `fork.number=0`).
- `ptest.report.dir` bật việc ghi `*.properties` báo cáo (mức song song thực, số context, DB còn sót); bỏ nếu không cần.
- Mockito agent: Mockito bản mới cho phép nạp bằng `-javaagent:<đường dẫn mockito-core.jar>`. Bản Mockito 5.11 đi kèm
  Boot 3.3.5 có jar chưa dùng được làm javaagent, nên mẫu để Mockito tự attach; thư viện ghi một dòng WARN
  "Mockito is not loaded with -javaagent" và vẫn chạy.

## Bảng cấu hình

Hai nhóm đọc từ hai nơi khác nhau.

**Toàn cục** (đọc qua `SpringProperties`: file `spring.properties` ở gốc classpath TRƯỚC, rồi system property `-D`):

| Khóa | Mặc định | Tác dụng |
|---|---|---|
| `ptest.max-contexts` | `8` | số context đang hoạt động tối đa trong JVM; vượt thì lỗi PT10 |
| `spring.test.context.cache.maxSize` | `32` (của Spring) | phải >= `ptest.max-contexts`, nếu không thư viện ném `CONFIG` (Spring sẽ đóng context đang dùng) |
| `ptest.unsupported` | `fail` | `warn`: chỉ cảnh báo `@DirtiesContext`, `@Transactional` + cổng thật |
| `ptest.report.dir` | không đặt | thư mục ghi báo cáo |
| `ptest.report.exit-wait-millis` | `20000` | thời gian tối đa hook cuối JVM chờ context đóng để ghi báo cáo |
| `fork.number` | `0` | số fork, đưa vào tên database |
| `ptest.guard.*` | xem [Guard](guard.md) | mức từng luật |

**Theo context** (đọc từ `Environment` của chính context: `@SpringBootTest(properties = ...)`, system property, file cấu hình):

| Khóa | Mặc định | Tác dụng |
|---|---|---|
| `ptest.unbound-policy` | `guarded` | `guarded`, `fail`, `real`: xem [Web và thread nền](web-and-background-threads.md) |
| `ptest.db.mode` | `auto` | `auto`, `h2`, `postgres`, `none`: xem [Cơ sở dữ liệu](database.md) |
| `ptest.db.isolation` | `database` | `database` hoặc `schema` (chỉ Postgres) |
| `ptest.db.pool-size` | `max(4, parallelism)` | `maximum-pool-size` của DB do thư viện cấp |
| `ptest.db.h2.options` | rỗng | phần mở rộng URL H2, vd `MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE` |
| `ptest.db.postgres.image` | `postgres:16.4` | image của container Postgres |
| `ptest.db.postgres.max-connections` | `300` | `max_connections` của container |

Thư viện tự đăng ký qua `META-INF/spring.factories` (`ContextCustomizerFactory`) và `ServiceLoader` của JUnit
(listener báo cáo, dọn dẹp cuối phiên); không cần thêm `@ExtendWith` hay `@ContextConfiguration`.
