package demo.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import demo.core.DemoApp;
import demo.core.ExchangeRateClient;
import demo.project.Ports;
import demo.support.Rendezvous;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.ParallelTest;
import io.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Ba test chạy đồng thời (rào chắn 3 bên) trong lớp bao ngoài và hai lớp {@code @Nested}:
 * <ul>
 *   <li>{@link FieldOnly} chỉ có field {@code @PerTestMock}: khóa context KHÔNG đổi, dùng chung context với lớp bao ngoài;</li>
 *   <li>{@link OwnDeclaration} tự khai {@code @PerTestMock} cấp class: tập khai báo khác nên context KHÁC (report: 2 context "nested-ctx").</li>
 * </ul>
 */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "nested-ctx")
class NestedDeclarationIT {

    @Autowired ApplicationContext ctx;

    private static void verifyContexts() {
        Rendezvous.meet("nested-ctx", 3);
        assertThat(Rendezvous.shared("nested-ctx:outer")).as("outer class: one context").hasSize(1);
        assertThat(Rendezvous.shared("nested-ctx:field-only")).as("field-only @Nested: the outer context")
                .isEqualTo(Rendezvous.shared("nested-ctx:outer"));
        assertThat(Rendezvous.shared("nested-ctx:own")).as("@Nested with its own class-level declaration: another context")
                .hasSize(1).doesNotContainAnyElementsOf(Rendezvous.shared("nested-ctx:outer"));
    }

    @Test
    void outerClassUsesTheBaseContext() {
        Rendezvous.share("nested-ctx:outer", ParallelTest.contextKey(ctx));
        verifyContexts();
        assertThat(ctx.containsBean("mailer")).as("declaration of the nested class does not leak outwards").isFalse();
    }

    @Nested
    class FieldOnly {
        @Autowired ApplicationContext nestedCtx;
        @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

        @Test
        void sharesTheOuterContextAndHasItsOwnMock() {
            Rendezvous.share("nested-ctx:field-only", ParallelTest.contextKey(nestedCtx));
            when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("3"));
            verifyContexts();
            assertThat(nestedCtx.containsBean("mailer")).isFalse();
        }
    }

    @Nested
    @PerTestMock(Ports.Mailer.class)
    class OwnDeclaration {
        @Autowired ApplicationContext nestedCtx;
        @PerTestMock Ports.Mailer mailer;

        @Test
        void ownClassLevelDeclarationMakesAnotherContext() {
            Rendezvous.share("nested-ctx:own", ParallelTest.contextKey(nestedCtx));
            when(mailer.send("a@b.c")).thenReturn("sent");
            verifyContexts();
            assertThat(nestedCtx.containsBean("mailer")).isTrue();
            assertThat(mailer.send("a@b.c")).isEqualTo("sent");
        }
    }
}
