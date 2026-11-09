package demo.it;

import com.hit.ptest.PerTestSpy;
import demo.core.AccountRepository;
import demo.core.AuditLog;
import demo.core.FinalPricer;
import demo.core.NotificationService;
import demo.core.RateCache;
import demo.core.TransferService;

/** {@link SpyContract} với {@code @PerTestSpy} của thư viện; các test của hợp đồng chạy SONG SONG, mỗi test có spy riêng. */
class SpyContractPtestIT extends MainIT implements SpyContract {

    @PerTestSpy TransferService transferSpy;
    @PerTestSpy AccountRepository repoSpy;
    @PerTestSpy FinalPricer pricerSpy;
    @PerTestSpy NotificationService notificationsSpy;
    @PerTestSpy RateCache cacheSpy;

    @Override public TransferService service() { return transferService; }
    @Override public TransferService serviceSpy() { return transferSpy; }
    @Override public AccountRepository repo() { return accounts; }
    @Override public AccountRepository repoSpy() { return repoSpy; }
    @Override public FinalPricer pricer() { return finalPricer; }
    @Override public FinalPricer pricerSpy() { return pricerSpy; }
    @Override public NotificationService notifications() { return notificationService; }
    @Override public NotificationService notificationsSpy() { return notificationsSpy; }
    @Override public RateCache cache() { return rateCache; }
    @Override public RateCache cacheSpy() { return cacheSpy; }
    @Override public AuditLog auditLog() { return auditLog; }
}
