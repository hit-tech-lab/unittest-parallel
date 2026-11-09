package io.hit.ptest.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hit.ptest.ParallelTestException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.core.SpringProperties;

/** Cấu hình ptest.guard.*: mặc định, ghi đè theo luật, tắt toàn bộ, giá trị sai là lỗi, danh sách gọi bị cấm. */
@Isolated
class GuardSettingsTest {

    @AfterEach
    void clear() {
        for (String k : List.of("ptest.guard.enabled", "ptest.guard.PT07", "ptest.guard.PT01", "ptest.guard.forbidden-calls",
                "ptest.guard.scan-exclude")) {
            SpringProperties.setProperty(k, null);
        }
    }

    @Test
    void defaultsComeFromTheRuleDefinitions() {
        for (GuardRule r : GuardRule.values()) {
            assertThat(GuardSettings.severity(r)).as(r.name()).isEqualTo(r.defaultSeverity());
        }
        assertThat(GuardRule.PT01.defaultSeverity()).isEqualTo(Severity.WARN);
        assertThat(GuardRule.PT07.defaultSeverity()).isEqualTo(Severity.FAIL);
    }

    @Test
    void perRuleOverrideIsCaseInsensitiveAndOnlyAffectsThatRule() {
        SpringProperties.setProperty("ptest.guard.PT07", "Warn");
        SpringProperties.setProperty("ptest.guard.PT01", "off");
        assertThat(GuardSettings.severity(GuardRule.PT07)).isEqualTo(Severity.WARN);
        assertThat(GuardSettings.severity(GuardRule.PT01)).isEqualTo(Severity.OFF);
        assertThat(GuardSettings.severity(GuardRule.PT06)).isEqualTo(Severity.FAIL);
    }

    @Test
    void disablingTheGuardTurnsEveryRuleOff() {
        SpringProperties.setProperty("ptest.guard.enabled", "false");
        for (GuardRule r : GuardRule.values()) {
            assertThat(GuardSettings.severity(r)).as(r.name()).isEqualTo(Severity.OFF);
        }
    }

    @Test
    void unknownSeverityIsAConfigurationErrorNotASilentDefault() {
        SpringProperties.setProperty("ptest.guard.PT07", "maybe");
        assertThatThrownBy(() -> GuardSettings.severity(GuardRule.PT07)).isInstanceOf(ParallelTestException.Config.class)
                .hasMessageContaining("ptest.guard.PT07=maybe");
    }

    @Test
    void extraForbiddenCallsAreAppendedAndCommasInsideParenthesesDoNotSplit() {
        int defaults = GuardSettings.forbiddenCalls().size();
        SpringProperties.setProperty("ptest.guard.forbidden-calls", "a.B#c(x.Y, z.W), a.B#d()");
        List<ForbiddenCall> calls = GuardSettings.forbiddenCalls();
        assertThat(calls).hasSize(defaults + 2);
        assertThat(calls.get(defaults)).isEqualTo(new ForbiddenCall("a.B", "c", List.of("x.Y", "z.W")));
        assertThat(calls.get(defaults + 1)).isEqualTo(new ForbiddenCall("a.B", "d", List.of()));
    }

    @Test
    void malformedForbiddenCallFailsLoudly() {
        SpringProperties.setProperty("ptest.guard.forbidden-calls", "NoHashHere()");
        assertThatThrownBy(GuardSettings::forbiddenCalls).isInstanceOf(ParallelTestException.Config.class)
                .hasMessageContaining("NoHashHere()");
    }

    @Test
    void defaultForbiddenCallsCoverTheWholeTableMethods() {
        assertThat(GuardSettings.forbiddenCalls().stream().map(ForbiddenCall::toString)).contains(
                "org.springframework.data.repository.CrudRepository#deleteAll()",
                "org.springframework.data.repository.CrudRepository#count()",
                "org.springframework.data.repository.CrudRepository#findAll()",
                "org.springframework.test.jdbc.JdbcTestUtils#deleteFromTables(*)");
    }
}
