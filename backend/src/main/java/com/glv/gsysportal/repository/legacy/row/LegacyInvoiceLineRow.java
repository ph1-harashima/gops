package com.glv.gsysportal.repository.legacy.row;

/** Mirrors Legacy TR_INV_DTL (ITEM_CD/QTY/QTY_STK_IN/PO_NO). {@code poNo} lets
 * the caller distinguish an Original-PO row (poNo equals the queried Official
 * PO No. exactly) from a linked Credit-PO row (poNo matches the
 * {@code "{officialPoNo}-{arrCode}"} pattern - see
 * docs/fulfillment-follow-up-foundation.md 2章 for how this Phase derived and
 * verified that exact naming convention from
 * {@code BusinessLogicUtil.creditPoNo} in phasep-gulliver). */
public record LegacyInvoiceLineRow(String itemCd, Integer qty, Integer qtyStkIn, String poNo) {
}
