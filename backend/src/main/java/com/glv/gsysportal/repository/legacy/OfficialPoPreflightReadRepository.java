package com.glv.gsysportal.repository.legacy;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    private final NamedParameterJdbcTemplate legacyJdbc;

    public OfficialPoPreflightReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
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
}
