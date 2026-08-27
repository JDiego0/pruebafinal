package co.riwi.messaging;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * TEST-11 / ARCH-04: mechanically enforces the dependency rule from
 * ARCHITECTURE.md §3.1 (presentation -> application -> domain <- infrastructure).
 * If this test fails, fix the design, not the test (CLAUDE.md).
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "co.riwi.messaging";

    @Test
    void domainMustNotDependOnFrameworkOrInfrastructureTypes() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.postgresql..",
                        "java.sql..",
                        "com.fasterxml.jackson..",
                        "jakarta.servlet..",
                        "io.jsonwebtoken.."
                )
                .because("ARCH-03: the domain layer must have zero dependency on Spring, JDBC, JPA, Jackson, the servlet API, or JWT libraries.");

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE));
    }

    @Test
    void domainMustNotDependOnOtherLayers() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".application..",
                        BASE_PACKAGE + ".infrastructure..",
                        BASE_PACKAGE + ".presentation.."
                )
                .because("the domain layer depends on nothing else in this codebase.");

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE));
    }

    @Test
    void applicationMustNotDependOnServletOrJdbcDriverTypes() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..",
                        "org.postgresql..",
                        "io.jsonwebtoken.."
                )
                .because("use cases depend on domain ports, never on how those ports are implemented.");

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE));
    }

    @Test
    void applicationMustNotDependOnInfrastructureOrPresentation() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".infrastructure..",
                        BASE_PACKAGE + ".presentation.."
                )
                .because("use cases depend only on domain (model, error, port).");

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE));
    }

    @Test
    void onlyPresentationMayDependOnSpringWeb() {
        ArchRule rule = classes().that().resideInAnyPackage(
                        BASE_PACKAGE + ".domain..", BASE_PACKAGE + ".application..")
                .should().notBeAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .because("controllers belong in presentation only.");

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE));
    }

    @Test
    void useCasesLiveOnlyInApplicationUsecase() {
        ArchRule rule = classes().that().haveSimpleNameEndingWith("UseCase")
                .should().resideInAPackage(BASE_PACKAGE + ".application.usecase")
                .because("thin use cases (ARCH-05) live in exactly one package, never scattered across layers.");

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE));
    }
}
