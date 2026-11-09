# ptest: chạy test Spring Boot song song (bản Spring Boot 3)

Thư viện `com.hit.ptest` cho phép các lớp test `@SpringBootTest` chạy song song trong cùng một JVM, dùng chung
`ApplicationContext`, và mỗi test vẫn có mock/spy riêng của mình trên bean của context đó.

- Bản này: Spring Boot 3.3+, Spring Framework 6.1+, Java 17+, JUnit 5.x, Mockito 5 (mock maker inline).
- Artifact: `com.hit.research:parallel-test-lib:0.1.0-SNAPSHOT` (module `parallel-test-lib`).
- Bản Spring Boot 4 có cùng API và cùng tài liệu; chỉ khác phiên bản và tọa độ Maven.

## Mục lục

| Trang | Nội dung |
|---|---|
| [Giới thiệu](docs/introduction.md) | Vấn đề, cách giải quyết, nguyên tắc |
| [Yêu cầu và cài đặt](docs/requirements-and-setup.md) | Phiên bản, dependency, `junit-platform.properties`, surefire, bảng cấu hình |
| [Khai báo mock/spy](docs/mock-spy-declaration.md) | Cấp class và field, chọn bean, REAL/SPY/MOCK, context nhóm, `@Nested`, `@TestFactory`... |
| [Web và thread nền](docs/web-and-background-threads.md) | MOCK và RANDOM_PORT, `@Async`, `unbound-policy`, `ParallelTest.wrap` |
| [Cơ sở dữ liệu](docs/database.md) | H2, Postgres (Testcontainers), `ptest.db.*`, `@ServiceConnection` |
| [Guard an toàn song song](docs/guard.md) | Mã PT01..PT13, cấu hình, cách viết test an toàn |
| [Giới hạn đã biết](docs/limitations.md) | Giới hạn của thư viện và của JUnit |
| [Di chuyển từ `@MockBean`/`@SpyBean`](docs/migration.md) | Bảng đối chiếu trước/sau |
| [Xử lý sự cố](docs/troubleshooting.md) | Mã lỗi, nguyên nhân, cách sửa |
| [Cách hoạt động](docs/how-it-works.md) | Wrapper, binding, khóa context |
| [Số đo](docs/benchmarks.md) | Benchmark và overhead, kèm điều kiện đo |
| [Chạy bộ test mẫu](docs/running-sample-tests.md) | `scripts/verify.sh` |

Tham chiếu chi tiết đi kèm mã nguồn: [GUARD.md](parallel-test-lib/GUARD.md), [LIMITS.md](parallel-test-lib/LIMITS.md).

## Bắt đầu nhanh (5 phút)

Ví dụ dùng các lớp `demo.core.*` của module `lib-tests` (`DemoApp`, `TransferService`, `AccountRepository`, `ExchangeRateClient`).

**1. Dependency** (cài thư viện vào local repository bằng `mvn -DskipTests install` ở thư mục gốc repo, JDK 17):

```xml
<dependency>
    <groupId>com.hit.research</groupId>
    <artifactId>parallel-test-lib</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

H2 trên classpath là đủ để mỗi context có database riêng. Postgres: xem [Cơ sở dữ liệu](docs/database.md).

**2. Bật JUnit song song** (`src/test/resources/junit-platform.properties`):

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.config.strategy=fixed
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

**3. Surefire** (Boot 3.3 mặc định surefire 3.2.x báo sai số test khi chạy song song; dùng 3.5.x):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.6</version>
    <configuration>
        <argLine>-Xshare:off</argLine>
        <includes>
            <include>**/*IT.java</include>
        </includes>
    </configuration>
</plugin>
```

`includes` cần khi lớp test đặt tên `*IT` (surefire mặc định chỉ nhận `*Test`, `Test*`, `*Tests`, `*TestCase`); bỏ nếu dùng tên khác.

**4. Lớp cơ sở** có `@ParallelSpringTest`:

```java
package demo.it;

import demo.core.AccountRepository;
import demo.core.DemoApp;
import demo.core.ExchangeRateClient;
import demo.core.TransferService;
import com.hit.ptest.ParallelSpringTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ParallelSpringTest(context = "main")
public abstract class MainIT {

    @Autowired protected AccountRepository accounts;
    @Autowired protected TransferService transferService;
    @Autowired @Qualifier("primaryFx") protected ExchangeRateClient primaryFx;
}
```

**5. Test REAL, SPY, MOCK** của cùng bean `primaryFx`, ba lớp chạy song song trên MỘT context:

```java
package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.Account;
import demo.core.TransferService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** REAL: không khai báo gì, bean thật. */
class TransferRealIT extends MainIT {

    @Test
    void sameCurrencyCreditsTheSameAmount() {
        Account from = accounts.save(new Account("a-" + UUID.randomUUID(), "USD", new BigDecimal("100")));
        Account to = accounts.save(new Account("b-" + UUID.randomUUID(), "USD", BigDecimal.ZERO));

        TransferService.Result r = transferService.transfer(from.getId(), to.getId(), BigDecimal.TEN);

        assertThat(r.credited()).isEqualByComparingTo("10");
    }
}
```

```java
package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import demo.core.ExchangeRateClient;
import com.hit.ptest.PerTestSpy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/** SPY: gọi bean thật, stub một phần. */
class FxSpyIT extends MainIT {

    @PerTestSpy @Qualifier("primaryFx") ExchangeRateClient spy;

    @Test
    void stubsOneCallAndKeepsTheRest() {
        doReturn(new BigDecimal("3.30")).when(spy).rate("USD", "JPY");

        assertThat(primaryFx.rate("USD", "JPY")).isEqualByComparingTo("3.30");
        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("1.10"); // bean thật
        verify(spy).rate("USD", "EUR");
    }
}
```

```java
package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import demo.core.Account;
import demo.core.ExchangeRateClient;
import demo.core.TransferService;
import com.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/** MOCK: thay hoàn toàn bean, chỉ trong test này. */
class TransferMockIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient fx;

    @Test
    void usesTheStubbedRate() {
        when(fx.rate("USD", "EUR")).thenReturn(new BigDecimal("2"));
        Account from = accounts.save(new Account("a-" + UUID.randomUUID(), "USD", new BigDecimal("100")));
        Account to = accounts.save(new Account("b-" + UUID.randomUUID(), "EUR", BigDecimal.ZERO));

        TransferService.Result r = transferService.transfer(from.getId(), to.getId(), BigDecimal.TEN);

        assertThat(r.credited()).isEqualByComparingTo("20");
        verify(fx).rate("USD", "EUR");
    }
}
```

Chạy `mvn test`. Tiếp theo đọc [Khai báo mock/spy](docs/mock-spy-declaration.md).
