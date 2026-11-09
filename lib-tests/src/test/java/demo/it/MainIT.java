package demo.it;

import demo.core.AccountRepository;
import demo.core.AuditLog;
import demo.core.DemoApp;
import demo.core.ExchangeRateClient;
import demo.core.FinalPricer;
import demo.core.NotificationService;
import demo.core.RateCache;
import demo.core.TransferService;
import demo.core.UntouchedService;
import demo.support.Rendezvous;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.ParallelTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** Context chính dùng chung cho hầu hết IT; một cấu hình duy nhất nên chỉ MỘT ApplicationContext. */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.error.include-message=always", "spring.web.error.include-message=always"})
@ParallelSpringTest(context = "main")
public abstract class MainIT {

    @Autowired protected ApplicationContext ctx;
    @Autowired @Qualifier("primaryFx") protected ExchangeRateClient primaryFx;
    @Autowired @Qualifier("backupFx") protected ExchangeRateClient backupFx;
    @Autowired protected AuditLog auditLog;
    @Autowired protected TransferService transferService;
    @Autowired protected FinalPricer finalPricer;
    @Autowired protected NotificationService notificationService;
    @Autowired protected RateCache rateCache;
    @Autowired protected AccountRepository accounts;
    @Autowired protected UntouchedService untouchedService;

    /** Hẹn nhiều test cùng lúc và kiểm tra tất cả dùng CÙNG một ApplicationContext. */
    protected void meetInSameContext(String group, int parties) {
        Rendezvous.share(group + ":ctx", ParallelTest.contextKey(ctx));
        Rendezvous.meet(group, parties);
        assertThat(Rendezvous.shared(group + ":ctx")).as("all tests of group %s share one context", group).hasSize(1);
    }
}
