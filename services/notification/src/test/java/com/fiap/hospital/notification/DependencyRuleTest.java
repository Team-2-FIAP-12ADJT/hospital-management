package com.fiap.hospital.notification;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
    packages = "com.fiap.hospital.notification",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class DependencyRuleTest {

    @ArchTest
    static final ArchRule slicesAreFreeOfCycles =
        slices()
            .matching("com.fiap.hospital.notification.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);
}
