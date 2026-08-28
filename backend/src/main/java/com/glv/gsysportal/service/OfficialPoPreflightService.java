package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.dto.response.OfficialPoPreflightIssue;
import com.glv.gsysportal.dto.response.OfficialPoPreflightResult;
import com.glv.gsysportal.repository.legacy.OfficialPoPreflightReadRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Phase 7-C2A 11章/12章: Legacy READ ONLY Preflight for Official PO
 * Integration. Only checks that are actually executable BEFORE an Official
 * PO No. exists (7章's Gate - this Phase never assigns one): Supplier/Brand/
 * Item Master existence. Existing-PO / Invoice / Stock-in / Revision
 * re-import checks (docs/official-po-integration-detailed-design.md 11.2章)
 * need a PO No. to query TR_PO/TR_INV_DTL by, so they cannot run yet - this
 * is surfaced as a single INFO-severity issue rather than silently skipped,
 * so nobody mistakes an early PASS for "fully cleared for re-import".
 */
@Service
public class OfficialPoPreflightService {

    static final String CODE_SUPPLIER_NOT_FOUND = "SUPPLIER_NOT_FOUND";
    static final String CODE_BRAND_NOT_FOUND = "BRAND_NOT_FOUND";
    static final String CODE_ITEM_NOT_FOUND = "ITEM_NOT_FOUND";
    static final String CODE_OFFICIAL_PO_NO_NOT_ASSIGNED = "OFFICIAL_PO_NO_NOT_ASSIGNED";

    private final OfficialPoPreflightReadRepository preflightReadRepository;

    public OfficialPoPreflightService(OfficialPoPreflightReadRepository preflightReadRepository) {
        this.preflightReadRepository = preflightReadRepository;
    }

    public OfficialPoPreflightResult run(PortalOrder order) {
        List<OfficialPoPreflightIssue> issues = new ArrayList<>();

        if (!preflightReadRepository.supplierExists(order.getSupplierCode())) {
            issues.add(new OfficialPoPreflightIssue(CODE_SUPPLIER_NOT_FOUND, OfficialPoPreflightIssue.SEVERITY_BLOCKED,
                    "Supplier code '" + order.getSupplierCode() + "' was not found in the Legacy Supplier Master (MS_COMM/MS_SUPPL).",
                    null));
        }

        String brandName = preflightReadRepository.findBrandName(order.getBrandCode());
        if (brandName == null) {
            issues.add(new OfficialPoPreflightIssue(CODE_BRAND_NOT_FOUND, OfficialPoPreflightIssue.SEVERITY_BLOCKED,
                    "Brand code '" + order.getBrandCode() + "' was not found in the Legacy Brand Master (MS_COMM/MS_BRAND).",
                    null));
        }

        List<String> skus = order.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .map(PortalOrderDetail::getSku)
                .toList();
        Set<String> existingSkus = preflightReadRepository.findExistingItemCodes(skus);
        for (String sku : skus) {
            if (!existingSkus.contains(sku)) {
                issues.add(new OfficialPoPreflightIssue(CODE_ITEM_NOT_FOUND, OfficialPoPreflightIssue.SEVERITY_BLOCKED,
                        "Item code '" + sku + "' was not found (or is deleted) in the Legacy Item Master (MS_ITEM).",
                        sku));
            }
        }

        // Always present this Phase - officialPoNo is never assigned yet
        // (7-C2A 7章's Gate), so Existing-PO/Invoice/Stock-in checks
        // (docs/official-po-integration-detailed-design.md 11.2章) cannot run.
        issues.add(new OfficialPoPreflightIssue(CODE_OFFICIAL_PO_NO_NOT_ASSIGNED, OfficialPoPreflightIssue.SEVERITY_INFO,
                "No Official PO No. is assigned yet, so Existing-PO/Invoice/Stock-in re-import checks are deferred to a later Phase.",
                null));

        return new OfficialPoPreflightResult(aggregateResult(issues), issues);
    }

    private static String aggregateResult(List<OfficialPoPreflightIssue> issues) {
        boolean blocked = issues.stream().anyMatch(i -> OfficialPoPreflightIssue.SEVERITY_BLOCKED.equals(i.severity()));
        if (blocked) {
            return OfficialPoPreflightResult.RESULT_BLOCKED;
        }
        boolean warning = issues.stream().anyMatch(i -> OfficialPoPreflightIssue.SEVERITY_WARNING.equals(i.severity()));
        if (warning) {
            return OfficialPoPreflightResult.RESULT_WARNING;
        }
        return OfficialPoPreflightResult.RESULT_PASS;
    }
}
