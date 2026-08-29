package com.fiap.hospital.history;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
    packages = "com.fiap.hospital.history",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class DependencyRuleTest {

    @ArchTest
    static final ArchRule slicesAreFreeOfCycles =
        slices()
            .matching("com.fiap.hospital.history.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);
}
