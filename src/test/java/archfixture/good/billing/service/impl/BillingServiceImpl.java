package archfixture.good.billing.service.impl;

import archfixture.good.common.util.Strings;
import archfixture.good.shop.service.ShopService;

/**
 * Fixture for ModuleBoundaryRulesTest: the allowed way for one module to use another. It calls the other module's
 * service interface and the shared kernel, and nothing else of theirs.
 */
public class BillingServiceImpl {

    private final ShopService shop;

    public BillingServiceImpl(ShopService shop) {
        this.shop = shop;
    }

    public String bill() {
        return Strings.tidy(shop.describe());
    }
}
