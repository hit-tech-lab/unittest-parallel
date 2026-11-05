package com.example.parallel.it;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.audit.AuditLog;
import com.example.parallel.fx.ExchangeRateClient;
import com.hittech.paralleltest.annotation.SharedContextBase;
import com.hittech.paralleltest.data.TestData;
import com.hittech.paralleltest.mock.PerTestMock;
import com.hittech.paralleltest.mock.PerTestMockHttp;
import com.hittech.paralleltest.mock.RoutedMocks;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * THE base of all full-application integration tests. The guard enforces that subclasses add no
 * configuration (PT02), so every IT shares this one context and runs in parallel.
 *
 * External dependencies are declared in @RoutedMocks: each test gets its own fresh mock through
 * {@code @PerTestMock} -- also for requests served by the embedded server (see {@link #http()}).
 */
@SharedContextBase
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RoutedMocks(ExchangeRateClient.class)
public abstract class AbstractIntegrationTest {

    @PerTestMock protected ExchangeRateClient exchangeRates;
    @Autowired protected AccountRepository accounts;
    @Autowired protected AuditLog auditLog;
    @Value("${local.server.port}") protected int port;

    /** HTTP client to the embedded server that carries this test's mocks to the server thread. */
    protected RestClient http() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestInterceptor(PerTestMockHttp.propagation())
                .build();
    }

    protected Account newAccount(String currency, String balance) {
        return accounts.save(new Account(TestData.unique("it"), currency, new BigDecimal(balance)));
    }

    protected BigDecimal balanceOf(Account a) {
        return accounts.findById(a.getId()).orElseThrow().getBalance();
    }
}
