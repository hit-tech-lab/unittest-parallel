# Cấu hình `ptest.*`

Mọi key cấu hình của thư viện, chia thành hai nhóm có NƠI ĐẶT khác nhau. Đặt sai nhóm vào sai chỗ thì key không có tác dụng.

| Key | Nhóm | Đặt ở đâu | Mặc định | Ý nghĩa |
|---|---|---|---|---|
| `ptest.db.mode` | theo context | `application.yml`, `@SpringBootTest(properties=...)`, `-D` | `auto` | `auto`, `h2`, `postgres`, `none`: chế độ cấp DB |
| `ptest.db.isolation` | theo context | như trên | `database` | `database` hoặc `schema` (chỉ Postgres) |
| `ptest.db.h2.options` | theo context | như trên | rỗng | phần options nối vào URL H2 |
| `ptest.db.postgres.image` | theo context | như trên | `postgres:16.4` | image Postgres; container là singleton của JVM, context đầu tiên quyết định |
| `ptest.db.postgres.max-connections` | theo context | như trên | `300` | `max_connections` của container Postgres |
| `ptest.db.pool-size` | theo context | như trên | `max(4, parallelism)` | cỡ pool kết nối của mỗi context |
| `ptest.unbound-policy` | theo context | như trên | `guarded` | `guarded`, `fail`, `real`: thread không binding gọi bean can thiệp |
| `ptest.max-contexts` | toàn cục | `spring.properties` hoặc `-D` | `8` | số context đang hoạt động tối đa trong JVM |
| `ptest.unsupported` | toàn cục | như trên | `fail` | `fail` hoặc `warn` với cấu hình không hỗ trợ |
| `ptest.report.dir` | toàn cục | như trên | không đặt | thư mục ghi báo cáo |
| `ptest.report.exit-wait-millis` | toàn cục | như trên | `20000` | thời gian tối đa hook cuối JVM chờ context đóng |
| `ptest.guard.enabled` | toàn cục | như trên | `true` | `false` tắt mọi luật guard |
| `ptest.guard.<PTxx>` | toàn cục | như trên | theo luật | `fail`, `warn`, `off` cho từng luật |
| `ptest.guard.forbidden-calls` | toàn cục | như trên | rỗng | thêm lời gọi bị cấm của PT07 |
| `ptest.guard.scan-exclude` | toàn cục | như trên | rỗng | package bỏ qua khi quét cả source set |

Chi tiết từng nhóm: [Yêu cầu và cài đặt](requirements-and-setup.md#bảng-cấu-hình), [Cơ sở dữ liệu](database.md), [Guard](guard.md).

## Nhóm theo context

Đọc từ `Environment` của chính context nên đặt được trong `application.yml` của test:

```yaml
ptest:
  db:
    mode: h2
    h2:
      options: ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
    pool-size: 8
  unbound-policy: guarded
```

hoặc `@SpringBootTest(properties = "ptest.unbound-policy=fail")`. IDE gợi ý nhóm này nhờ file
`META-INF/additional-spring-configuration-metadata.json` của thư viện.

## Nhóm toàn cục

Đọc qua `SpringProperties` (không qua `Environment`): `spring.properties` ở gốc classpath (`src/test/resources`) trước, rồi system property.

```properties
# src/test/resources/spring.properties
ptest.max-contexts=12
ptest.guard.PT10=warn
ptest.report.dir=target/ptest-reports
```

hoặc `-Dptest.max-contexts=12` (ví dụ qua `argLine` của surefire).

**Đặt nhóm toàn cục trong `application.yml` bị bỏ qua.** Khi tạo context, nếu thư viện thấy key toàn cục trong `Environment`
mà `SpringProperties` không có thì ghi WARN (một lần mỗi key mỗi JVM):

```
ptest.max-contexts là cấu hình TOÀN CỤC, không có tác dụng khi đặt trong application.yml/properties của test; hãy đặt trong spring.properties (classpath) hoặc -Dptest.max-contexts=...
```

Context vẫn được tạo bình thường; chỉ giá trị đó không được áp dụng.

## Key nội bộ

`ptest.context.key` và `ptest.context.name` do thư viện tự ghi vào `Environment` để định danh context; không phải cấu hình của người dùng.
