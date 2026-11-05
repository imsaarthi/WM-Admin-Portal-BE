package com.wealthmax.support.architecture;

import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * The module rules (ADR-005). A module is the first package segment under the base package, so
 * com.wealthmax.careers is the module "careers" and a new module needs no new test.
 *
 * The rules take the base package as a parameter so they can be run on the real code and also on the small
 * fixture classes that prove each rule really catches a violation.
 */
public final class ModuleBoundaryRules {

    private static final String COMMON = "common";

    /** Packages that belong to a module and that no other module may touch. */
    private static final List<String> INTERNAL_PACKAGES = List.of("entity", "repository", "mapper", "service.impl");

    private ModuleBoundaryRules() {
    }

    /** Rule 1: a module uses another module only through its service interfaces, DTOs, enums and events. */
    public static ArchRule noModuleReachesIntoAnotherModulesInternals(String basePackage) {
        ArchCondition<JavaClass> condition = new ArchCondition<>(
                "not depend on another module's entity, repository, mapper or service.impl package") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String from = moduleOf(basePackage, origin.getPackageName());
                if (from == null || COMMON.equals(from)) {
                    return; // the application class, or common (see rule 2)
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    String to = moduleOf(basePackage, targetPackage);
                    if (to != null && !to.equals(from) && !COMMON.equals(to)
                            && isInternal(basePackage, to, targetPackage)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
        return ArchRuleDefinition.classes().should(condition).allowEmptyShould(true)
                .as("no module reaches into another module's internals");
    }

    /** Rule 2: the shared kernel knows no module. */
    public static ArchRule commonDependsOnNoModule(String basePackage) {
        ArchCondition<JavaClass> condition = new ArchCondition<>("not depend on any module") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                if (!COMMON.equals(moduleOf(basePackage, origin.getPackageName()))) {
                    return;
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String to = moduleOf(basePackage, dependency.getTargetClass().getPackageName());
                    if (to != null && !COMMON.equals(to)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
        return ArchRuleDefinition.classes().should(condition).allowEmptyShould(true)
                .as("common depends on no module");
    }

    /** Rule 3: modules do not depend on each other in a circle. */
    public static ArchRule noCyclesBetweenModules(String basePackage) {
        return SlicesRuleDefinition.slices().matching(basePackage + ".(*)..")
                .should().beFreeOfCycles().allowEmptyShould(true)
                .as("no dependency cycles between modules");
    }

    /** Rule 4: nothing is imported from the old repository. Port behaviour, never code. */
    public static ArchRule nothingDependsOn(String forbiddenPackage) {
        DescribedPredicate<JavaClass> forbidden = JavaClass.Predicates.resideInAPackage(forbiddenPackage + "..");
        return ArchRuleDefinition.noClasses().should().dependOnClassesThat(forbidden).allowEmptyShould(true)
                .as("nothing depends on " + forbiddenPackage);
    }

    /** "com.wealthmax.careers.entity" with base "com.wealthmax" is the module "careers". Anything else is null. */
    static String moduleOf(String basePackage, String packageName) {
        String prefix = basePackage + ".";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String rest = packageName.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static boolean isInternal(String basePackage, String module, String packageName) {
        String insideModule = packageName.substring((basePackage + "." + module).length()) + ".";
        return INTERNAL_PACKAGES.stream().anyMatch(internal -> insideModule.startsWith("." + internal + "."));
    }
}
