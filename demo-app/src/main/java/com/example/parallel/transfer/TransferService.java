package com.example.parallel.transfer;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountNotFoundException;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.audit.AuditLog;
import com.example.parallel.fx.ExchangeRateClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final ExchangeRateClient exchangeRates;
    private final AuditLog auditLog;

    public TransferService(AccountRepository accounts, ExchangeRateClient exchangeRates, AuditLog auditLog) {
        this.accounts = accounts;
        this.exchangeRates = exchangeRates;
        this.auditLog = auditLog;
    }

    public record TransferResult(Long fromAccountId, Long toAccountId, BigDecimal debited, BigDecimal credited) {
    }

    @Transactional
    public TransferResult transfer(Long fromId, Long toId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("cannot transfer to the same account");
        }
        // Lock in a stable order (lowest id first) to avoid deadlocks between concurrent transfers.
        Long first = fromId < toId ? fromId : toId;
        Long second = fromId < toId ? toId : fromId;
        Account a = lock(first);
        Account b = lock(second);
        Account from = a.getId().equals(fromId) ? a : b;
        Account to = from == a ? b : a;

        BigDecimal credited = amount;
        if (!from.getCurrency().equals(to.getCurrency())) {
            BigDecimal rate = exchangeRates.rate(from.getCurrency(), to.getCurrency());
            credited = amount.multiply(rate).setScale(4, RoundingMode.HALF_EVEN);
        }
        from.debit(amount);
        to.credit(credited);
        auditLog.record(new AuditLog.Event("TRANSFER", fromId, toId, amount + " -> " + credited));
        return new TransferResult(fromId, toId, amount, credited);
    }

    private Account lock(Long id) {
        return accounts.findForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
