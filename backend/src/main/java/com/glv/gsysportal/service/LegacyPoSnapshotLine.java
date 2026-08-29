package com.glv.gsysportal.service;

import java.math.BigDecimal;

/** One Canonical Snapshot line, keyed by SKU (item_cd) - see
 * {@link LegacyPoSnapshot}'s Javadoc and
 * {@code LegacyPoConcurrencyReadRepository}'s Javadoc on why SKU, not
 * TR_PO_DTL.line_no, is the stable Line identity this Phase relies on. */
public record LegacyPoSnapshotLine(String skuCode, Integer orderedQty, BigDecimal unitPrice) {
}
