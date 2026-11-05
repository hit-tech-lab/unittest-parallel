package com.example.parallel.it;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.audit.AuditLog;
import com.example.parallel.support.FakeExchangeRateClient;
import com.example.parallel.support.TestData;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Shared base for most integration tests. Rules that make it safe under parallel execution:
 *
 *  1. ONE identical configuration for all subclasses -> Spring caches and reuses ONE context
 *     (fast; verified by ParallelismProbe "springContexts").
 *  2. No @MockitoBean here: a Mockito mock bean is a singleton shared by every concurrent test,
 *     and stubbing/verify/reset on it from several threads is not safe. We use a thread-safe
 *     fake keyed by per-test data instead (see MockitoBean*IT for the alternative with locks).
 *  3. No @DirtiesContext (closing a context while other tests use it breaks them).
 *  4. RANDOM_PORT: no port clash between forks/contexts. Requests run on server threads, so a
 *     test-level @Transactional rollback would NOT apply -> each test creates its own unique data
 *     and asserts only on that data (never deleteAll(), never global count()).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.FakeFxConfig.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeFxConfig {
        @Bean
        @Primary
        FakeExchangeRateClient fakeExchangeRateClient() {
            return new FakeExchangeRateClient();
        }
    }

    @Autowired protected FakeExchangeRateClient fx;
    @Autowired protected AccountRepository accounts;
    @Autowired protected AuditLog auditLog;
    @Value("${local.server.port}") protected int port;

    protected RestClient http() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    protected Account newAccount(String currency, String balance) {
        return accounts.save(new Account(TestData.uniqueOwner("it"), currency, new BigDecimal(balance)));
    }

    protected BigDecimal balanceOf(Account a) {
        return accounts.findById(a.getId()).orElseThrow().getBalance();
    }
}
