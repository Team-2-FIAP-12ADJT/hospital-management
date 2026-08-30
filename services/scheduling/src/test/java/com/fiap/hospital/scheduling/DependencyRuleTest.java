package com.fiap.hospital.scheduling;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
    packages = "com.fiap.hospital.scheduling",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class DependencyRuleTest {

    // Mantém a regra executável enquanto uma das features protegidas ainda não tiver classes.
    @ArchTest
    static final ArchRule participantsDoNotKnowAppointments =
        noClasses()
            .that().resideInAPackage("..participants..")
            .should().dependOnClassesThat().resideInAPackage("..appointments..")
            .because("cadastrar paciente e médico tem de continuar funcionando "
                + "com o pacote de consulta apagado")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule appointmentsDoNotReachParticipantInternals =
        noClasses()
            .that().resideInAPackage("..appointments..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..participants.api..",
                "..participants.repository..",
                "..participants.service.."
            )
            .because("appointments usa apenas o contrato publicado por participants; "
                + "quem escreve participante é participants")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharedPackagesKnowNoFeature =
        noClasses()
            .that().resideInAnyPackage("..outbox..", "..config..")
            .should().dependOnClassesThat().resideInAnyPackage("..participants..", "..appointments..")
            .because("a publicação de evento é genérica; quem sabe qual evento "
                + "está publicando é a feature");

    @ArchTest
    static final ArchRule featureSlicesAreFreeOfCycles =
        slices()
            .matching("com.fiap.hospital.scheduling.(*)..")
            .should().beFreeOfCycles();
}
