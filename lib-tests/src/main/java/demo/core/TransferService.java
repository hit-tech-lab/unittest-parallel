package demo.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    public record Result(BigDecimal debited, BigDecimal credited) {
    }

    private final AccountRepository accounts;
    private final ExchangeRateClient primary;
    private final ExchangeRateClient backup;
    private final AuditLog auditLog;

    public TransferService(AccountRepository accounts, @Qualifier("primaryFx") ExchangeRateClient primary,
                           @Qualifier("backupFx") ExchangeRateClient backup, AuditLog auditLog) {
        this.accounts = accounts;
        this.primary = primary;
        this.backup = backup;
        this.auditLog = auditLog;
    }

    /** Ghi nợ trước, tìm tài khoản nhận sau: nếu thiếu tài khoản nhận thì @Transactional phải rollback bên nợ. */
    @Transactional
    public Result transfer(Long fromId, Long toId, BigDecimal amount) {
        Account from = accounts.findById(fromId).orElseThrow(() -> new AccountNotFoundException(fromId));
        from.setBalance(from.getBalance().subtract(amount));
        accounts.saveAndFlush(from);
        Account to = accounts.findById(toId).orElseThrow(() -> new AccountNotFoundException(toId));
        BigDecimal credited = from.getCurrency().equals(to.getCurrency())
                ? amount : amount.multiply(rate(from.getCurrency(), to.getCurrency())).setScale(4, RoundingMode.HALF_EVEN);
        to.setBalance(to.getBalance().add(credited));
        accounts.saveAndFlush(to);
        auditLog.record(new AuditLog.Event("TRANSFER", fromId + "->" + toId));
        return new Result(amount, credited);
    }

    /** primaryFx lỗi thì dùng backupFx: hai bean cùng kiểu, có thể ở hai chế độ khác nhau. */
    public BigDecimal rate(String from, String to) {
        try {
            return primary.rate(from, to);
        } catch (RuntimeException e) {
            return backup.rate(from, to);
        }
    }
}
