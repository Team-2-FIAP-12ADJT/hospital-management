package com.fiap.hospital.scheduling;

import com.fiap.hospital.archrule.fixture.appointments.FixtureAppointmentService;
import com.fiap.hospital.archrule.fixture.appointments.FixtureAppointmentUsingContract;
import com.fiap.hospital.archrule.fixture.appointments.FixtureAppointmentWriterClient;
import com.fiap.hospital.archrule.fixture.config.FixtureFeatureAwareConfig;
import com.fiap.hospital.archrule.fixture.participants.FixtureParticipantContract;
import com.fiap.hospital.archrule.fixture.participants.repository.FixtureParticipantRepository;
import com.fiap.hospital.archrule.fixture.participants.service.FixtureParticipantService;
import com.fiap.hospital.scheduling.cyclealpha.FixtureCycleAlpha;
import com.fiap.hospital.scheduling.cyclebeta.FixtureCycleBeta;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova que as regras de DependencyRuleTest reprovam violações isoladas e
 * aceitam o contrato publicado entre as features.
 *
 * Avalia as instâncias de DependencyRuleTest de propósito: uma regra
 * equivalente declarada aqui provaria que a cópia funciona, não a de produção.
 */
class DependencyRuleBitesTest {

    private static final String FIXTURE_ROOT = "com.fiap.hospital.archrule.fixture";

    @Test
    void reportsTheForbiddenDirection() {
        JavaClasses sabotaged = new ClassFileImporter().importPackages(FIXTURE_ROOT);

        EvaluationResult result =
            DependencyRuleTest.participantsDoNotKnowAppointments.evaluate(sabotaged);

        assertTrue(result.hasViolation(),
            "a regra não enxergou ..participants.. dependendo de ..appointments..");
    }

    @Test
    void reportsAppointmentsReachingParticipantInternals() {
        JavaClasses sabotaged = new ClassFileImporter().importClasses(
            FixtureAppointmentService.class,
            FixtureParticipantRepository.class
        );

        EvaluationResult result =
            DependencyRuleTest.appointmentsDoNotReachParticipantInternals.evaluate(sabotaged);

        assertTrue(result.hasViolation(),
            "a regra não enxergou ..appointments.. alcançando ..participants.repository..");
    }

    @Test
    void reportsAppointmentsCallingParticipantServices() {
        JavaClasses sabotaged = new ClassFileImporter().importClasses(
            FixtureAppointmentWriterClient.class,
            FixtureParticipantService.class
        );

        EvaluationResult result =
            DependencyRuleTest.appointmentsDoNotReachParticipantInternals.evaluate(sabotaged);

        assertTrue(result.hasViolation(),
            "a regra não enxergou ..appointments.. alcançando ..participants.service..");
    }

    @Test
    void staysSilentWhenAppointmentsUsesThePublishedContract() {
        JavaClasses onlyTheAllowedSide = new ClassFileImporter().importClasses(
            FixtureAppointmentUsingContract.class,
            FixtureParticipantContract.class
        );

        EvaluationResult result =
            DependencyRuleTest.appointmentsDoNotReachParticipantInternals.evaluate(onlyTheAllowedSide);

        assertFalse(result.hasViolation(),
            "a regra reprovou appointments usando o contrato publicado por participants");
    }

    @Test
    void reportsSharedPackageDependingOnAFeature() {
        JavaClasses sabotaged = new ClassFileImporter().importClasses(
            FixtureFeatureAwareConfig.class,
            FixtureParticipantContract.class
        );

        EvaluationResult result =
            DependencyRuleTest.sharedPackagesKnowNoFeature.evaluate(sabotaged);

        assertTrue(result.hasViolation(),
            "a regra não enxergou ..config.. dependendo de ..participants..");
    }

    @Test
    void reportsACycleBetweenFeatureSlices() {
        JavaClasses sabotaged = new ClassFileImporter().importClasses(
            FixtureCycleAlpha.class,
            FixtureCycleBeta.class
        );

        EvaluationResult result =
            DependencyRuleTest.featureSlicesAreFreeOfCycles.evaluate(sabotaged);

        assertTrue(result.hasViolation(),
            "a regra não enxergou um ciclo entre duas features");
    }
}
