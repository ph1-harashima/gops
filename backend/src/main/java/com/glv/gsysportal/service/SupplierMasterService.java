package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.ManufacturerChannelResponse;
import com.glv.gsysportal.dto.response.OfficialPoShortCodeResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.SupplierContactResponse;
import com.glv.gsysportal.dto.response.SupplierMasterDetailResponse;
import com.glv.gsysportal.dto.response.SupplierMasterDetailResponse.BrandRef;
import com.glv.gsysportal.dto.response.SupplierMasterSummaryResponse;
import com.glv.gsysportal.dto.response.SupplierRegionClassificationResponse;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import com.glv.gsysportal.repository.legacy.OfficialPoPreflightReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacySupplierBrandAssociationRow;
import com.glv.gsysportal.repository.legacy.row.LegacySupplierRow;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;

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

    // Stage 5H Systematic Performance Remediation (RC-J): same
    // default/max page size convention as OrderCandidateService/
    // WarehouseStockService - one shared pagination contract, not a third
    // independently-chosen one.
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

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

    /**
     * Stage 5H Systematic Performance Remediation (RC-G, docs/real-data-audit/
     * gops-stage5h-systematic-performance-remediation.md): no method-level
     * {@code @Transactional} - same reasoning as {@code DashboardService
     * .getDashboard()}'s own Stage 5E RC-F fix. Each Legacy/Portal
     * repository call below already opens and commits its own short
     * transaction (Spring Data JPA repositories are {@code @Transactional
     * (readOnly = true)} per call by default when no broader transaction is
     * active; every Legacy repository method here carries its own
     * {@code @Transactional(transactionManager = "legacyTransactionManager")}).
     * A method-level annotation here would hold one Portal connection
     * (pool max=5, unchanged) reserved for this method's entire body,
     * including {@link #brandsBySupplier}'s Legacy-side work - Stage 5G's
     * audit found this was still happening here even after RC-F fixed the
     * identical pattern for Dashboard.
     *
     * <p>RC-J (same Stage): now Backend-paginated (602 Suppliers today,
     * same {@link PageResponse} envelope/default-20/max-100 convention as
     * {@code OrderCandidateService}/{@code WarehouseStockService}). Every
     * row's own computation (region/contact/channel/PO-code aggregation,
     * Brand count) is unchanged - only the final slice returned differs;
     * Business Meaning of every field is identical to before this Stage.
     */
    public PageResponse<SupplierMasterSummaryResponse> listSuppliers(Integer page, Integer size) {
        List<LegacySupplierRow> suppliers = legacyMasterReadRepository.findAllSuppliers();
        Map<String, Map<String, String>> brandsBySupplier = brandsBySupplier();
        List<SupplierContactResponse> contacts = supplierContactService.list();
        List<ManufacturerChannelResponse> channels = manufacturerChannelService.list();
        List<SupplierRegionClassificationResponse> regions = supplierRegionClassificationService.list();
        List<OfficialPoShortCodeResponse> shortCodes = officialPoShortCodeService.list();

        List<SupplierMasterSummaryResponse> all = suppliers.stream()
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

        int clampedSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int clampedPage = page == null || page < 0 ? 0 : page;
        int fromIndex = Math.min(clampedPage * clampedSize, all.size());
        int toIndex = Math.min(fromIndex + clampedSize, all.size());
        return PageResponse.of(all.subList(fromIndex, toIndex), clampedPage, clampedSize, all.size());
    }

    /** Stage 5H Systematic Performance Remediation (RC-G): see
     * {@link #listSuppliers()}'s own Javadoc - same reasoning applies here. */
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

    /**
     * supplierCode -> (brandCode -> brandName), union of Legacy Order
     * Candidate-eligible Items' most-recent-PO Supplier and Portal Orders'
     * own recorded Supplier/Brand - same union {@code
     * DashboardService.buildBrandRows} already uses conceptually, just
     * sourced lean rather than via a full Order Candidate catalog.
     *
     * <p>Stage 5H Systematic Performance Remediation (RC-H, docs/real-data-audit/
     * gops-stage5h-systematic-performance-remediation.md): previously called
     * {@code orderCandidateService.findOrderCandidates(null, null, null)} -
     * the full, unpaginated, calc4-evaluating 116,842-SKU catalog method
     * Dashboard itself stopped calling in Stage 5E (RC-A) - just to read 2
     * of its ~20 fields (supplierCode/brandCode/brandName). Stage 5G's
     * audit measured this at 54.4s (List) / 51.1s (Detail) against the
     * Production Snapshot. Replaced with a lightweight Legacy query
     * returning only (supplierCode, brandCode) pairs (see
     * SupplierBrandAssociationQuery.sql's own header comment) plus the
     * existing bulk brand-name lookup ({@code
     * OrderCandidateService.findBrandNames()}, already used by RC-B) -
     * no calc4, no FormulaParser, no current_stock/Recommended Qty
     * evaluation anywhere in this path. The Portal Orders union below is
     * unchanged from before this Stage (out of RC-H's scope - Stage 5G
     * flagged {@code portalOrderRepository.findAll()} as its own, lower-
     * priority finding, not part of Stage 5H).
     */
    private Map<String, Map<String, String>> brandsBySupplier() {
        Map<String, String> brandNames = orderCandidateService.findBrandNames();
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (LegacySupplierBrandAssociationRow a : legacyMasterReadRepository.findSupplierBrandAssociations()) {
            result.computeIfAbsent(a.supplierCode(), k -> new LinkedHashMap<>())
                    .putIfAbsent(a.brandCode(), brandNames.getOrDefault(a.brandCode(), a.brandCode()));
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
