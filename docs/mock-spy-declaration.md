# Khai báo mock/spy

Các ví dụ dùng bộ lớp `demo.core.*`, `demo.project.*` của `lib-tests` và lớp cơ sở `MainIT` ở [README](../README.md).
Mọi ví dụ `extends MainIT` giả định `MainIT` có `accounts`, `transferService`, `primaryFx` như ở đó. Đoạn không có
`package`/`import` là thân lớp hoặc thân method; import tĩnh lấy từ `Assertions`, `Mockito`, `ArgumentMatchers` như ở các ví dụ đầy đủ.

## Vị trí khai báo

| Vị trí | Ví dụ | Tác dụng | Khóa context |
|---|---|---|---|
| Field của lớp test | `@PerTestMock @Qualifier("primaryFx") ExchangeRateClient fx;` | handle: mock/spy RIÊNG của từng test, chỉ test đó và thread nó giao việc thấy | không |
| Tham số method test | `void t(@PerTestSpy TransferService h)` | như field, chỉ cho method đó (ghi đè field cùng bean) | không |
| Cấp class `@PerTestMock` | `@PerTestMock(PaymentGateway.class)` | như `@MockBean`: bean đã có thì THAY định nghĩa bằng mock, chưa có thì TẠO bean mock; mock cho CẢ context | có |
| Cấp class `@PerTestSpy` | `@PerTestSpy(ExchangeRateClient.class)` | mỗi test có sẵn một spy riêng của bean thật; lấy handle bằng field ở lớp con | không |

Mặc định mọi bean không khai báo là REAL (bean thật). Cấp class của `@PerTestMock` là thứ duy nhất làm đổi context.

## Field: mock và spy riêng của từng test

```java
package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import com.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

class ModeMockIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void mockStubIsPrivateToThisTest() {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("9.9"));

        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("9.9"); // bean đã tiêm, định tuyến tới mock của test này
        verify(mock).rate("USD", "EUR");
    }
}
```

Luôn stub và verify trên handle (`mock`), không stub trên field `@Autowired` (`primaryFx`): field đó là wrapper dùng chung.
Stub nhầm vào wrapper bị phát hiện và báo `BEAN_WRAPPER_STUBBED` kèm dòng stub (xem [Xử lý sự cố](troubleshooting.md)).

Spy chuẩn Mockito trên bean thật; `doReturn(...).when(spy)` stub một phần, phần còn lại chạy bản thật:

```java
@PerTestSpy @Qualifier("primaryFx") ExchangeRateClient spy;

@Test
void spyCallsRealAndCountsOnlyOwnCalls() {
    doReturn(new BigDecimal("3.30")).when(spy).rate("USD", "JPY");

    assertThat(primaryFx.rate("USD", "JPY")).isEqualByComparingTo("3.30");
    assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("1.10");
    verify(spy, times(1)).rate("USD", "EUR");   // chỉ đếm lời gọi của test này
}
```

(Thêm `import static org.mockito.Mockito.doReturn;` và `times`.)

## Chọn bean khi có nhiều bean cùng kiểu

Field/tham số: theo kiểu; còn nhiều bean thì lọc theo `name` hoặc `@Qualifier`, rồi `@Primary`, rồi tên field trùng tên
bean. Không xác định được là `BEAN_AMBIGUOUS`. `@Priority` không được xét.

```java
package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import demo.core.Account;
import demo.core.ExchangeRateClient;
import demo.core.TransferService;
import com.hit.ptest.PerTestMock;
import com.hit.ptest.PerTestSpy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/** Hai bean cùng kiểu ExchangeRateClient (primaryFx, backupFx), hai chế độ khác nhau trong một test. */
class ModeTwoSameTypeIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient primaryMock;   // chọn bằng @Qualifier
    @PerTestSpy(name = "backupFx") ExchangeRateClient backupSpy;           // chọn bằng name

    @Test
    void primaryFailsAndBackupIsUsed() {
        when(primaryMock.rate("USD", "EUR")).thenThrow(new IllegalStateException("primary down"));
        Account from = accounts.save(new Account("two-" + System.nanoTime(), "USD", new BigDecimal("100")));
        Account to = accounts.save(new Account("two-" + System.nanoTime(), "EUR", BigDecimal.ZERO));

        TransferService.Result r = transferService.transfer(from.getId(), to.getId(), new BigDecimal("10"));

        assertThat(r.credited()).isEqualByComparingTo("12.0000");   // 10 x 1,20 của backup thật
        verify(primaryMock).rate("USD", "EUR");
        verify(backupSpy).rate("USD", "EUR");
    }
}
```

