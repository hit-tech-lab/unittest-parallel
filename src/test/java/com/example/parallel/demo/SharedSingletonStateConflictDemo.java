package com.example.parallel.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.audit.AuditLog;
import com.example.parallel.support.Rendezvous;
import com.example.parallel.support.TestData;
import com.example.parallel.transfer.TransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * NEGATIVE CONTROL (expected to FAIL). A stateful singleton bean (AuditLog) is shared by all tests
 * of the cached context: clear() + size() assertions race with concurrent tests.
 * Fix: filter by data you own (TransferApiIT), or @ResourceLock("auditLog") if you must clear it.
 */
@SpringBootTest
@ActiveProfiles("test")
class SharedSingletonStateConflictDemo {

    @Autowired AuditLog auditLog;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accounts;

    private void transferAndAssertSingleEvent() {
        auditLog.clear();
        Account a = accounts.save(new Account(TestData.uniqueOwner("s"), "USD", BigDecimal.TEN));
        Account b = accounts.save(new Account(TestData.uniqueOwner("s"), "USD", BigDecimal.ZERO));
        transferService.transfer(a.getId(), b.getId(), BigDecimal.ONE);
        Rendezvous.await("audit-recorded", 2);
        assertThat(auditLog.events()).hasSize(1);
    }

    @Test
    void first() {
        transferAndAssertSingleEvent();
    }

    @Test
    void second() {
        transferAndAssertSingleEvent();
    }
}
