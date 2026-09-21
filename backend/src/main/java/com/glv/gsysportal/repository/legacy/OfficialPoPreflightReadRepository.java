package com.glv.gsysportal.repository.legacy;

import com.glv.gsysportal.repository.legacy.row.LegacySupplierBrandAssociationRow;
import com.glv.gsysportal.repository.legacy.row.LegacySupplierRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Legacy G-SYS Adapter - READ ONLY (same guarantee as {@link LegacyStockReadRepository},
 * Technical Design 4.1). Phase 7-C2A Preflight (docs/official-po-integration-detailed-design.md
 * 11章): the only checks that are actually executable before an Official PO
 * No. exists (Supplier/Brand/Item Master existence). PO-No.-dependent checks
 * (existing PO / Invoice / Stock-in) are deliberately NOT queried here - see
 * {@code OfficialPoPreflightService}'s informational issue explaining why.
 */
@Repository
public class OfficialPoPreflightReadRepository {

    private static final String SUPPLIER_BRAND_ASSOCIATION_QUERY_RESOURCE = "legacy/SupplierBrandAssociationQuery.sql";

    private final NamedParameterJdbcTemplate legacyJdbc;
    private final String supplierBrandAssociationSql;

    public OfficialPoPreflightReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
        this.supplierBrandAssociationSql = loadSql(SUPPLIER_BRAND_ASSOCIATION_QUERY_RESOURCE);
    }

    /** Mirrors PrOfficialPoImportBatch's Supplier Master check
     * (MS_COMM CATE_ID='MS_SUPPL'). */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public boolean supplierExists(String supplierCode) {
        Integer count = legacyJdbc.queryForObject(
                "SELECT COUNT(*) FROM ms_comm WHERE cate_id = 'MS_SUPPL' AND code_id = :code",
                new MapSqlParameterSource("code", supplierCode), Integer.class);
        return count != null && count > 0;
    }

    /** Mirrors PrOfficialPoImportBatch's Brand Master check
     * (MS_COMM CATE_ID='MS_BRAND'). Returns the Brand's current code_name, or
     * null if the Brand Code does not exist in Legacy. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public String findBrandName(String brandCode) {
        List<String> names = legacyJdbc.query(
                "SELECT code_name FROM ms_comm WHERE cate_id = 'MS_BRAND' AND code_id = :code",
                new MapSqlParameterSource("code", brandCode), (rs, rowNum) -> rs.getString("code_name"));
        return names.isEmpty() ? null : names.get(0);
    }

    /** Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
     * the Supplier List's Source of Truth. Same {@code ms_comm CATE_ID='MS_SUPPL'}
     * table {@link #supplierExists} already validates against - this simply
     * returns every registered row instead of checking one Code, so the
     * Portal never has to guess/fabricate a Supplier that isn't actually
     * registered in Legacy. No new table, no new Legacy query pattern. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacySupplierRow> findAllSuppliers() {
        return legacyJdbc.query(
                "SELECT code_id, code_name FROM ms_comm WHERE cate_id = 'MS_SUPPL' ORDER BY code_id",
                (rs, rowNum) -> new LegacySupplierRow(rs.getString("code_id"), rs.getString("code_name")));
    }

    /** Mirrors {@link #findBrandName} for the Supplier side - used by the
     * Supplier Settings Context header so it always shows the current
     * Legacy-registered name, not a name snapshotted on some earlier Order. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public String findSupplierName(String supplierCode) {
        List<String> names = legacyJdbc.query(
                "SELECT code_name FROM ms_comm WHERE cate_id = 'MS_SUPPL' AND code_id = :code",
                new MapSqlParameterSource("code", supplierCode), (rs, rowNum) -> rs.getString("code_name"));
        return names.isEmpty() ? null : names.get(0);
    }

    /** Mirrors PrOfficialPoImportBatch's Item Master check
     * (MS_ITEM, excluding deleted rows). Returns the subset of {@code skus}
     * that actually exist and are not deleted - callers diff against the
     * full requested set to find missing ones. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public Set<String> findExistingItemCodes(Collection<String> skus) {
        if (skus.isEmpty()) {
            return Set.of();
        }
        List<String> found = legacyJdbc.query(
                "SELECT item_cd FROM ms_item WHERE item_cd IN (:skus) AND (del_flg IS NULL OR del_flg = 0)",
                new MapSqlParameterSource("skus", skus), (rs, rowNum) -> rs.getString("item_cd"));
        return new HashSet<>(found);
    }

    /**
     * Stage 5H Systematic Performance Remediation (RC-H, docs/real-data-audit/
     * gops-stage5h-systematic-performance-remediation.md): the lightweight
     * (Supplier, Brand) association {@code SupplierMasterService
     * .brandsBySupplier()} needs - see SupplierBrandAssociationQuery.sql's
     * own header comment for why this replaces a full-catalog
     * {@code OrderCandidateService.findOrderCandidates(null,null,null)}
     * call. Brand display name is NOT resolved here - the caller uses the
     * existing bulk {@code LegacyStockReadRepository.findAllBrandNames()}
     * lookup, avoiding a second per-row {@code ms_comm} join.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacySupplierBrandAssociationRow> findSupplierBrandAssociations() {
        return legacyJdbc.query(supplierBrandAssociationSql, new MapSqlParameterSource(),
                (rs, rowNum) -> new LegacySupplierBrandAssociationRow(
                        rs.getString("supplier_cd"), rs.getString("brand_cd")));
    }

    private static String loadSql(String resourceName) {
        try {
            var resource = new ClassPathResource(resourceName);
            return new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            try (var is = new ClassPathResource(resourceName).getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException inner) {
                throw new UncheckedIOException("Failed to load " + resourceName, inner);
            }
        }
    }
}
