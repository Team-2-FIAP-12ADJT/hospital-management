package com.fiap.hospital.scheduling;

import com.fiap.hospital.scheduling.participants.domain.Doctor;
import com.fiap.hospital.scheduling.participants.domain.Patient;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
    static final ArchRule appointmentsUseOnlyPublishedParticipantContract =
        classes()
            .that().resideInAPackage("..appointments..")
            .should(dependOnlyOnPublishedParticipantContract())
            .because("appointments usa apenas o contrato publicado por participants; "
                + "quem escreve participante é participants")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharedPackagesKnowNoFeature =
        classes()
            .that().resideInAnyPackage("..outbox..", "..config..")
            .should(dependOnlyOnSharedInfrastructureInsideTheService())
            .because("a publicação de evento é genérica; quem sabe qual evento "
                + "está publicando é a feature")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule featureSlicesAreFreeOfCycles =
        slices()
            .matching("com.fiap.hospital.scheduling.(*)..")
            .should().beFreeOfCycles();

    private static ArchCondition<JavaClass> dependOnlyOnPublishedParticipantContract() {
        return new ArchCondition<>("depender somente do contrato publicado por participants") {
            @Override
            public void check(JavaClass source, ConditionEvents events) {
                for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (isParticipantType(target) && !isPublishedParticipantContract(target)) {
                        events.add(SimpleConditionEvent.violated(
                            dependency,
                            dependency.getDescription()
                        ));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> dependOnlyOnSharedInfrastructureInsideTheService() {
        return new ArchCondition<>("não depender de nenhuma feature do serviço") {
            @Override
            public void check(JavaClass source, ConditionEvents events) {
                String serviceRoot = serviceRootOfSharedClass(source);
                for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                    if (isFeatureInsideService(dependency.getTargetClass(), serviceRoot)) {
                        events.add(SimpleConditionEvent.violated(
                            dependency,
                            dependency.getDescription()
                        ));
                    }
                }
            }
        };
    }

    private static boolean isParticipantType(JavaClass target) {
        return hasPackageSegment(target, "participants");
    }

    private static boolean isPublishedParticipantContract(JavaClass target) {
        return target.getName().equals(Patient.class.getName())
            || target.getName().equals(Doctor.class.getName())
            || hasPackageSegment(target, "participants.contract");
    }

    private static boolean hasPackageSegment(JavaClass target, String segment) {
        String packageName = target.getPackageName();
        return packageName.equals(segment)
            || packageName.startsWith(segment + ".")
            || packageName.endsWith("." + segment)
            || packageName.contains("." + segment + ".");
    }

    private static String serviceRootOfSharedClass(JavaClass source) {
        String packageName = source.getPackageName();
        int configMarker = indexOfPackageSegment(packageName, "config");
        int outboxMarker = indexOfPackageSegment(packageName, "outbox");
        int marker = configMarker >= 0 && outboxMarker >= 0
            ? Math.min(configMarker, outboxMarker)
            : Math.max(configMarker, outboxMarker);
        return marker >= 0 ? packageName.substring(0, marker) : packageName;
    }

    private static int indexOfPackageSegment(String packageName, String segment) {
        String marker = "." + segment;
        int index = packageName.indexOf(marker);
        while (index >= 0) {
            int afterSegment = index + marker.length();
            if (afterSegment == packageName.length() || packageName.charAt(afterSegment) == '.') {
                return index;
            }
            index = packageName.indexOf(marker, afterSegment);
        }
        return -1;
    }

    private static boolean isFeatureInsideService(JavaClass target, String serviceRoot) {
        String packageName = target.getPackageName();
        String localPrefix = serviceRoot + ".";
        if (!packageName.startsWith(localPrefix)) {
            return false;
        }

        String localPackage = packageName.substring(localPrefix.length());
        String topLevelPackage = localPackage.split("\\.", 2)[0];
        return !topLevelPackage.equals("config") && !topLevelPackage.equals("outbox");
    }
}
