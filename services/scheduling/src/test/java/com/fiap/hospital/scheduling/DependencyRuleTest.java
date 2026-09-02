package com.fiap.hospital.scheduling;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
    packages = "com.fiap.hospital.scheduling",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class DependencyRuleTest {

    @ArchTest
    static final ArchRule participantsDoNotKnowAppointments =
        classes()
            .that().resideInAPackage("..participants..")
            .should(doNotKnowAppointments())
            .because("cadastrar paciente e médico tem de continuar funcionando "
                + "com o pacote de consulta apagado")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule featuresDoNotReachIntoEachOther =
        classes()
            .that().resideInAPackage("..")
            .should(featurePackagesAreIsolated())
            .because("cada feature do serviço deve continuar isolada; "
                + "o único acesso permitido a outra feature é ao aggregate root "
                + "ou ao contrato publicado")
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

    private static ArchCondition<JavaClass> doNotKnowAppointments() {
        return new ArchCondition<>("não depender de appointments em nenhum pacote") {
            @Override
            public void check(JavaClass source, ConditionEvents events) {
                for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetFeature = topLevelFeature(target.getPackageName());
                    if (targetFeature != null && targetFeature.equals("appointments")) {
                        events.add(SimpleConditionEvent.violated(
                            dependency,
                            dependency.getDescription()
                        ));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> featurePackagesAreIsolated() {
        return new ArchCondition<>("não depender de outra feature além de domain e contract") {
            @Override
            public void check(JavaClass source, ConditionEvents events) {
                String sourceFeature = topLevelFeature(source.getPackageName());
                if (sourceFeature == null) {
                    return;
                }

                for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetFeature = topLevelFeature(target.getPackageName());
                    if (targetFeature == null || sourceFeature.equals(targetFeature)) {
                        continue;
                    }
                    if (isPublishedFeatureContract(target, targetFeature)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(
                        dependency,
                        dependency.getDescription()
                    ));
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

    private static String topLevelFeature(String packageName) {
        String root = serviceRoot(packageName);
        if (root == null) {
            return null;
        }

        String localPackage = packageName.substring(root.length());
        if (localPackage.startsWith(".")) {
            localPackage = localPackage.substring(1);
        }
        if (localPackage.isEmpty()) {
            return null;
        }

        String candidate = localPackage.split("\\.", 2)[0];
        return isSharedPackageSegment(candidate) ? null : candidate;
    }

    private static String serviceRoot(String packageName) {
        if (packageName.startsWith("com.fiap.hospital.scheduling.")) {
            return "com.fiap.hospital.scheduling";
        }
        if (packageName.startsWith("com.fiap.hospital.archrule.fixture.")) {
            return "com.fiap.hospital.archrule.fixture";
        }
        return null;
    }

    private static boolean isPublishedFeatureContract(JavaClass target, String targetFeature) {
        String root = serviceRoot(target.getPackageName());
        if (root == null) {
            return false;
        }

        String localPackage = target.getPackageName().substring(root.length());
        if (localPackage.startsWith(".")) {
            localPackage = localPackage.substring(1);
        }
        String[] segments = localPackage.split("\\.");
        if (segments.length < 2 || !segments[0].equals(targetFeature)) {
            return false;
        }

        return segments[1].equals("domain") || segments[1].equals("contract");
    }

    private static boolean isSharedPackageSegment(String segment) {
        return Set.of(
            "config",
            "outbox",
            "api",
            "service",
            "repository",
            "internal",
            "domain",
            "contract",
            "fixture",
            "archrule"
        ).contains(segment);
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