- Tên field trùng tên bean cũng chọn đúng bean: `@PerTestMock ExchangeRateClient primaryFx;`.
- Một bean có `@Primary` thì `@PerTestMock ExchangeRateClient fx;` chọn bean đó.
- Trên tham số method phải dùng `name`, không dùng `@Qualifier` (SpringExtension giành tham số có `@Qualifier`, JUnit báo hai
  `ParameterResolver` cạnh tranh): `void t(@PerTestMock(name = "primaryFx") ExchangeRateClient m)`.
- Cấp class không có field để đặt tên: dùng `@Primary` hoặc `name = "..."` trên khai báo.

## Cấp class: như `@MockBean`

Dùng cho cổng ra ngoài mà context test không có cài đặt (database tài liệu, mail, cache...). Thư viện tạo bean mock;
`@PostConstruct`/kết nối ngoài của bean thật không bao giờ chạy; mock được đăng ký như singleton có sẵn nên không qua BeanPostProcessor nào (bean `@ConfigurationProperties` chỉ có getter cũng khởi động được). Đặt ở lớp cơ sở có `@ParallelSpringTest`.
Rút gọn từ `ProjectBaseIT` (bản đầy đủ khai 10 cổng và 3 gateway):

```java
@PerTestMocks({
        @PerTestMock(Ports.Mailer.class),                                   // theo kiểu
        @PerTestMock(Ports.DocumentDb.class),
        @PerTestMock(name = "gatewayA", classes = PaymentGateway.class),    // theo tên + kiểu
        @PerTestMock(name = "gatewayB", classes = PaymentGateway.class),
        @PerTestMock(name = "gatewayC", classes = PaymentGateway.class),
})
@SpringBootTest(classes = ProjectConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "project", wrap = {"*", "limitProperties"})
public abstract class ProjectBaseIT {
    @Autowired protected CheckoutService checkout;
}
```

Lớp con chỉ khai field ở chỗ cần stub; test không khai field nhận một mock Mockito dùng chung không trạng thái (trả
`null`/0/rỗng, không ghi nhớ lời gọi):

```java
class ProjectCheckoutIT extends ProjectBaseIT {

    @PerTestMock(name = "gatewayA") PaymentGateway gatewayA;
    @PerTestMock(name = "gatewayB") PaymentGateway gatewayB;
    @PerTestMock Ports.DocumentDb documentDb;

    @Test
    void firstGatewayFailsSecondIsUsed() {
        when(gatewayA.charge(anyString(), any())).thenThrow(new IllegalStateException("A down"));

        assertThat(checkout.pay("o1", BigDecimal.TEN)).isEqualTo("B");

        verify(gatewayB).charge("o1", BigDecimal.TEN);
        verify(documentDb).save("o1", "B");
    }
}
```

Quy tắc cấp class:

- Cú pháp: `@PerTestMock(A.class)`, `@PerTestMock({A.class, B.class})`, `@PerTestMock(name = "x", classes = A.class)`
  (`name` chỉ đi với đúng một kiểu), `@PerTestMocks({...})` hoặc lặp trực tiếp annotation.
- `name` là tên bean để chọn khi đã có nhiều bean cùng kiểu, và là tên bean được tạo nếu chưa có. Không có `name`: tên
  lớp viết thường chữ đầu.
- Bean đã có trong context thì định nghĩa bị thay; tên, alias, `@Primary`, `@Fallback` (Spring 6.2+), `@Qualifier` được giữ.
- Mock được đăng ký sau `ConfigurationClassPostProcessor`: bean mock TẠO MỚI không được `@ConditionalOnBean(MockedType)`
  nhìn thấy (khác `@MockBean`). Cần điều kiện đó thì đăng ký bean bằng `@Bean`/`@Import` trong cấu hình test để thư viện chỉ
  việc thay.

## Bean REAL ở lớp này, SPY ở lớp kia, MOCK ở lớp khác, cùng một context

Bean khai cấp class luôn là mock cho cả context. Bean cần thật ở một số lớp thì KHÔNG khai cấp class; chỉ lớp cần mock
đặt field. Cả ba lớp dưới đây dùng chung context `project`:

