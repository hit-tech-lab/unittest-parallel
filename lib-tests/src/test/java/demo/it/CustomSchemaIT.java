package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.audit.AuditApp;
import demo.audit.AuditRecord;
import demo.audit.AuditRecordRepository;
import com.hit.ptest.ParallelSpringTest;
import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** C: entity nằm trong schema "audit": schema phải tồn tại trước khi Hibernate chạy ddl. */
@SpringBootTest(classes = AuditApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "audit", schemas = "audit")
class CustomSchemaIT {

    @Autowired AuditRecordRepository repository;
    @Autowired JdbcTemplate jdbc;

    @RepeatedTest(4)
    void entityInCustomSchemaWorks() {
        AuditRecord saved = repository.save(new AuditRecord("n-" + UUID.randomUUID()));
        assertThat(repository.findById(saved.getId())).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from audit.audit_record where id = ?", Integer.class, saved.getId())).isEqualTo(1);
    }
}
