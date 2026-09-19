package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.ManufacturerChannelResponse;
import com.glv.gsysportal.dto.response.OfficialPoShortCodeResponse;
import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.dto.response.SupplierContactResponse;
import com.glv.gsysportal.dto.response.SupplierMasterDetailResponse;
import com.glv.gsysportal.dto.response.SupplierMasterDetailResponse.BrandRef;
import com.glv.gsysportal.dto.response.SupplierMasterSummaryResponse;
import com.glv.gsysportal.dto.response.SupplierRegionClassificationResponse;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import com.glv.gsysportal.repository.legacy.OfficialPoPreflightReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacySupplierRow;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
 * "Master Maintenance -> Supplier一覧 -> Supplier選択 -> Supplier Settings"
 * per the Cross-Screen IA Audit's 4章 recommendation (Commit c5f005c).
 *
 * Deliberately a pure READ aggregation over already-existing data sources -
 * no new Portal Master table, no duplicated Business Logic/Validation:
 * <ul>
 *   <li>Supplier Code/Name: Legacy {@code ms_comm CATE_ID='MS_SUPPL'} (READ
 *       ONLY, the same table {@code OfficialPoPreflightReadRepository}
 *       already validates every Supplier Code against elsewhere in this
 *       codebase) - never guessed/fabricated.</li>
 *   <li>Brand association per Supplier: the same Order Candidate/Order data
 *       {@code DashboardService}'s own Brand breakdown already derives from -
 *       no separate Brand Master.</li>
 *   <li>Contact/Channel/Region/PO Code "Configured" status: the existing
 *       {@link SupplierContactService}/{@link ManufacturerChannelService}/
 *       {@link SupplierRegionClassificationService}/{@link OfficialPoShortCodeService}
 *       {@code list()} methods, reused as-is and simply grouped by Supplier
 *       Code here - their own CRUD/validation logic is untouched and not
 *       re-implemented.</li>
 * </ul>
 */
@Service
public class SupplierMasterService {

    private final OfficialPoPreflightReadRepository legacyMasterReadRepository;
    private final OrderCandidateService orderCandidateService;
    private final PortalOrderRepository portalOrderRepository;
    private final SupplierContactService supplierContactService;
    private final ManufacturerChannelService manufacturerChannelService;
    private final SupplierRegionClassificationService supplierRegionClassificationService;
    private final OfficialPoShortCodeService officialPoShortCodeService;

