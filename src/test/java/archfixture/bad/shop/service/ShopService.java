package archfixture.bad.shop.service;

import archfixture.bad.billing.entity.Invoice;
import archfixture.bad.billing.service.BillingService;

/**
 * Fixture for ModuleBoundaryRulesTest, deliberately wrong: it uses billing's entity (rule 1), and it calls billing
 * while billing calls it (rule 3).
 */
public interface ShopService {

    BillingService billing();

    Invoice invoice();
}
