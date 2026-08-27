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
CREATE TABLE ms_item (
  item_cd            VARCHAR(30)  NOT NULL,
  brand_cd           VARCHAR(10)  NULL,
  description        VARCHAR(200) NULL,
  model              VARCHAR(400) NULL,
  model_no           VARCHAR(150) NULL,
  lead_time          VARCHAR(10)  NULL,
  item_status        VARCHAR(30)  NULL,
  discon             BIT(1)       NULL,
  sale_flg           BIT(1)       NULL,
  set_flg            BIT(1)       NULL,
  col_stk_standard   VARCHAR(10)  NULL,
  del_flg            BIT(1)       NULL,
  create_datetime    DATETIME     NULL,
  update_datetime    DATETIME     NULL,
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
CREATE TABLE tr_po (
  po_no              VARCHAR(30)  NOT NULL,
  status             VARCHAR(10)  NULL,
  supplier_cd        VARCHAR(10)  NULL,
  brand_cd           VARCHAR(10)  NULL,
  ccy                VARCHAR(10)  NULL,
  ordr_date          DATE         NULL,
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
