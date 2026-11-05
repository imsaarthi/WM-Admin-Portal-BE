package com.wealthmax;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import com.wealthmax.support.architecture.ModuleBoundaryRules;

/**
 * The module boundaries of the whole application (ADR-005). One test covers every top-level package under
 * com.wealthmax, so a new module is checked from its first class with no new test. No database.
 *
 * Do not add an exception to make a failure go away. Call the other module's service interface, or move the
 * shared piece into common.
 *
 * ModuleBoundaryRulesTest proves that each rule really catches a violation.
 */
class ModuleBoundaryArchitectureTest {

    private static final String BASE = "com.wealthmax";
    private static final String OLD_REPOSITORY = "com.wealthmax.adminPortal";

    private static JavaClasses applicationClasses;

    @BeforeAll
    static void importApplicationClasses() {
        applicationClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    @DisplayName("no module reaches into another module's entity, repository, mapper or service.impl")
    void noModuleReachesIntoAnotherModulesInternals() {
        ModuleBoundaryRules.noModuleReachesIntoAnotherModulesInternals(BASE).check(applicationClasses);
    }

    @Test
    @DisplayName("common depends on no module")
    void commonDependsOnNoModule() {
        ModuleBoundaryRules.commonDependsOnNoModule(BASE).check(applicationClasses);
    }

    @Test
    @DisplayName("modules have no dependency cycles")
    void noCyclesBetweenModules() {
        ModuleBoundaryRules.noCyclesBetweenModules(BASE).check(applicationClasses);
    }

    @Test
    @DisplayName("nothing is imported from the old repository (com.wealthmax.adminPortal)")
    void nothingIsImportedFromTheOldRepository() {
        ModuleBoundaryRules.nothingDependsOn(OLD_REPOSITORY).check(applicationClasses);
    }
}
