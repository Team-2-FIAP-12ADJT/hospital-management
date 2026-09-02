package com.fiap.hospital.identity;

import com.fiap.hospital.archrule.fixture.accounts.FixtureAccountService;
import com.fiap.hospital.archrule.fixture.accounts.FixtureAccountUsingActivationContract;
import com.fiap.hospital.archrule.fixture.activation.contract.FixtureActivationContract;
import com.fiap.hospital.archrule.fixture.activation.repository.FixtureActivationRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyRuleBitesTest {

    @Test
    void reportsAccountsReachingActivationInternals() {
        JavaClasses sabotaged = new ClassFileImporter().importClasses(
            FixtureAccountService.class,
            FixtureActivationRepository.class
        );

        EvaluationResult result =
            DependencyRuleTest.featuresDoNotReachIntoEachOther.evaluate(sabotaged);

        assertTrue(result.hasViolation(),
            "a regra deixou accounts depender de activation.repository");
    }

    @Test
    void staysSilentWhenAccountsUsesActivationContract() {
        JavaClasses allowed = new ClassFileImporter().importClasses(
            FixtureAccountUsingActivationContract.class,
            FixtureActivationContract.class
        );

        EvaluationResult result =
            DependencyRuleTest.featuresDoNotReachIntoEachOther.evaluate(allowed);

        assertFalse(result.hasViolation(),
            "a regra reprovou accounts usando o contrato publicado por activation");
    }
}
