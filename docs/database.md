# Cơ sở dữ liệu

Mỗi context có database riêng để các lớp test khác context không đụng dữ liệu của nhau. Các test trong CÙNG một context
vẫn dùng chung database đó, nên dữ liệu của từng test phải có khóa riêng (xem [Guard](guard.md)).

## Khi nào thư viện cấp database

Thư viện chỉ cấp DB khi context hỏi `spring.datasource.url` VÀ chưa ai cung cấp url. Giá trị của thư viện nằm ở độ ưu tiên
thấp nhất trong `Environment`. Context không có `DataSource` thì không bao giờ hỏi nên không được cấp DB.

`ptest.db.mode` (theo context, xem [Yêu cầu và cài đặt](requirements-and-setup.md#bảng-cấu-hình)):

| Giá trị | Hành vi |
|---|---|
| `auto` (mặc định) | H2 nếu `org.h2.Driver` có trên classpath, ngược lại không cấp. KHÔNG bao giờ tự chọn Postgres |
| `h2` | H2 in-memory |
| `postgres` | Postgres bằng Testcontainers |
| `none` | không cấp DB |

Giá trị khác là lỗi `CONFIG` (`auto|h2|postgres|none`).

## H2

Mỗi context một URL riêng: `jdbc:h2:mem:<tên duy nhất>;DB_CLOSE_DELAY=-1`, trong đó tên gồm số fork, 8 ký tự uuid và khóa
context (chữ thường, tối đa 60 ký tự). Không dùng chung giữa context hay fork. Code viết cho Postgres thì thêm tùy chọn
URL H2 bằng `ptest.db.h2.options`:

```java
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "ptest.db.h2.options=MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ParallelSpringTest(context = "pg-like")
class PgLikeIT { }
```

Schema tùy chỉnh phải tồn tại trước khi Hibernate chạy ddl (vd entity `@Table(schema = "audit")`): khai báo ở `schemas`,
thư viện chạy `CREATE SCHEMA IF NOT EXISTS` trước.

```java
@SpringBootTest(classes = AuditApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "audit", schemas = "audit")
class CustomSchemaIT {

    @Autowired AuditRecordRepository repository;

    @RepeatedTest(4)
    void entityInCustomSchemaWorks() {
        AuditRecord saved = repository.save(new AuditRecord("n-" + UUID.randomUUID()));
        assertThat(repository.findById(saved.getId())).isPresent();
    }
}
```

## Postgres (Testcontainers)

Cần `org.postgresql:postgresql` và `org.testcontainers:testcontainers-postgresql` 2.0+ (xem
[Yêu cầu và cài đặt](requirements-and-setup.md)) và Docker. Bật cho cả bộ test trong surefire:

```xml
<systemPropertyVariables>
    <ptest.db.mode>postgres</ptest.db.mode>
    <ptest.db.isolation>database</ptest.db.isolation>
    <ptest.db.pool-size>8</ptest.db.pool-size>
    <ptest.db.postgres.max-connections>300</ptest.db.postgres.max-connections>
</systemPropertyVariables>
```

| Khóa | Mặc định | Ghi chú |
|---|---|---|
| `ptest.db.mode` | `auto` | đặt `postgres` |
| `ptest.db.isolation` | `database` | `database`: mỗi context một database (`CREATE DATABASE`); `schema`: mỗi context một schema (`currentSchema=<tên>,public`, hàm/extension trong `public` vẫn tìm thấy) |
| `ptest.db.pool-size` | `max(4, parallelism)` | `maximum-pool-size` Hikari của DB do thư viện cấp |
| `ptest.db.postgres.image` | `postgres:16.4` | container là singleton của JVM: image của context đầu tiên quyết định, image khác ở context sau bị bỏ qua (có WARN) |
| `ptest.db.postgres.max-connections` | `300` | `max_connections` của container; context đầu tiên của JVM quyết định |

- MỘT container cho cả JVM. Nhiều fork surefire thì mỗi fork một container, mỗi cái có `max_connections` riêng.
- Context khai `schemas` luôn dùng database riêng (kể cả khi `isolation=schema`), vì schema phải tồn tại trước ddl và tên
  schema trong entity là cố định.
- Thư viện đặt `spring.datasource.hikari.minimum-idle=1` cho DB do nó cấp (Hikari mặc định `minimumIdle` bằng
  `maximumPoolSize`, nên mỗi context sẽ giữ trọn pool). Ghi đè bằng property của bạn.
- `max_connections` và số context: trường hợp xấu nhất là `ptest.max-contexts x ptest.db.pool-size` kết nối (vd 20 context x 24 = 480).
  Vượt `ptest.db.postgres.max-connections` thì thư viện ghi WARN
  `ptest.max-contexts (..) x pool size (..) = .. may exceed Postgres max_connections=..`; tăng `max-connections` hoặc giảm
  pool/số context. Số đo kết nối thực tế ở [Số đo](benchmarks.md).
- Lỗi khởi động container được cache: mọi context sau đó thất bại ngay với `DB_PROVISION_FAILED`, không thử lại. Sửa Docker/image
  rồi chạy lại, hoặc `ptest.db.mode=none`.
- Database/schema được xóa khi context đóng; cuối phiên JUnit thư viện đóng chủ động các context còn lại trong khi container
  còn sống, và báo cáo (`ptest.report.dir`) ghi số DB còn sót.

## Tự cung cấp kết nối

Nếu `spring.datasource.url` đến từ nguồn khác thì thư viện KHÔNG cấp DB và KHÔNG áp username/password/pool của nó, chỉ ghi INFO
tên nguồn. Các context cùng url dùng chung MỘT database (mất cô lập dữ liệu giữa context), nên chỉ nên dùng khi có chủ ý.

`@DynamicPropertySource`:

```java
@SuppressGuard(rules = "PT11", reason = "một test method; DataSource của người dùng giữ pool Hikari mặc định")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "dyn-prop")
class DynamicPropertyContainerIT {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.4");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void dataSourceIsTheUsersContainer() {
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }
}
```

`@ServiceConnection` (hoặc Docker Compose, tức bean `JdbcConnectionDetails`): thư viện nhận ra và không cấp DB.

```java
@SuppressGuard(rules = "PT11", reason = "một test method; DataSource của người dùng giữ pool Hikari mặc định")
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "svc-conn")
class ServiceConnectionIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.4");

    static {
        POSTGRES.start();   // container của chính test, không phải của thư viện
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void dataSourceIsTheUsersContainer() {
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
    }
}
```

Ba lưu ý:

- Chỉ đặt username/password mà không đặt url: thư viện vẫn cấp DB và giá trị của bạn ghi đè giá trị của nó (thường gây lỗi đăng nhập);
  hãy đặt cả url.
- Pool do bạn sở hữu nên PT11 (pool nhỏ hơn số thread song song) vẫn kiểm: tự đặt `maximum-pool-size` đủ lớn, hoặc `@SuppressGuard`
  có lý do như trên khi chỉ có một test method.
- Multi-datasource tự đặt (`@Bean DataSource` khác, `spring.datasource.<tên>.*`) hoàn toàn do ứng dụng quản lý.

## `DataSource` bị bọc proxy

Guard SQL bọc mỗi `DataSource` thành proxy theo interface. Tiêm `DataSource` thì dùng được, kể cả
`dataSource.unwrap(HikariDataSource.class)`; tiêm kiểu cụ thể (`HikariDataSource`) lỗi lúc khởi động.