```java
class ProjectPricingRealIT extends ProjectBaseIT {          // REAL: không khai báo gì
    @Test
    void realBean() {
        assertThat(pricing.price(BigDecimal.TEN)).isEqualByComparingTo("21");   // 10 x 2 + fee() = 1
    }
}

class ProjectPricingSpyIT extends ProjectBaseIT {           // SPY: this.fee() trong price() đi qua spy
    @PerTestSpy PricingService spy;

    @Test
    void spyBean() {
        doReturn(new BigDecimal("5")).when(spy).fee();

        assertThat(pricing.price(BigDecimal.TEN)).isEqualByComparingTo("25");   // 10 x 2 + 5
        verify(spy).fee();
    }
}

class ProjectPricingMockIT extends ProjectBaseIT {          // MOCK
    @PerTestMock PricingService mock;

    @Test
    void mockedBean() {
        when(mock.price(BigDecimal.TEN)).thenReturn(new BigDecimal("7"));

        assertThat(pricing.price(BigDecimal.TEN)).isEqualByComparingTo("7");
    }
}
```

(`pricing` là `@Autowired protected PricingService pricing;` trong `ProjectBaseIT`.)

Hệ quả: `@PerTestSpy` trên bean đã bị `@PerTestMock` cấp class thay hoặc tạo là lỗi `SPY_NO_BEAN`, vì không còn bản thật.

Hai điều cần biết với bean được mock theo từng test:

- Bean phải được bọc. Mặc định `wrap = {"*"}` bọc mọi bean singleton do ứng dụng định nghĩa (kể cả `@Bean` có kiểu của framework như
  `RestTemplate`, `ObjectMapper`). Không bọc: bean hạ tầng do framework định nghĩa, kiểu hạ tầng (`DataSource`,
  `EntityManagerFactory`, transaction manager, `MessageSource`, `ConversionService`), bean `@ConfigurationProperties`, bean kho dữ liệu (kiểu `Map`/`Collection`, vd map thuộc tính khai báo bằng `@Bean`),
  `BeanPostProcessor`, `FactoryBean`, prototype/scope khác. Muốn mock bean bị bỏ qua thì liệt kê tên:
  `wrap = {"*", "limitProperties"}`. Tên không có wildcard bắt buộc phải tồn tại và bọc được, nếu không là `BEAN_NOT_INTERCEPTABLE`.
- `exclude` thắng `wrap`; mock/spy bean bị loại nhận `BEAN_NOT_WRAPPED` kèm lý do:

```java
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "narrow", exclude = "auditLog")
class NotWrappedConflictDemo {
    @PerTestMock AuditLog auditLog;   // BEAN_NOT_WRAPPED: excluded by @ParallelSpringTest(exclude=[auditLog])
    @Test void test() { }
}
```

## Context nhóm theo `context = "..."`

`@ParallelSpringTest(context = "main")` đặt tên context để đếm, đặt tên tài nguyên DB và báo cáo. Hai lớp dùng chung
MỘT `ApplicationContext` khi cùng cấu hình Spring, cùng giá trị `@ParallelSpringTest` và cùng tập `@PerTestMock` cấp class.

```java
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.extra-bean=true")
@ParallelSpringTest(context = "alt")
abstract class AltBaseIT {
    @Autowired protected ApplicationContext ctx;
}
// mọi lớp extends AltBaseIT dùng chung context "alt"; MainIT dùng context "main" (cấu hình khác)
```

| Tình huống | Kết quả |
|---|---|
| Cùng cấu hình, cùng giá trị, cùng tập mock cấp class (kể cả khai ở lớp cha) | một context |
| Cùng tên, KHÁC cấu hình Spring (vd `properties`) | hai context riêng, không lỗi: tên chỉ là nhãn |
| Cùng tên, khác `wrap`/`exclude`/`schemas` | lỗi `CONTEXT_NAME_CONFLICT` (thường gõ nhầm tên) |
| Khác tập `@PerTestMock` cấp class | context khác (như `@MockBean`); PT10 in diff từng bean khác nhau |
| Field và tham số `@PerTestMock`/`@PerTestSpy`, `@PerTestSpy` cấp class | KHÔNG vào khóa context |

Ví dụ lớp con khai thêm một mock cấp class nên tách ra context riêng:

```java
@PerTestMock(name = "extraAudit", classes = AuditLog.class)
@ParallelSpringTest(context = "project-extra")
class ProjectExtraMockOnlyIT extends ProjectBaseIT {
    @PerTestMock(name = "extraAudit") AuditLog extra;
    // context "project-extra", khác context "project" của ProjectBaseIT
}
```

Tổng số context đang hoạt động bị chặn bởi `ptest.max-contexts` (mặc định 8): xem [Guard](guard.md), mã PT10.

## Tham số của method test

