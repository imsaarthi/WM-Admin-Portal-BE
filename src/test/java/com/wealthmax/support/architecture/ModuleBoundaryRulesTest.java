package com.wealthmax.support.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

/**
 * Tests the rules themselves. A rule that quietly stops catching violations would still let
 * ModuleBoundaryArchitectureTest pass, so each rule is run on code that follows it (it must pass) and on code
 * that breaks it (it must report a violation). The fixture classes live under src/test/java/archfixture.
 */
class ModuleBoundaryRulesTest {

    private static final String GOOD = "archfixture.good";
    private static final String BAD = "archfixture.bad";

    private static JavaClasses good;
    private static JavaClasses bad;

    @BeforeAll
    static void importFixtures() {
        good = new ClassFileImporter().importPackages(GOOD);
        bad = new ClassFileImporter().importPackages(BAD);
    }

    @Test
    @DisplayName("the bad fixtures are really imported")
    void badFixturesAreImported() {
        assertThat(bad.stream().map(c -> c.getName())).contains(
                "archfixture.bad.common.util.Helper",
                "archfixture.bad.shop.service.ShopService",
                "archfixture.bad.billing.service.BillingService",
                "archfixture.bad.billing.entity.Invoice");
    }

    @Test
    @DisplayName("rule 1 reports a module that uses another module's entity")
    void ruleOneCatchesAnEntityLeak() {
        EvaluationResult result = ModuleBoundaryRules.noModuleReachesIntoAnotherModulesInternals(BAD).evaluate(bad);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString())
                .contains("archfixture.bad.shop.service.ShopService")
                .contains("archfixture.bad.billing.entity.Invoice");
    }

    @Test
    @DisplayName("rule 2 reports common depending on a module")
    void ruleTwoCatchesCommonDependingOnAModule() {
        EvaluationResult result = ModuleBoundaryRules.commonDependsOnNoModule(BAD).evaluate(bad);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString()).contains("archfixture.bad.common.util.Helper");
    }

    @Test
    @DisplayName("rule 3 reports two modules that depend on each other")
    void ruleThreeCatchesACycle() {
        assertThat(ModuleBoundaryRules.noCyclesBetweenModules(BAD).evaluate(bad).hasViolation()).isTrue();
    }

    @Test
    @DisplayName("rule 4 reports a dependency on the forbidden (old repository) package")
    void ruleFourCatchesAForbiddenImport() {
        EvaluationResult result = ModuleBoundaryRules.nothingDependsOn("archfixture.bad.billing.entity").evaluate(bad);

        assertThat(result.hasViolation()).isTrue();
    }

    @Test
    @DisplayName("the fixtures are really imported (an empty import would make every rule pass)")
    void fixturesAreImported() {
        assertThat(good.stream().map(c -> c.getName())).contains(
                "archfixture.good.billing.service.impl.BillingServiceImpl",
                "archfixture.good.shop.service.ShopService",
                "archfixture.good.common.util.Strings");
    }

    @Test
    @DisplayName("a module that uses another module's service interface and common passes every rule")
    void allowedUsagePassesEveryRule() {
        assertThatCode(() -> {
            ModuleBoundaryRules.noModuleReachesIntoAnotherModulesInternals(GOOD).check(good);
            ModuleBoundaryRules.commonDependsOnNoModule(GOOD).check(good);
            ModuleBoundaryRules.noCyclesBetweenModules(GOOD).check(good);
            ModuleBoundaryRules.nothingDependsOn("archfixture.legacy").check(good);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("moduleOf takes the first package segment under the base package")
    void moduleOfFindsTheFirstSegment() {
        assertThat(ModuleBoundaryRules.moduleOf("com.wealthmax", "com.wealthmax.careers.entity")).isEqualTo("careers");
        assertThat(ModuleBoundaryRules.moduleOf("com.wealthmax", "com.wealthmax.careers")).isEqualTo("careers");
        assertThat(ModuleBoundaryRules.moduleOf("com.wealthmax", "com.wealthmax")).isNull();
        assertThat(ModuleBoundaryRules.moduleOf("com.wealthmax", "org.springframework.web")).isNull();
        assertThat(ModuleBoundaryRules.moduleOf("com.wealthmax", "com.wealthmaxx.careers")).isNull();
    }
}