    public SupplierMasterService(OfficialPoPreflightReadRepository legacyMasterReadRepository,
                                  OrderCandidateService orderCandidateService,
                                  PortalOrderRepository portalOrderRepository,
                                  SupplierContactService supplierContactService,
                                  ManufacturerChannelService manufacturerChannelService,
                                  SupplierRegionClassificationService supplierRegionClassificationService,
                                  OfficialPoShortCodeService officialPoShortCodeService) {
        this.legacyMasterReadRepository = legacyMasterReadRepository;
        this.orderCandidateService = orderCandidateService;
        this.portalOrderRepository = portalOrderRepository;
        this.supplierContactService = supplierContactService;
        this.manufacturerChannelService = manufacturerChannelService;
        this.supplierRegionClassificationService = supplierRegionClassificationService;
        this.officialPoShortCodeService = officialPoShortCodeService;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<SupplierMasterSummaryResponse> listSuppliers() {
        List<LegacySupplierRow> suppliers = legacyMasterReadRepository.findAllSuppliers();
        Map<String, Map<String, String>> brandsBySupplier = brandsBySupplier();
        List<SupplierContactResponse> contacts = supplierContactService.list();
        List<ManufacturerChannelResponse> channels = manufacturerChannelService.list();
        List<SupplierRegionClassificationResponse> regions = supplierRegionClassificationService.list();
        List<OfficialPoShortCodeResponse> shortCodes = officialPoShortCodeService.list();

        return suppliers.stream()
                .map(s -> new SupplierMasterSummaryResponse(
                        s.code(),
                        s.name(),
                        aggregateValue(regions.stream()
                                .filter(r -> r.active() && r.supplierCode().equals(s.code()))
                                .map(SupplierRegionClassificationResponse::regionClassification)),
                        brandsBySupplier.getOrDefault(s.code(), Map.of()).size(),
                        contacts.stream().anyMatch(c -> c.active() && c.supplierCode().equals(s.code())),
                        aggregateValue(channels.stream()
                                .filter(c -> c.active() && c.supplierCode().equals(s.code()))
                                .map(ManufacturerChannelResponse::channel)),
                        shortCodes.stream()
                                .filter(c -> c.active() && "SUPPLIER".equals(c.codeType()) && c.businessCode().equals(s.code()))
                                .map(OfficialPoShortCodeResponse::shortCode)
                                .findFirst().orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public SupplierMasterDetailResponse getSupplier(String supplierCode) {
        String name = legacyMasterReadRepository.findSupplierName(supplierCode);
        if (name == null) {
            throw new SupplierCodeNotFoundException(supplierCode);
        }

        Map<String, String> brands = brandsBySupplier().getOrDefault(supplierCode, Map.of());
        List<BrandRef> brandRefs = brands.entrySet().stream()
                .map(e -> new BrandRef(e.getKey(), e.getValue()))
                .toList();

        List<SupplierContactResponse> activeContacts = supplierContactService.list().stream()
                .filter(c -> c.active() && c.supplierCode().equals(supplierCode))
                .toList();
        String region = aggregateValue(supplierRegionClassificationService.list().stream()
                .filter(r -> r.active() && r.supplierCode().equals(supplierCode))
                .map(SupplierRegionClassificationResponse::regionClassification));
        String channel = aggregateValue(manufacturerChannelService.list().stream()
                .filter(c -> c.active() && c.supplierCode().equals(supplierCode))
                .map(ManufacturerChannelResponse::channel));
        String shortCode = officialPoShortCodeService.list().stream()
                .filter(c -> c.active() && "SUPPLIER".equals(c.codeType()) && c.businessCode().equals(supplierCode))
                .map(OfficialPoShortCodeResponse::shortCode)
                .findFirst().orElse(null);

        return new SupplierMasterDetailResponse(
                supplierCode, name, brandRefs, region,
                !activeContacts.isEmpty(), activeContacts.size(), channel, shortCode);
    }

    /** supplierCode -> (brandCode -> brandName), same derivation
     * {@code DashboardService.buildBrandRows} already uses (Order Candidates
     * union Orders) - deliberately not a new Legacy query. */
    private Map<String, Map<String, String>> brandsBySupplier() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (OrderCandidateResponse c : orderCandidateService.findOrderCandidates(null, null, null)) {
            if (c.supplierCode() != null && c.brandCode() != null) {
                result.computeIfAbsent(c.supplierCode(), k -> new LinkedHashMap<>())
                        .putIfAbsent(c.brandCode(), c.brandName() != null ? c.brandName() : c.brandCode());
            }
        }
        for (PortalOrder o : portalOrderRepository.findAll()) {
            if (o.getSupplierCode() != null && o.getBrandCode() != null) {
                result.computeIfAbsent(o.getSupplierCode(), k -> new LinkedHashMap<>())
                        .putIfAbsent(o.getBrandCode(), o.getBrandNameSnapshot() != null ? o.getBrandNameSnapshot() : o.getBrandCode());
            }
        }
        return result;
    }

    /** null if empty, the single value if all agree, "MIXED" otherwise -
     * e.g. two Brands under one Supplier classified DOMESTIC and OVERSEAS
     * respectively, or one on EMAIL and another on EDI. */
    private static String aggregateValue(java.util.stream.Stream<String> values) {
        Set<String> distinct = values.collect(java.util.stream.Collectors.toSet());
        if (distinct.isEmpty()) {
            return null;
        }
        return distinct.size() == 1 ? distinct.iterator().next() : "MIXED";
    }
}
