package com.hittech.paralleltest.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.hittech.paralleltest.fixtures.Fixtures;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every rule must fire on its violating fixture and stay silent on compliant ones. */
class ClassRulesTest {

    static final GuardSettings DEFAULTS = GuardSettings.of(new Properties());

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(Fixtures.GoodCase.class, Set.of()),
                Arguments.of(Fixtures.GoodCase.Inner.class, Set.of()),
                Arguments.of(Fixtures.IsolatedContextCase.class, Set.of()),
                Arguments.of(Fixtures.MockitoBeanLockedCase.class, Set.of()),
                Arguments.of(Fixtures.DirtiesIsolatedCase.class, Set.of()),
                Arguments.of(Fixtures.ForbiddenCallIsolatedCase.class, Set.of()),
                Arguments.of(Fixtures.NoBaseCase.class, Set.of(Rule.PT01)),
                Arguments.of(Fixtures.PropertyBelowBaseCase.class, Set.of(Rule.PT02)),
                Arguments.of(Fixtures.ImportBelowBaseCase.class, Set.of(Rule.PT02)),
                Arguments.of(Fixtures.NestedConfigCase.class, Set.of(Rule.PT02)),
                Arguments.of(Fixtures.DynamicPropertyCase.class, Set.of(Rule.PT02)),
                Arguments.of(Fixtures.MockitoBeanCase.class, Set.of(Rule.PT02, Rule.PT03)),
                Arguments.of(Fixtures.DirtiesCase.class, Set.of(Rule.PT04)),
                Arguments.of(Fixtures.UnroutedMockCase.class, Set.of(Rule.PT05)),
                Arguments.of(Fixtures.StaticStateCase.class, Set.of(Rule.PT06)),
                Arguments.of(Fixtures.ForbiddenCallCase.class, Set.of(Rule.PT07)),
                Arguments.of(Fixtures.FixedPortCase.class, Set.of(Rule.PT08)),
                Arguments.of(Fixtures.ListenersReplacedCase.class, Set.of(Rule.PT09)));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void rulesFireExactlyWhereExpected(Class<?> fixture, Set<Rule> expected) {
        List<Violation> violations = ClassRules.doCheck(fixture, DEFAULTS);
        assertThat(violations.stream().map(Violation::rule).collect(Collectors.toSet()))
                .as("violations of %s:%n%s", fixture.getSimpleName(), violations)
                .isEqualTo(expected);
    }

    @Test
    void forbiddenCallsPointToMethodAndLineIncludingLambdas() {
        List<Violation> v = ClassRules.doCheck(Fixtures.ForbiddenCallCase.class, DEFAULTS);
        assertThat(v).hasSize(2);
        assertThat(v).extracting(Violation::location)
                .anySatisfy(l -> assertThat(l).contains("ForbiddenCallCase.test(Fixtures.java:"))
                .anySatisfy(l -> assertThat(l).contains("ForbiddenCallCase.test<lambda>(Fixtures.java:"));
        assertThat(v).extracting(Violation::detail)
                .anySatisfy(d -> assertThat(d).contains("ThingRepository.deleteAll"))
                .anySatisfy(d -> assertThat(d).contains("ThingRepository.count"));
    }

    @Test
    void projectSpecificForbiddenCalls() {
        Properties p = new Properties();
        p.setProperty("forbidden-calls", Fixtures.Collaborator.class.getName() + "#hello()");
        List<Violation> v = ClassRules.doCheck(Fixtures.CustomForbiddenCallCase.class, GuardSettings.of(p));
        assertThat(v).singleElement().satisfies(x -> {
            assertThat(x.rule()).isEqualTo(Rule.PT07);
            assertThat(x.detail()).contains("Collaborator.hello");
        });
        assertThat(ClassRules.doCheck(Fixtures.CustomForbiddenCallCase.class, DEFAULTS)).isEmpty();
    }

    @Test
    void messageExplainsWhatWhyAndFix() {
        String msg = new ParallelGuardViolation("x", ClassRules.doCheck(Fixtures.MockitoBeanCase.class, DEFAULTS)).getMessage();
        assertThat(msg).contains("[PT03]", "MockitoBeanCase.collaborator", "why:", "fix:", "@PerTestMock");
    }

    @Test
    void scannerReportsEverythingIncludingHelpers() {
        List<Violation> all = ParallelSafetyScanner.scan(ClassRulesTest.class);
        assertThat(all).extracting(Violation::location)
                .anySatisfy(l -> assertThat(l).contains("CleanupHelper.wipe"))
                .anySatisfy(l -> assertThat(l).contains("NoBaseCase"))
                .noneSatisfy(l -> assertThat(l).contains("GoodCase"));
        assertThat(all.stream().map(Violation::rule).collect(Collectors.toSet()))
                .containsAll(Set.of(Rule.PT01, Rule.PT02, Rule.PT03, Rule.PT04, Rule.PT05, Rule.PT06, Rule.PT07,
                        Rule.PT08, Rule.PT09));
    }
}
