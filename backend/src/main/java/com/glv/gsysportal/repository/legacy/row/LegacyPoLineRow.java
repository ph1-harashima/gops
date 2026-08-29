package com.glv.gsysportal.repository.legacy.row;

/** Mirrors Legacy TR_PO_DTL (ITEM_CD/QTY_PO) for one Official PO's own lines
 * (never a linked Credit PO's lines - those are read separately, see
 * FulfillmentReadRepository's Javadoc). */
public record LegacyPoLineRow(String itemCd, Integer qtyPo) {
}
