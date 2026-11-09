package demo.demo;

import demo.audit.AuditApp;
import demo.audit.AuditRecord;
import demo.audit.AuditRecordRepository;
import io.hit.ptest.ParallelSpringTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Negative control C: entity nằm trong schema "audit" nhưng không khai báo schemas => schema không tồn tại trước ddl => phải FAIL. */
@SpringBootTest(classes = AuditApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "audit-missing")
class MissingSchemaConflictDemo {

    @Autowired AuditRecordRepository repository;

    @Test
    void saveIntoMissingSchema() {
        repository.save(new AuditRecord("x"));
    }
}
