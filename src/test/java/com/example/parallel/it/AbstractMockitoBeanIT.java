package com.example.parallel.it;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.fx.ExchangeRateClient;
import com.example.parallel.support.TestData;
import com.example.parallel.transfer.TransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * When you DO want @MockitoBean (stubbing + verify on a Spring bean):
 *  - the mock is a singleton inside the cached context, shared by every test class with this config;
 *  - it is reset after each test method (MockReset.AFTER) -- possibly while another test uses it;
 *  - Mockito stubbing/verification is not thread-safe across threads.
 * So every class using this mock takes the same exclusive @ResourceLock: JUnit then never runs two
 * of them at the same time and runs their methods sequentially, while all OTHER tests stay parallel.
 * (SharedMockitoBeanConflictDemo shows what happens without the lock.)
 */
@SpringBootTest
@ActiveProfiles("test")
@ResourceLock(AbstractMockitoBeanIT.EXCHANGE_RATE_MOCK)
abstract class AbstractMockitoBeanIT {

    static final String EXCHANGE_RATE_MOCK = "mockitobean:ExchangeRateClient";

    @MockitoBean protected ExchangeRateClient exchangeRates;
    @Autowired protected TransferService transferService;
    @Autowired protected AccountRepository accounts;

    protected Account newAccount(String currency, String balance) {
        return accounts.save(new Account(TestData.uniqueOwner("mb"), currency, new BigDecimal(balance)));
    }
}
