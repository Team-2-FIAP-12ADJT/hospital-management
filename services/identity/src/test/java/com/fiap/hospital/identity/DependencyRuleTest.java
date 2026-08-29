package com.fiap.hospital.identity;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
    packages = "com.fiap.hospital.identity",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class DependencyRuleTest {

    @ArchTest
    static final ArchRule configKnowsNoFeature =
        noClasses()
            .that().resideInAPackage("..identity.config..")
            .should().dependOnClassesThat().resideInAnyPackage("..accounts..", "..activation..")
            .because("a configuração é genérica; quem sabe o que está "
                + "configurando é a feature");

    @ArchTest
    static final ArchRule featureSlicesAreFreeOfCycles =
        slices()
            .matching("com.fiap.hospital.identity.(*)..")
            .should().beFreeOfCycles();
}
