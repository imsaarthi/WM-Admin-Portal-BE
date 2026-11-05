package archfixture.bad.common.util;

import archfixture.bad.shop.service.ShopService;

/** Fixture for ModuleBoundaryRulesTest, deliberately wrong: the shared kernel depends on a module (rule 2). */
public class Helper {

    private ShopService shop;

    public ShopService shop() {
        return shop;
    }
}
