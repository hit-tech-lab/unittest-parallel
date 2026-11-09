package demo.support;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.Account;
import demo.core.AccountRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Kịch bản C: hai context chạy cùng lúc; một bên lưu dữ liệu rồi bên kia XÓA BẢNG trong DB của mình. Nếu hai context dùng chung DB thì bên lưu mất dữ liệu. Chia sẻ bởi IT (DB riêng, phải đạt) và Demo (DB chung, phải fail).
 */
public abstract class DbIsolationBase {

    @Autowired protected AccountRepository accounts;
    @Autowired protected ApplicationContext ctx;

    protected abstract String group();

    protected abstract boolean dropsItsTable();

    protected void scenario() {
        Account saved = accounts.save(new Account("db-" + UUID.randomUUID(), "USD", BigDecimal.TEN));
        Rendezvous.share(group() + ":urls", ctx.getEnvironment().getProperty("spring.datasource.url"));
        Rendezvous.meet(group() + "-saved", 2);
        if (dropsItsTable()) {
            ctx.getBean(JdbcTemplate.class).execute("drop table account");
        }
        Rendezvous.meet(group() + "-dropped", 2);
        if (!dropsItsTable()) {
            assertThat(accounts.findById(saved.getId())).as("data survives the other context dropping its table").isPresent();
        }
    }
}