```java
@Test
void rollbackStillHappensUnderSpies(@PerTestSpy TransferService transferHandle,
                                    @PerTestSpy AccountRepository repoHandle) {
    Account from = accounts.save(new Account("tx-" + System.nanoTime(), "USD", new BigDecimal("100")));
    assertThatThrownBy(() -> transferService.transfer(from.getId(), -1L, BigDecimal.TEN))
            .isInstanceOf(AccountNotFoundException.class);
    assertThat(accounts.findById(from.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100");
    verify(transferHandle).transfer(from.getId(), -1L, BigDecimal.TEN);
    verify(repoHandle).saveAndFlush(any(Account.class));
}
```

Tham số ghi đè field cùng bean chỉ cho method đó; field nhận chính handle của method. Hai method cùng lớp có thể đổi chế độ
của cùng một bean (field là MOCK, method thứ hai khai tham số `@PerTestSpy`). Một bean vừa mock vừa spy trong cùng phân
cấp (field/tham số) là `MODE_CONFLICT`; tham số của method ghi đè field cùng bean thì không phải xung đột.

## `@Nested`

Lớp `@Nested` nhận field của lớp bao ngoài; mọi field cùng bean trong phân cấp nhận cùng một handle.

```java
class NestedIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient outerMock;

    @Test
    void outerTest() {
        when(outerMock.rate("USD", "EUR")).thenReturn(new BigDecimal("11"));
        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("11");
    }

    @Nested
    class Inner {
        @PerTestMock @Qualifier("primaryFx") ExchangeRateClient innerMock;

        @Test
        void innerTest() {
            when(innerMock.rate("USD", "EUR")).thenReturn(new BigDecimal("22"));
            assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("22");
        }
    }
}
```

Lớp `@Nested` tự khai THÊM `@PerTestMock`/`@PerTestSpy` cấp class có tập khai báo khác lớp bao ngoài nên chạy ở context
KHÁC (tốn thêm một context). Muốn dùng chung thì khai ở lớp bao ngoài, hoặc dùng field trong lớp `@Nested`.

## `@RepeatedTest`, `@ParameterizedTest`, `@TestFactory`

Mỗi lần lặp/mỗi lần gọi là một test riêng với mock MỚI và binding riêng.

```java
class RepeatedMockIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @RepeatedTest(4)
    void eachRepetitionHasItsOwnMock(RepetitionInfo info) {
        BigDecimal mine = BigDecimal.valueOf(info.getCurrentRepetition());
        when(mock.rate("USD", "EUR")).thenReturn(mine);

        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo(mine);
        verify(mock, times(1)).rate("USD", "EUR");
    }
}
```

`@ParameterizedTest`: tham số `@PerTestMock` đặt SAU các tham số do nguồn đối số cấp (JUnit chỉ giao cho resolver của nó
các chỉ số có đối số); đặt chồng chỉ số là lỗi cạnh tranh `ParameterResolver`.

```java
@ParameterizedTest
@ValueSource(strings = {"2.5", "3.5", "4.5"})
void eachInvocationHasItsOwnMock(String value, @PerTestMock(name = "primaryFx") ExchangeRateClient mock) {
    when(mock.rate("A", "B")).thenReturn(new BigDecimal(value));

    assertThat(primaryFx.rate("A", "B")).isEqualByComparingTo(value);
}
```

`@TestFactory`: mock của lần gọi factory phủ cả các dynamic test của nó. JUnit có thể chạy dynamic test trên thread khác
thread gọi factory khi chạy song song, nên thư viện bọc từng dynamic test để tự gắn lại binding (`DynamicBindings`),
kể cả `DynamicContainer` lồng nhau.

```java
@PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

@TestFactory
Stream<DynamicTest> factory() {
    when(mock.rate("USD", "EUR")).thenReturn(BigDecimal.ONE);
    return Stream.of("first", "second").map(step -> dynamicTest(step, () ->
            assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("1")));
}
```

`@Timeout(threadMode = SEPARATE_THREAD)` được hỗ trợ cho `@Test`, `@TestTemplate`, `@BeforeEach`, `@AfterEach`, `@TestFactory`:
thư viện gắn lại binding trên thread chạy method.

## `TestInstance.Lifecycle.PER_CLASS`

Được hỗ trợ khi các method của lớp KHÔNG chạy đồng thời (`@Execution(SAME_THREAD)`, `@Isolated`, `@ResourceLock`):
mỗi method nhận handle mới. Nếu method có thể chạy đồng thời thì field `@PerTestMock`/`@PerTestSpy` là lỗi `CONFIG`.

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class ProjectPerClassSerialIT extends ProjectBaseIT {
    @PerTestMock Ports.Mailer mailer;   // mỗi method một mock mới
    // ...
}
```
