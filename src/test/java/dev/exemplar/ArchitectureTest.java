package dev.exemplar;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void dependenciesPointTowardTheDomain() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.exemplar.artifact");
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideOutsideOfPackages("java..", "dev.exemplar.artifact.domain..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideOutsideOfPackages(
            "java..", "dev.exemplar.artifact.domain..", "dev.exemplar.artifact.application..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..http..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..aws..")
        .check(classes);
    slices().matching("dev.exemplar.artifact.(*)..").should().beFreeOfCycles().check(classes);
  }
}
