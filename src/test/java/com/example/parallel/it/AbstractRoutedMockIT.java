package com.example.parallel.it;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.fx.ExchangeRateClient;
import com.example.parallel.support.MockRouter;
import com.example.parallel.support.PerTestMock;
import com.example.parallel.support.PerTestMockExtension;
import com.example.parallel.support.TestData;
import com.example.parallel.transfer.TransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Scales to MANY test classes using the same mocked bean: no @ResourceLock, one shared context,
 * every class and method runs in parallel, each test stubs/verifies its own mock.
 * webEnvironment=MOCK so MockMvc requests execute on the test thread (routable).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractRoutedMockIT.RouterConfig.class)
@ExtendWith(PerTestMockExtension.class)
abstract class AbstractRoutedMockIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class RouterConfig {
        @Bean
        @Primary
        ExchangeRateClient exchangeRateClientRouter() {
            return MockRouter.routerFor(ExchangeRateClient.class);
        }
    }

    @PerTestMock protected ExchangeRateClient exchangeRates;
    @Autowired protected TransferService transferService;
    @Autowired protected AccountRepository accounts;

    protected Account newAccount(String currency, String balance) {
        return accounts.save(new Account(TestData.uniqueOwner("rt"), currency, new BigDecimal(balance)));
    }
}
