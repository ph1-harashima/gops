package com.glv.gsysportal.repository.legacy.row;

import java.math.BigDecimal;

/** Phase 7-C6: raw TR_PO_DTL row for the Canonical Snapshot - includes
 * {@code unitPrice}, which {@link LegacyPoLineRow} (7-C7A's Fulfillment-only
 * subset) deliberately omits (Fulfillment never needed price). */
public record LegacyPoConcurrencyLineRow(String skuCode, Integer orderedQty, BigDecimal unitPrice) {
}
