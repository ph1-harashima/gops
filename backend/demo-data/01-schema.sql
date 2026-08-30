-- Legacy-compatible Demo MySQL schema
--
-- IMPORTANT (found during Implementation Step 0/1, corrects Technical Design 6.2):
-- goo_dummy_dumpfile.sql's CREATE TABLE statements for ms_item/ms_stk are an OLDER,
-- INCOMPATIBLE schema version (e.g. ms_item has no BRAND_CD/LEAD_TIME column at all;
-- ms_stk has no STK_STANDARD/SOLD_QTY column and only PO_QTY_1..6/ARR_QTY_1..6 instead
-- of the production PO_QTY_1..20/ARR_QTY_1..10/SHIP_QTY_1..10). That DDL cannot support
-- calc4 (STK_STANDARD is required as AT) or monthlySales (SOLD_QTY) at all.
--
-- This schema is instead derived directly from the CURRENT production JPA entities,
-- which are the authoritative source actually queried by MsStkRepositoryImpl.java:
--   - jp.ne.glv.model.MsItem   (phasep-gulliver/gulliver/.../model/MsItem.java)
--   - jp.ne.glv.model.MsStk    (.../model/MsStk.java, @EmbeddedId MsStkPK: WH_CD + ITEM_CD)
--   - jp.ne.glv.model.MsComm   (.../model/MsComm.java, @EmbeddedId MsCommPK: CATE_ID + CODE_ID)
--   - jp.ne.glv.model.MsFormula(.../model/MsFormula.java)
--   - jp.ne.glv.model.TrPo / TrPoDtl (.../model/TrPo.java, TrPoDtl.java)
--
-- Only the columns actually needed for Order Candidate List + Recommended Qty (calc4)
-- are included (a deliberately reduced column set, per Technical Design 4.2's
-- "reduce columns, not business rules" principle applied to schema as well).
-- Column names, types and the composite keys match the real entities exactly.

CREATE DATABASE IF NOT EXISTS legacy_demo CHARACTER SET utf8mb4;
USE legacy_demo;

