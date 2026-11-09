package demo.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

/**
 * Consumer chỉ được dùng API công khai của thư viện: các lớp ở gốc {@code io.hit.ptest} (không tính package con) và
 * {@code io.hit.ptest.propagation.BindingHeaderInterceptor} và {@code io.hit.ptest.guard.AbstractParallelSafetyTest}. Mọi lớp trong package con khác là nội bộ.
 * Chỉ quét lớp của consumer ({@code demo..}); bản thân thư viện không thuộc phạm vi.
 */
class ConsumerApiUsageIT {

    private static final String PUBLIC_PROPAGATION_API = "io.hit.ptest.propagation.BindingHeaderInterceptor";
    private static final String PUBLIC_GUARD_API = "io.hit.ptest.guard.AbstractParallelSafetyTest";

    @Test
    void consumerUsesOnlyPublicApi() {
        var consumer = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages("demo");
        classes().that().resideInAPackage("demo..")
                .should(new ArchCondition<JavaClass>("only depend on io.hit.ptest root API, BindingHeaderInterceptor or AbstractParallelSafetyTest") {
                    @Override
                    public void check(JavaClass c, ConditionEvents events) {
                        c.getDirectDependenciesFromSelf().stream()
                                .map(d -> d.getTargetClass())
                                .filter(t -> t.getPackageName().startsWith("io.hit.ptest."))
                                .filter(t -> !t.getFullName().equals(PUBLIC_PROPAGATION_API) && !t.getFullName().equals(PUBLIC_GUARD_API))
                                .forEach(t -> events.add(SimpleConditionEvent.violated(c,
                                        c.getName() + " uses internal " + t.getFullName())));
                    }
                })
                .check(consumer);
    }
}
