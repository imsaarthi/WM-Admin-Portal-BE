package archfixture.good.common.util;

/** Fixture for ModuleBoundaryRulesTest: shared kernel code that depends on no module. */
public final class Strings {

    private Strings() {
    }

    public static String tidy(String value) {
        return value.trim();
    }
}
