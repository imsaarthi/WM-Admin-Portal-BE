package archfixture.bad.billing.service;

import archfixture.bad.shop.service.ShopService;

/** Fixture for ModuleBoundaryRulesTest: billing calls shop, and shop calls billing back (a cycle). */
public interface BillingService {

    ShopService shop();
}
