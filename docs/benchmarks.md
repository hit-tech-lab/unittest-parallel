# Số đo

Số liệu từ benchmark tải lớn và vi benchmark overhead của thư viện. Đây là số của MỘT máy và MỘT lần chạy mỗi ô; dùng để
định cỡ, không phải cam kết hiệu năng.

## Điều kiện đo

- Bản đo: Spring Boot 4.1.1, JDK 25, JUnit 6.0.3. Bản Spring Boot 3 chưa được đo riêng.
- Máy 8 nhân, đang dùng chung với IDE, swap gần đầy. Sai số giữa các lần chạy khoảng 10%; mỗi ô bảng là một lần chạy.
- Bộ test sinh: 10 test mỗi lớp, 5 context profile luân phiên, lớp luân phiên REAL / SPY (`@PerTestSpy`) / MOCK (`@PerTestMock`), mỗi test ghi và đọc DB qua JPA.
- DB: H2 in-memory, hoặc Postgres 16.4 (Testcontainers, một container mỗi JVM, một database mỗi context, pool = parallelism).
- `window` = thời gian test của thư viện (từ test đầu đến test cuối, gồm khởi tạo context). Không tính khởi động JVM và container.

## Thời gian theo số test và parallelism (giây, cột `window`)

| Số test | DB | par 1 | par 4 | par 8 | par 16 |
|---:|---|---:|---:|---:|---:|
| 300 | H2 | 9,6 | 9,3 | 9,3 | 8,9 |
| 300 | Postgres | 13,8 | 12,8 | 11,4 | 11,9 |
| 1000 | H2 | 14,4 | 11,8 | 12,5 | 11,9 |
| 1000 | Postgres | 19,3 | 15,1 | 14,9 | 15,4 |
| 3000 | H2 | 19,7 | 17,8 | 15,9 | 17,4 |
| 3000 | Postgres | 30,2 | 19,0 | 20,9 | 21,4 |

Cả 24 lần chạy: 0 test fail, không còn context hay database sót, kết nối Postgres về 0 khi JVM kết thúc.

Nhận xét từ số đo:

- Thời gian giảm rõ từ parallelism 1 sang 4, gần phẳng từ 4 sang 8, parallelism 16 không nhanh hơn 8. Với 8 nhân, 4 đến 8 là hợp lý;
  16 chỉ thêm kết nối và tranh chấp.
- Chi phí cố định chiếm phần lớn khi số test nhỏ: 5 context tốn 4,4 đến 5,2 giây (H2) hoặc 6,8 đến 8,3 giây (Postgres, thêm tạo database), cộng dồn
  và gần như tuần tự vì Spring TestContext giữ khóa cache trong lúc dựng context. Context đầu tiên chậm nhất (3,3 đến 3,6 giây H2, 5,3 đến 6,3 giây Postgres).
  Muốn giảm thì giảm số context.
- Chi phí biên mỗi test khoảng 2,4 đến 3,7 ms (H2, parallelism 1 đến 8).
- Bộ nhớ: RSS đỉnh của JVM fork từ 326 MB lên 436 MB khi số test tăng 10 lần. Kiểm riêng 9000 test, H2, parallelism 8, `-Xmx192m` chạy xong và
  heap sống sau GC ổn định khoảng 75 MB.
- Kết nối Postgres tối đa thấy được là 46 ở parallelism 16 (5 context x pool 16, trần lý thuyết 80); số lấy mẫu mỗi 0,2 giây nên có thể thấp hơn đỉnh thật.
  Ước lượng cấu hình: kết nối tối đa khoảng số context x `ptest.db.pool-size`; `max_connections` phải lớn hơn (mặc định thư viện 300).
- Khoảng 18% mẫu CPU là dựng mock/spy Mockito mỗi test.

## Overhead của wrapper (vi benchmark)

Một luồng, trung vị của 5 vòng sau 3 vòng khởi động, mỗi lời gọi `FxClient.rate`; SPY/MOCK dùng 2e5 lời gọi mỗi vòng vì Mockito ghi lại mọi lời gọi.

| Cấu hình | ns mỗi lời gọi (trước tối ưu) | ns mỗi lời gọi (sau tối ưu) |
|---|---:|---:|
| gọi trực tiếp `new FxClientImpl()` | ~33 | ~33 |
| bean Spring không bọc (`wrap = {}`) | 35 | 21 |
| mock Mockito `stubOnly` thuần (không có thư viện) | 3388 | 3419 |
| wrapper REAL | 8494 | 4318 |
| wrapper SPY (`@PerTestSpy`) | 16038 | 10893 |
| wrapper MOCK (`@PerTestMock`) | 9372 | 9142 |

Chi phí của wrapper chủ yếu là chi phí nền của Mockito (khoảng 3,4 micro giây mỗi lời gọi mock), không phải mã của thư viện (dưới 1%). Bean có proxy AOP
(`@Transactional`...) vẫn phải qua host vì spy nằm trong advice. Bean không được bọc (ngoài `wrap` hoặc `exclude`) không chịu overhead này.
