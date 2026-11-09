package demo.project;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.AuditLog;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.ParallelTest;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/**
 * Lớp con khai THÊM bean mock cấp class mà lớp cơ sở chưa có, và khai lại {@code @ParallelSpringTest}: tập mock khác => KHÓA
 * context khác (như @MockBean), nên report ghi context riêng (lớp này một, các lớp còn lại của ProjectBaseIT dùng chung "project").
 */
@PerTestMock(name = "extraAudit", classes = AuditLog.class)
@ParallelSpringTest(context = "project-extra")
class ProjectExtraMockOnlyIT extends ProjectBaseIT {

    @PerTestMock(name = "extraAudit") AuditLog extra;

    @Test
    void extraMockOnlyBeanMakesAnotherContext() {
        assertThat(ctx.containsBean("extraAudit")).isTrue();
        assertThat(ParallelTest.contextKey(ctx)).startsWith("project-extra@");
        assertThat(extra).isNotNull();
    }
}
