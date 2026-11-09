# Chạy bộ test mẫu

Repository gồm hai module Maven:

| Module | Vai trò |
|---|---|
| `parallel-test-lib` | thư viện `com.hit.ptest` và test đơn vị của nó |
| `lib-tests` | ứng dụng mẫu `demo.core.*`, `demo.project.*`, `demo.audit.*` và bộ test tích hợp (`*IT`) chỉ dùng API công khai; `*Demo` là đối chứng âm cố ý fail |

Các ví dụ trong tài liệu lấy từ `lib-tests`.

## Xác minh toàn bộ: `scripts/verify.sh`

```bash
JAVA_HOME=/path/to/jdk-17 scripts/verify.sh
```

Yêu cầu: JDK 17 (script dừng nếu phiên bản chính khác 17), Maven (`./mvnw` trong repo nếu có, ngược lại `mvn` trong `PATH`, hoặc biến `MVN`),
`python3`, và Docker cho các chế độ Postgres. Biến môi trường:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `JAVA_HOME` | | JDK 17 |
| `MVN` | `./mvnw` hoặc `mvn` | lệnh Maven |
| `RUNS` | `2` | số lần chạy lặp chế độ H2 |
| `SKIP_PG` | không đặt | `1` bỏ qua các chế độ Postgres (không có Docker); script ghi rõ chúng CHƯA được xác minh. Thiếu Docker mà không đặt `SKIP_PG=1` thì script dừng, không im lặng bỏ qua |

Các bước script chạy; mỗi bước build cả `parallel-test-lib` (in một dòng `Tests run` cho test đơn vị rồi một dòng cho `lib-tests`) và kiểm báo cáo
bằng `scripts/check_reports.py` (số test, số context chính xác, vi phạm guard, DB không sót):

| Bước | Cấu hình |
|---|---|
| `h2-1` ... `h2-<RUNS>` | toàn bộ `*IT`, H2, cô lập `database` |
| `auto-h2` | các test nhóm `auto-default` với `ptest.db.mode` không đặt (chế độ `auto`) |
| `forks-h2` | `Aop*IT`, `PostConstructIT`, `CustomSchemaIT`, `UnboundThread*IT` với 2 fork surefire |
| `demo-h2` | profile `conflict-demo`: các `*Demo` PHẢI fail đúng mã luật, đúng vị trí; control hợp lệ PHẢI pass |
| `pg-database` | toàn bộ `*IT`, Postgres, cô lập `database` |
| `pg-schema` | toàn bộ `*IT`, Postgres, cô lập `schema`, `max_connections=500` |
| `forks-pg` | tập con như `forks-h2` trên Postgres, 2 fork |

Log từng bước: `lib-tests/target/verify-logs/<bước>.log`. Thành công in `ALL CHECKS PASSED`.

## Chạy riêng bằng Maven

```bash
# test đơn vị của thư viện + bộ IT của lib-tests (H2)
mvn -B -ntp -pl lib-tests -am verify

# Postgres, cô lập theo schema
mvn -B -ntp -pl lib-tests -am verify -Dptest.db.mode=postgres -Dptest.db.isolation=schema

# đối chứng âm (các *Demo phải fail; failureIgnore bật sẵn trong profile)
mvn -B -ntp -pl lib-tests -am verify -Pconflict-demo -Dptest.max-contexts=20
```

Thuộc tính của `lib-tests/pom.xml` có thể ghi đè bằng `-D`: `ptest.db.mode` (`h2`|`postgres`), `ptest.db.isolation` (`database`|`schema`),
`ptest.max-contexts`, `ptest.db.postgres.max-connections`, `test.parallelism`, `test.forks`, `test.includes`.

`lib-tests` đặt `ptest.max-contexts=40`, `spring.test.context.cache.maxSize=64`, `test.parallelism=24`; cấu hình này chỉ để chạy nhiều test chồng nhau,
không phải khuyến nghị cho dự án thật (xem [Yêu cầu và cài đặt](requirements-and-setup.md)).
