package com.hit.ptest.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hit.ptest.ParallelTestException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.core.SpringProperties;

/** FAIL/WARN/OFF trên danh sách vi phạm: thông điệp có mã luật + vị trí + lý do + cách sửa; mã luật ổn định. */
@Isolated
class GuardEnforcerTest {

    private static final Violation PT07 = new Violation(GuardRule.PT07, "a.B.m(B.java:10)", "calls X.deleteAll");
    private static final Violation PT06 = new Violation(GuardRule.PT06, "a.B.counter", "static int counter is not final");

    @AfterEach
    void clear() {
        SpringProperties.setProperty("ptest.guard.PT07", null);
        SpringProperties.setProperty("ptest.guard.enabled", null);
    }

    @Test
    void failThrowsGuardExceptionListingEveryViolationWithLocationWhyAndFix() {
        assertThatThrownBy(() -> GuardEnforcer.enforce("a.B is not parallel-safe", List.of(PT07, PT06)))
                .isInstanceOf(ParallelTestException.Guard.class)
                .hasMessageContaining("a.B is not parallel-safe (2 violations)")
                .hasMessageContaining("[PT07] " + GuardRule.PT07.title()).hasMessageContaining("at:   a.B.m(B.java:10)")
                .hasMessageContaining("what: calls X.deleteAll").hasMessageContaining("why:  " + GuardRule.PT07.why())
                .hasMessageContaining("fix:  " + GuardRule.PT07.fix()).hasMessageContaining("[PT06]")
                .satisfies(e -> assertThat(((ParallelTestException.Guard) e).ruleCodes()).containsExactly("PT07", "PT06"));
    }

    @Test
    void warnOnlyRuleDoesNotFailButTheOtherStill() {
        SpringProperties.setProperty("ptest.guard.PT07", "warn");
        assertThatNoException().isThrownBy(() -> GuardEnforcer.enforce("h", List.of(PT07)));
        assertThatThrownBy(() -> GuardEnforcer.enforce("h", List.of(PT07, PT06))).hasMessageContaining("(1 violation)")
                .hasMessageContaining("[PT06]").satisfies(e -> assertThat(e.getMessage()).doesNotContain("[PT07]"));
    }

    @Test
    void offAndDisabledDropViolationsEntirely() {
        SpringProperties.setProperty("ptest.guard.PT07", "off");
        assertThatNoException().isThrownBy(() -> GuardEnforcer.enforce("h", List.of(PT07)));
        SpringProperties.setProperty("ptest.guard.PT07", null);
        SpringProperties.setProperty("ptest.guard.enabled", "false");
        assertThatNoException().isThrownBy(() -> GuardEnforcer.enforce("h", List.of(PT07, PT06)));
    }

    @Test
    void noViolationsMeansNoException() {
        assertThatNoException().isThrownBy(() -> GuardEnforcer.enforce("h", List.of()));
    }

    @Test
    void ruleCodesAreStableAndEachRuleExplainsWhatWhyAndFix() {
        assertThat(java.util.Arrays.stream(GuardRule.values()).map(Enum::name)).containsExactly(
                "PT01", "PT03", "PT06", "PT07", "PT08", "PT10", "PT11", "PT12", "PT13");
        for (GuardRule r : GuardRule.values()) {
            assertThat(r.title()).isNotBlank();
            assertThat(r.why()).isNotBlank();
            assertThat(r.fix()).isNotBlank();
        }
    }
}
