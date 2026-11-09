package com.hit.ptest.arch;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.ParallelTest;
import com.hit.ptest.junit.ParallelTestExtension;
import org.junit.jupiter.api.Test;

/**
 * Khóa cấu trúc package của thư viện: không có vòng phụ thuộc giữa các package và đúng chiều phụ thuộc.
 *
 * <p>Package gốc {@code com.hit.ptest} là API công khai (annotation, enum, exception) và tính như một lát riêng.
 * Hai cạnh được loại khỏi kiểm tra vòng vì bản chất là cổng ra ngoài của API: {@code @ParallelSpringTest} khai báo
 * {@code @ExtendWith(ParallelTestExtension.class)} (JUnit bắt buộc tham chiếu lớp), và facade {@code ParallelTest}
 * gọi vào các package trong. Mọi cạnh khác đều bị kiểm tra.
 */
class PackageArchitectureTest {

    private static final String ROOT = "com.hit.ptest";
    private static final com.tngtech.archunit.core.domain.JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    private static final SliceAssignment BY_PACKAGE = new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass c) {
            String pkg = c.getPackageName();
            if (pkg.equals(ROOT)) {
                return SliceIdentifier.of("api");
            }
            return pkg.startsWith(ROOT + ".") ? SliceIdentifier.of(pkg.substring(ROOT.length() + 1).split("\\.")[0])
                    : SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
            return "package con cua " + ROOT;
        }
    };

    @Test
    void packagesAreFreeOfCycles() {
        slices().assignedFrom(BY_PACKAGE).should().beFreeOfCycles()
                .ignoreDependency(ParallelSpringTest.class, ParallelTestExtension.class)
                .ignoreDependency(JavaClass.Predicates.equivalentTo(ParallelTest.class), alwaysTrue())
                .check(MAIN);
    }

    @Test
    void interceptAndDbDependOnlyOnTheApiPackage() {
        noClasses().that().resideInAPackage(ROOT + ".intercept..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".context..", ROOT + ".propagation..",
                        ROOT + ".junit..", ROOT + ".db..")
                .check(MAIN);
        noClasses().that().resideInAPackage(ROOT + ".db..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".context..", ROOT + ".propagation..",
                        ROOT + ".junit..", ROOT + ".intercept..")
                .check(MAIN);
    }

    @Test
    void propagationDependsOnlyOnInterceptAndApi() {
        noClasses().that().resideInAPackage(ROOT + ".propagation..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".context..", ROOT + ".db..", ROOT + ".junit..")
                .check(MAIN);
    }

    @Test
    void contextDoesNotDependOnJunitIntegration() {
        noClasses().that().resideInAPackage(ROOT + ".context..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".junit..")
                .check(MAIN);
    }

    @Test
    void nothingDependsOnTheJunitPackageExceptTheApiAnnotation() {
        noClasses().that().resideOutsideOfPackages(ROOT + ".junit..").and().doNotHaveFullyQualifiedName(ParallelSpringTest.class.getName())
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".junit..")
                .check(MAIN);
    }

    /** Guard dùng độc lập với cơ chế can thiệp: chỉ phụ thuộc API gốc (annotation, exception), không phụ thuộc package khác của thư viện. */
    @Test
    void guardDependsOnlyOnTheApiPackage() {
        noClasses().that().resideInAPackage(ROOT + ".guard..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".intercept..", ROOT + ".context..",
                        ROOT + ".propagation..", ROOT + ".junit..", ROOT + ".db..")
                .check(MAIN);
    }

    /** Chỉ context (lắp guard JDBC và ngân sách context) và junit (tích hợp vòng đời test) được gọi guard. */
    @Test
    void onlyContextAndJunitUseTheGuard() {
        noClasses().that().resideInAnyPackage(ROOT + ".intercept..", ROOT + ".db..", ROOT + ".propagation..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".guard..")
                .check(MAIN);
    }
}
