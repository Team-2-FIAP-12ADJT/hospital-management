package com.fiap.hospital.gateway;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

// O gateway não tem pacote de feature, e portanto não tem regra de direção.
@AnalyzeClasses(
    packages = "com.fiap.hospital.gateway",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class DependencyRuleTest {

    @ArchTest
    static final ArchRule slicesAreFreeOfCycles =
        slices()
            .matching("com.fiap.hospital.gateway.(*)..")
            .should().beFreeOfCycles();
}