-- Mirrors MS_ITEM (subset). PK: ITEM_CD.
--
-- Phase 8-B (Price Change Foundation) addition: item_grp_cd/prc_sell_w_tax/
-- cost_this_month_avg/free_ship_flg/ship_fee, mirroring the REAL Legacy
-- jp.ne.glv.model.MsItem column names/types EXACTLY (@Column(name=...)
-- confirmed via Source in docs/legacy-price-change-reverse-engineering.md
-- 5章: ITEM_GRP_CD VARCHAR(30), PRC_SELL_W_TAX DECIMAL(10,2), COST_THIS_MONTH_AVG
-- DECIMAL(10,2), FREE_SHIP_FLG BIT(1), SHIP_FEE DECIMAL(10,2)). These are
-- exactly the fields jp.ne.glv.utilities.Formula.PROFIT_RATE_SELL() needs
-- (PRC_SELL derived from PRC_SELL_W_TAX, COST_THIS_MONTH_AVG, FREE_SHIP_FLG,
-- SHIP_FEE) - the ported calculator in this codebase
-- (com.glv.gsysportal.legacy.calc.MarginCalculator) takes exactly these 4
-- inputs, no more. LIST_PRC_W_TAX/PRC_SALE_W_TAX/PRC_GLV_B2B/PRC_WS_FOR_KOREA/
-- the EC-mall-specific price columns are deliberately NOT mirrored here -
-- Phase 8-B's Target Design (target-price-change-workflow.md 8章) scopes
-- Foundation to "Current Selling Price" only; adding unused columns would
-- violate this schema file's own "reduce columns, not business rules"
-- principle (line 19).
CREATE TABLE ms_item (
  item_cd              VARCHAR(30)  NOT NULL,
  brand_cd             VARCHAR(10)  NULL,
  description          VARCHAR(200) NULL,
  model                VARCHAR(400) NULL,
  model_no             VARCHAR(150) NULL,
  lead_time            VARCHAR(10)  NULL,
  item_status          VARCHAR(30)  NULL,
  discon               BIT(1)       NULL,
  sale_flg             BIT(1)       NULL,
  set_flg              BIT(1)       NULL,
  col_stk_standard     VARCHAR(10)  NULL,
  item_grp_cd          VARCHAR(30)  NULL,
  prc_sell_w_tax       DECIMAL(10,2) NULL,
  cost_this_month_avg  DECIMAL(10,2) NULL,
  free_ship_flg        BIT(1)       NULL,
  ship_fee             DECIMAL(10,2) NULL,
  del_flg              BIT(1)       NULL,
  create_datetime      DATETIME     NULL,
  update_datetime      DATETIME     NULL,
  PRIMARY KEY (item_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mirrors MS_STK (subset). PK: (WH_CD, ITEM_CD) - matches MsStkPK exactly.
-- One 'XX' row per item holds STK_STANDARD/SOLD_QTY/PO_QTY_*/ARR_QTY_*/SHIP_QTY_*
-- (the aggregate/primary row - see Const.MS_STK_WH_CD_PRI="XX" in Legacy,
-- confirmed via SlTempostarImportBatch.java in Phase 0.5 audit).
-- Physical per-warehouse rows (WH1..WH12, excluding WH6/WH10/WH11 per Legacy's
-- documented exclusion rule) hold only STK_QTY.
CREATE TABLE ms_stk (
  wh_cd              VARCHAR(10) NOT NULL,
  item_cd            VARCHAR(30) NOT NULL,
  pri_wh_flag        BIT(1)      NULL,
  stk_qty            INT         NULL,
  stk_standard       INT         NULL,
  sold_qty           INT         NULL,
  po_qty_1 INT NULL, po_qty_2 INT NULL, po_qty_3 INT NULL, po_qty_4 INT NULL, po_qty_5 INT NULL,
  po_qty_6 INT NULL, po_qty_7 INT NULL, po_qty_8 INT NULL, po_qty_9 INT NULL, po_qty_10 INT NULL,
  po_qty_11 INT NULL, po_qty_12 INT NULL, po_qty_13 INT NULL, po_qty_14 INT NULL, po_qty_15 INT NULL,
  po_qty_16 INT NULL, po_qty_17 INT NULL, po_qty_18 INT NULL, po_qty_19 INT NULL, po_qty_20 INT NULL,
  arr_qty_1 INT NULL, arr_qty_2 INT NULL, arr_qty_3 INT NULL, arr_qty_4 INT NULL, arr_qty_5 INT NULL,
  arr_qty_6 INT NULL, arr_qty_7 INT NULL, arr_qty_8 INT NULL, arr_qty_9 INT NULL, arr_qty_10 INT NULL,
  ship_qty_1 INT NULL, ship_qty_2 INT NULL, ship_qty_3 INT NULL, ship_qty_4 INT NULL, ship_qty_5 INT NULL,
  ship_qty_6 INT NULL, ship_qty_7 INT NULL, ship_qty_8 INT NULL, ship_qty_9 INT NULL, ship_qty_10 INT NULL,
  del_flg            BIT(1)      NULL,
  create_datetime    DATETIME    NULL,
  update_datetime    DATETIME    NULL,
  PRIMARY KEY (wh_cd, item_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mirrors MS_COMM (generic EAV code master). PK: (CATE_ID, CODE_ID).
-- Uses the CORRECT CATE_ID values that the production code actually queries
-- (MS_BRAND / MS_SUPPL, from Const.java), NOT the mismatched 'BRAND'/'SUPPLIER'
-- values found in goo_dummy_dumpfile.sql (a discrepancy documented in Phase 0.5
-- audit Q章; deliberately not reproduced here).
CREATE TABLE ms_comm (
  cate_id            VARCHAR(10)  NOT NULL,
  code_id            VARCHAR(10)  NOT NULL,
  cate_name          VARCHAR(100) NULL,
  code_name          VARCHAR(100) NULL,
  val_1              VARCHAR(100) NULL,
  val_2              VARCHAR(100) NULL,
  del_flg            BIT(1)       NULL,
  create_datetime    DATETIME     NULL,
  update_datetime    DATETIME     NULL,
  PRIMARY KEY (cate_id, code_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mirrors MS_FORMULA. PK: ID (= ITEM_CD, LEFT JOIN'd 1:1 per item).
-- Deliberately left mostly EMPTY in seed data (see 02-seed.sql): items without a
-- row here fall back to Legacy's hardcoded Default 4EU formula, which is itself a
-- real, confirmed Legacy code path (Phase 0 audit C章) - not a fabricated shortcut.
-- A small number of rows ARE seeded to also demonstrate the per-item Formula path.
CREATE TABLE ms_formula (
  id                 VARCHAR(50)  NOT NULL,
  formula_11         VARCHAR(300) NULL,
  formula_12         VARCHAR(300) NULL,
  formula_13         VARCHAR(300) NULL,
  formula_14         VARCHAR(300) NULL,
  del_flg            BIT(1)       NULL,
  create_datetime    DATETIME     NULL,
  update_datetime    DATETIME     NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mirrors TR_PO (subset). PK: PO_NO.
-- Included so that Supplier can be derived from PO history (Legacy Adapter join),
-- since MS_ITEM has NO supplier_cd column at all (confirmed Phase 0 audit F章) -
-- Supplier is only ever a transactional attribute of a PO in real Legacy.
-- po_type added in Phase 7-C7A (Fulfillment / Follow-up Foundation) - real
-- Legacy TR_PO.PO_TYPE distinguishes an ordinary PO from a Credit PO
-- (PO_TYPE=CREDIT, Const.TR_PO_PO_TYPE_CREDIT in phasep-gulliver), which
-- FulfillmentReadRepository's Outstanding calculation must be able to net
-- against the original PO it corrects (see that Repository's Javadoc and
-- docs/fulfillment-follow-up-foundation.md 2章 for the full Source-derived
-- mechanism).
-- deliv_week/deliv_date added in Phase 7-C6 (Excel / Legacy Concurrency
-- Control Foundation) - real Legacy TR_PO.DELIV_WEEK/DELIV_DATE (both plain
-- VARCHAR, confirmed via jp.ne.glv.model.TrPo - DELIV_DATE is free-text, not
-- a real DATE column) are part of LegacyPoConcurrencyReadRepository's
-- Canonical Snapshot candidate field list (7-C6 2章's "delivery").
CREATE TABLE tr_po (
  po_no              VARCHAR(30)  NOT NULL,
  status             VARCHAR(10)  NULL,
  po_type            VARCHAR(10)  NULL,
  supplier_cd        VARCHAR(10)  NULL,
  brand_cd           VARCHAR(10)  NULL,
  ccy                VARCHAR(10)  NULL,
  ordr_date          DATE         NULL,
  deliv_week         VARCHAR(5)   NULL,
  deliv_date         VARCHAR(50)  NULL,
  amt_ttl            DECIMAL(10,2) NULL,
  del_flg            BIT(1)       NULL,
  create_datetime    DATETIME     NULL,
  update_datetime    DATETIME     NULL,
  PRIMARY KEY (po_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mirrors TR_PO_DTL (subset). PK: (PO_NO, LINE_NO).
CREATE TABLE tr_po_dtl (
  po_no              VARCHAR(30)   NOT NULL,
  line_no            INT           NOT NULL,
  item_cd            VARCHAR(30)   NULL,
  qty_po             INT           NULL,
  prc_unit           DECIMAL(13,5) NULL,
  amt_line           DECIMAL(13,5) NULL,
  del_flg            BIT(1)        NULL,
  create_datetime    DATETIME      NULL,
  update_datetime    DATETIME      NULL,
  PRIMARY KEY (po_no, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mirrors TR_INV (subset). PK: (SUPPLIER_CD, INV_NO). Added in Phase 7-C7A
-- (0章's Source audit of jp.ne.glv.model.TrInv - PrOfficialPoImportBatch /
-- PrStkInReportImportBatch are the authoritative writers in real Legacy;
-- this Demo instance only ever reads these rows, never writes them, matching
-- the existing tr_po/tr_po_dtl "reduce columns, not business rules" pattern
-- above). tran_type mirrors Const.TR_INV_TRAN_TYPE_* ('30'=INVOICE,
-- '70'=CREDIT) - only the 2 values this Phase's Fulfillment calculation
-- actually distinguishes are meaningfully exercised by seed data.
CREATE TABLE tr_inv (
  supplier_cd        VARCHAR(10)   NOT NULL,
  inv_no             VARCHAR(100)  NOT NULL,
  status             VARCHAR(10)   NULL,
  tran_type          VARCHAR(10)   NULL,
  brand_cd           VARCHAR(10)   NULL,
  qty_ttl            INT           NULL,
  amt_ttl            DECIMAL(10,2) NULL,
  del_flg            BIT(1)        NULL,
  create_datetime    DATETIME      NULL,
  update_datetime    DATETIME      NULL,
  PRIMARY KEY (supplier_cd, inv_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mirrors TR_INV_DTL (subset). PK: (SUPPLIER_CD, INV_NO, LINE_NO). Added in
-- Phase 7-C7A. qty = invoiced/transit qty; qty_stk_in = actual
-- physically-received qty (NULL until Stock-in Report Import sets it in real
-- Legacy - this Demo instance seeds it directly). po_no links back to the PO
-- this Invoice line belongs to (a Credit line's po_no is the linked Credit
-- PO's own po_no, e.g. "{originalPoNo}-{arrCode}" - never the original PO's
-- po_no directly - see FulfillmentReadRepository's Javadoc).
CREATE TABLE tr_inv_dtl (
  supplier_cd        VARCHAR(10)  NOT NULL,
  inv_no             VARCHAR(100) NOT NULL,
  line_no            INT          NOT NULL,
  item_cd            VARCHAR(30)  NULL,
  qty_ordr           INT          NULL,
  qty                INT          NULL,
  qty_stk_in         INT          NULL,
  po_no              VARCHAR(100) NULL,
  del_flg            BIT(1)       NULL,
  create_datetime    DATETIME     NULL,
  update_datetime    DATETIME     NULL,
  PRIMARY KEY (supplier_cd, inv_no, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mirrors TR_ARR (subset). PK: (SUPPLIER_CD, PO_NO, INV_NO) - matches the
-- real Legacy TrArrPK exactly (jp.ne.glv.model.pk.TrArrPK, confirmed via
-- Source in docs/legacy-warehouse-logistics-logizero-reverse-engineering.md
-- 7章/13章). Added in Phase 8-G (Arrival / Warehouse Stock Visibility
-- Foundation) - real Legacy TR_ARR is a much larger Entity (customs/tariff/
-- demurrage/drayage/remittance columns), reduced here to only the columns
-- this Phase's Arrival Visibility screen actually displays ("reduce
-- columns, not business rules" principle, line 19 above).
--
-- IMPORTANT (Phase 8-G 11章's most important constraint, preserved
-- structurally): this table deliberately has NO wh_cd column - the real
-- Legacy TrArr entity has none either (confirmed Source fact, RE Document
-- 13章's "Arrival ⇄ Warehouse Stock: 不可能" finding). This is not an
-- oversight; adding one would make an accidental Arrival<->Warehouse Stock
-- join possible where none exists in real Legacy. Do not add it.
--
-- qty is a single header-level quantity (not per-SKU) - matches real
-- TR_ARR.QTY exactly (RE Document 7章's traceability table). Per-SKU
-- Ordered/Invoiced/Stock-In quantities for the Detail screen are derived by
-- joining tr_po_dtl/tr_inv_dtl instead (ArrivalReadRepository), reusing the
-- same Original+Credit netting FulfillmentReadRepository already
-- established for qty_stk_in (Phase 7-C7A).
CREATE TABLE tr_arr (
  supplier_cd        VARCHAR(10)   NOT NULL,
  po_no              VARCHAR(30)   NOT NULL,
  inv_no             VARCHAR(100)  NOT NULL,
  brand_cd           VARCHAR(10)   NULL,
  bl_no              VARCHAR(30)   NULL,
  vessel_no          VARCHAR(50)   NULL,
  qty                INT           NULL,
  etd                DATE          NULL,
  eta                DATE          NULL,
  eta_wh             DATE          NULL,
  stk_in_date        DATE          NULL,
  wh_rep_status      VARCHAR(100)  NULL,
  wh_rep_result      VARCHAR(100)  NULL,
  del_flg            BIT(1)        NULL,
  create_datetime    DATETIME      NULL,
  update_datetime    DATETIME      NULL,
  PRIMARY KEY (supplier_cd, po_no, inv_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
