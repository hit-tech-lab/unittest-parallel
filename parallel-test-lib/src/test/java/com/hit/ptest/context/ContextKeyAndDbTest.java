package com.hit.ptest.context;

import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.ParallelTestException;
import com.hit.ptest.UnboundPolicy;
import com.hit.ptest.db.DatabaseProvisioner;
import com.hit.ptest.db.PostgresProvisioner;

import com.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Điểm 9, 10, 11, 13, 16: khóa context, giới hạn slot, chế độ DB auto, tên DB dài, kiểm tra môi trường. */
class ContextKeyAndDbTest {

    private static final String[] NONE = {};

    @AfterEach
    void cleanup() {
        System.clearProperty("ptest.max-contexts");
        Fixtures.closeAll();
    }

    @ParallelSpringTest(context = "same", wrap = "a")
    static class DeclA {
    }

    @ParallelSpringTest(context = "same", wrap = "b")
    static class DeclB {
    }

    @ParallelSpringTest(context = "same", wrap = "b")
    static class DeclB2 {
    }

    @ParallelSpringTest(context = "other-name", wrap = "a")
    static class DeclOther {
    }

    @ParallelSpringTest(context = "enclosing")
    static class Enclosing {
        class Inner {
        }
    }

    @Test
    void nestedClassUsesTheEnclosingClassDeclaration() {
        assertThat(new ParallelContextCustomizerFactory().createContextCustomizer(Enclosing.Inner.class, List.of()))
                .as("without enclosing-class search the nested class got an unmanaged context").isNotNull();
    }

    @Test
    void sameNameDifferentSpringConfigIsNotAnErrorAndGetsDistinctKeys() {
        AnnotationConfigApplicationContext c1 = Fixtures.context("label", NONE, NONE, Map.of("x", "a"), Fixtures.Beans.class);
        AnnotationConfigApplicationContext c2 = Fixtures.context("label", NONE, NONE, Map.of("x", "b"), Fixtures.Beans.class);
        c1.refresh();
        c2.refresh();

        assertThat(Fixtures.managed(c1).key).startsWith("label@").isNotEqualTo(Fixtures.managed(c2).key);
        assertThat(Fixtures.managed(c1).name).isEqualTo(Fixtures.managed(c2).name);
    }

    @Test
    void declarationConflictIsDetectedWhicheverClassIsSeenFirst() {
        var factory = new ParallelContextCustomizerFactory();
        // thứ tự A rồi B và thứ tự B rồi A trên hai tên khác nhau: luôn có một bên lỗi, thông điệp nếu cả hai lớp
        assertThat(factory.createContextCustomizer(DeclA.class, List.of())).isNotNull();
        assertThatThrownBy(() -> factory.createContextCustomizer(DeclB.class, List.of()))
                .isInstanceOf(ParallelTestException.Config.class).hasMessageContaining("CONTEXT_NAME_CONFLICT")
                .hasMessageContaining(DeclA.class.getName()).hasMessageContaining(DeclB.class.getName());
        // lặp lại là ổn định: không phụ thuộc kết quả trước đó (không ghi nhận khai báo lỗi)
        assertThatThrownBy(() -> factory.createContextCustomizer(DeclB2.class, List.of()))
                .hasMessageContaining("CONTEXT_NAME_CONFLICT");
        assertThat(factory.createContextCustomizer(DeclA.class, List.of())).isNotNull();
        // control: tên khác, cùng khai báo => không lỗi
        assertThat(factory.createContextCustomizer(DeclOther.class, List.of())).isNotNull();
    }

    @Test
    void maxContextsIsReadGloballyAtCreationAndCountsActiveContextsOnly() {
        int active = ContextRegistry.INSTANCE.activeCount();
        System.setProperty("ptest.max-contexts", Integer.toString(active + 1));
        AnnotationConfigApplicationContext first = Fixtures.started("limit-1", NONE, Map.of(), Fixtures.Beans.class);

        AnnotationConfigApplicationContext second = Fixtures.context("limit-2", NONE, NONE, Map.of(), Fixtures.Beans.class);
        assertThatThrownBy(second::refresh).hasMessageContaining("CONTEXT_LIMIT_EXCEEDED").hasMessageContaining("limit-1@");

        first.close(); // đóng => nhả slot
        AnnotationConfigApplicationContext third = Fixtures.started("limit-3", NONE, Map.of(), Fixtures.Beans.class);
        assertThat(third.isActive()).isTrue();
    }

    @Test
    void thresholdAboveSpringCacheIsAnErrorWithAnAccurateName() {
        assertThatThrownBy(() -> ParallelContextCustomizer.requireWithinSpringCache(PtestSettings.springCacheMaxSize() + 1))
                .isInstanceOf(ParallelTestException.Config.class).hasMessageContaining("spring.test.context.cache.maxSize");
        ParallelContextCustomizer.requireWithinSpringCache(PtestSettings.springCacheMaxSize());
    }

    @Test
    void registryLimitCountsOnlyUnreleasedContexts() {
        ContextRegistry registry = new ContextRegistry();
        var c1 = new AnnotationConfigApplicationContext();
        var c2 = new AnnotationConfigApplicationContext();
        ManagedContext m1 = new ManagedContext("a@1#1", "a", c1, UnboundPolicy.FAIL, false);
        ManagedContext m2 = new ManagedContext("b@1#2", "b", c2, UnboundPolicy.FAIL, false);
        registry.register(m1, 1);
        assertThatThrownBy(() -> registry.register(m2, 1)).isInstanceOf(ParallelTestException.ContextLimit.class);
        registry.release(m1);
        registry.register(m2, 1);
        assertThat(registry.activeCount()).isEqualTo(1);
    }

    @Test
    void autoModeLeavesUserDatasourceAloneAndSkipsContextsThatNeverAskForOne() {
        int dbsBefore = ContextRegistry.INSTANCE.snapshot().get("databases").length();
        AnnotationConfigApplicationContext user = Fixtures.started("user-ds", NONE,
                Map.of("ptest.db.mode", "auto", "spring.datasource.url", "jdbc:h2:mem:user-chosen"), Fixtures.AskForUrlConfig.class);
        AnnotationConfigApplicationContext idle = Fixtures.started("no-ds", NONE, Map.of("ptest.db.mode", "auto"), Fixtures.Beans.class);

        assertThat(user.getBean("url", String.class)).as("user url wins, not overridden").isEqualTo("jdbc:h2:mem:user-chosen");
        assertThat(Fixtures.managed(user).hasDb()).isFalse();
        assertThat(Fixtures.managed(idle).hasDb()).as("no one asked for a datasource: nothing provisioned").isFalse();
        assertThat(ContextRegistry.INSTANCE.snapshot().get("databases").length()).isEqualTo(dbsBefore);

        // control: cùng auto, không ai tự đặt url, context hỏi => có DB riêng
        AnnotationConfigApplicationContext asking = Fixtures.started("asks-ds", NONE, Map.of("ptest.db.mode", "auto"), Fixtures.AskForUrlConfig.class);
        assertThat(asking.getBean("url", String.class)).startsWith("jdbc:h2:mem:ptest_f");
        assertThat(Fixtures.managed(asking).hasDb()).isTrue();
    }

    @Test
    void uniqueNamePutsTheUniquePartFirstAndStaysWithinPostgresIdentifierLimit() {
        String prefix = "very-long-context-name-".repeat(6);
        String n1 = DatabaseProvisioner.uniqueName(prefix + "@abc#1", "0");
        String n2 = DatabaseProvisioner.uniqueName(prefix + "@abc#1", "0");

        assertThat(n1).hasSizeLessThanOrEqualTo(63).startsWith("ptest_f").isNotEqualTo(n2);
        assertThat(n1.substring(0, 20)).isNotEqualTo(n2.substring(0, 20)).as("distinct within the first 20 chars, truncation cannot merge them");
        assertThat(n1).matches("[a-z0-9_]+");
    }

    @Test
    void postgresImageMismatchIsReportedAndSameImageIsSilent() {
        assertThat(PostgresProvisioner.imageMismatchWarning("postgres:16.4", "postgres:16.4")).isNull();
        assertThat(PostgresProvisioner.imageMismatchWarning("postgres:16.4", "postgres:17")).contains("IGNORED").contains("postgres:17");
    }

    @Test
    void mockitoAgentCheckRecognisesTheJavaagentArgument() {
        PtestSettings.requireInlineMockMaker(); // Mockito >= 5 / inline: không ném
        assertThat(PtestSettings.mockitoAgentMissing(List.of("-Xmx1g"))).isTrue();
        assertThat(PtestSettings.mockitoAgentMissing(List.of("-javaagent:/x/mockito-core-5.jar", "-Xshare:off"))).isFalse();
        // Ma trận Boot 3.3.5 dùng Mockito 5.11 (jar chưa phải javaagent): argLine của nó đặt -Dptest.test.agent=false
        assertThat(PtestSettings.mockitoAgentMissing(ManagementFactory.getRuntimeMXBean().getInputArguments()))
                .as("this module's surefire argLine loads the agent").isEqualTo("false".equals(System.getProperty("ptest.test.agent")));
    }

    @Test
    void nonBootLoaderContextIsReadyAfterRefresh() {
        AnnotationConfigApplicationContext plain = Fixtures.started("plain", NONE, Map.of(), Fixtures.Beans.class);
        assertThat(Fixtures.managed(plain).ready).isTrue();
        assertThat(Fixtures.managed(plain).bootLoader).isFalse();
    }

    @Test
    void h2OptionsAreAppendedToTheUrlAndTakeEffect() throws Exception {
        AnnotationConfigApplicationContext c = Fixtures.started("h2-options", NONE,
                Map.of("ptest.db.mode", "h2", "ptest.db.h2.options", ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"), Fixtures.AskForUrlConfig.class);
        String url = c.getBean("url", String.class);

        assertThat(url).startsWith("jdbc:h2:mem:").endsWith(";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        try (var conn = java.sql.DriverManager.getConnection(url, "sa", ""); var st = conn.createStatement();
             var rs = st.executeQuery("select setting_value from information_schema.settings where setting_name = 'MODE'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("PostgreSQL");
        }
    }

    @Test
    void withoutH2OptionsTheUrlIsUnchanged() {
        AnnotationConfigApplicationContext c = Fixtures.started("h2-no-options", NONE, Map.of("ptest.db.mode", "h2"), Fixtures.AskForUrlConfig.class);

        assertThat(c.getBean("url", String.class)).endsWith(";DB_CLOSE_DELAY=-1");
    }
}
