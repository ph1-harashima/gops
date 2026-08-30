-- Legacy-compatible Demo MySQL seed data (Order Candidate + Recommended Qty vertical slice)
-- Generated for Implementation Step 0/1. See docs/G-SYS_Online-Ordering_Prototype_Technical_Design.md 6章.
-- All Recommended Qty values are NOT hardcoded here: they are produced by the ported calc4
-- logic (com.glv.gsysportal.legacy.calc) reading STK_STANDARD/PO_QTY/ARR_QTY/SHIP_QTY/MS_FORMULA
-- from this seed data at query time, exactly as Legacy would.

-- IMPORTANT (found during Implementation Step 0/1): without this, the docker
-- entrypoint's non-interactive `mysql < 02-seed.sql` import negotiates a non-UTF-8
-- client charset by default, silently corrupting every multibyte (Japanese)
-- literal in this file into double-encoded garbage on INSERT - confirmed via
-- HEX(description) showing corrupted bytes already at rest in the table, not
-- merely on SELECT. SET NAMES forces the client side of THIS import session to
-- utf8mb4 so the literal bytes below are stored correctly.
SET NAMES utf8mb4;

USE legacy_demo;

-- Brand master (MS_BRAND)
-- Names anonymized for the 9/17 customer demo (docs/demo-data-anonymization.md).
-- Codes (BR_OUTDOOR/BR_HOME/BR_KITCHEN) and all numeric/master data are unchanged.
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_BRAND','BR_OUTDOOR','Brand Master','FIELDNEST',b'0',NOW(),NOW());
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_BRAND','BR_HOME','Brand Master','LIVORA',b'0',NOW(),NOW());
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_BRAND','BR_KITCHEN','Brand Master','KITCHENNE',b'0',NOW(),NOW());

-- Supplier master (MS_SUPPL)
-- Names anonymized for the 9/17 customer demo (docs/demo-data-anonymization.md).
-- Codes (SUP_ALPHA/SUP_BETA/SUP_GAMMA) and all numeric/master data are unchanged.
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_SUPPL','SUP_ALPHA','Supplier Master','東和ライフサプライ株式会社',b'0',NOW(),NOW());
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_SUPPL','SUP_BETA','Supplier Master','蒼空プロダクト株式会社',b'0',NOW(),NOW());
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_SUPPL','SUP_GAMMA','Supplier Master','東都リビングパートナーズ株式会社',b'0',NOW(),NOW());

-- MS_ITEM
-- DESCRIPTION (item name) anonymized/refreshed for the 9/17 customer demo
-- (docs/demo-data-anonymization.md). item_cd/brand_cd/lead_time/item_status/
-- discon/sale_flg are all unchanged.
--
-- Phase 8-B (Price Change Foundation): added item_grp_cd/prc_sell_w_tax/
-- cost_this_month_avg/free_ship_flg/ship_fee. cost_this_month_avg values
-- reuse each item's existing PO-OUTDOOR/HOME/KITCHEN prc_unit below (already
-- representative of what Legacy would treat as a recent per-unit landed
-- cost); prc_sell_w_tax is a plausible ~1.7x retail markup, rounded to a
-- clean ¥10 (no Business Rule attached - purely Demo realism, per Section 3
-- "Sourceに存在しない値は作らない" this does not fabricate a NEW column,
-- only plausible values for a real Legacy column). Items sharing a physical
-- product line are grouped under an IG-* item_grp_cd (2 SKUs/group) to
-- exercise Item Group-based Bulk Selection (target-price-change-workflow.md
-- 9章) - group codes themselves are Demo-only labels, not read from any
-- Source (Phase 7-J confirmed Item Group<->Brand naming is not derivable
-- from Source, RE 8章). KT-BOWL-002 is deliberately seeded with
-- prc_sell_w_tax BELOW cost_this_month_avg (an intentional negative-margin
-- fixture) to exercise the Margin Preview's display-only behavior (Phase
-- 8-B forbids any warning/blocking Business Rule, Section 5) without
-- needing a live price edit to reach that state in a screenshot/E2E run.
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('OD-TENT-001','BR_OUTDOOR','ファミリーテント L グリーン','30','NEW',b'0',b'1','IG-OD-TENT',1930.00,1137.00,b'1',300.00,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('OD-TENT-002','BR_OUTDOOR','ソロテント S ブルー','30','NEW',b'0',b'1','IG-OD-TENT',2160.00,1274.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('OD-CHAIR-001','BR_OUTDOOR','アウトドアチェア ブラック','45','NEW',b'0',b'1','IG-OD-CHAIR',2400.00,1411.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('OD-CHAIR-002','BR_OUTDOOR','アウトドアチェア ベージュ','45','NEW',b'0',b'1','IG-OD-CHAIR',2630.00,1548.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('OD-BAG-001','BR_OUTDOOR','トレッキングデイパック 20L','20','NEW',b'0',b'1','IG-OD-BAG',2860.00,1685.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('OD-BAG-002','BR_OUTDOOR','トレッキングデイパック 30L','20','NEW',b'0',b'1','IG-OD-BAG',3100.00,1822.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('OD-LAMP-001','BR_OUTDOOR','コンパクトLEDランタン(廃番)','15','DISCON',b'1',b'1',NULL,3330.00,1959.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('HM-MUG-001','BR_HOME','マグカップ ホワイト','10','NEW',b'0',b'1','IG-HM-MUG',3560.00,2096.00,b'1',250.00,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('HM-MUG-002','BR_HOME','マグカップ ブラック','10','NEW',b'0',b'1','IG-HM-MUG',3800.00,2233.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('HM-TOWEL-001','BR_HOME','モダンバスタオル グレー','25','NEW',b'0',b'1','IG-HM-TOWEL',4030.00,2370.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('HM-TOWEL-002','BR_HOME','モダンバスタオル ネイビー','25','NEW',b'0',b'1','IG-HM-TOWEL',4260.00,2507.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('HM-RUG-001','BR_HOME','リビングラグ 130x190','35','NEW',b'0',b'1','IG-HM-RUG',4500.00,2644.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('HM-RUG-002','BR_HOME','リビングラグ 200x250','35','NEW',b'0',b'1','IG-HM-RUG',4730.00,2781.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('KT-PAN-001','BR_KITCHEN','ステンレスフライパン 26cm','12','NEW',b'0',b'1','IG-KT-PAN',4960.00,2918.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('KT-PAN-002','BR_KITCHEN','ステンレスフライパン 20cm','12','NEW',b'0',b'1','IG-KT-PAN',5190.00,3055.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('KT-KNIFE-001','BR_KITCHEN','ステンレス三徳包丁','18','NEW',b'0',b'1','IG-KT-KNIFE',5430.00,3192.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('KT-KNIFE-002','BR_KITCHEN','ステンレスペティナイフ','18','NEW',b'0',b'1','IG-KT-KNIFE',5660.00,3329.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('KT-BOWL-001','BR_KITCHEN','ステンレスボウル 3点セット','22','NEW',b'0',b'1','IG-KT-BOWL',5890.00,3466.00,NULL,NULL,b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, item_grp_cd, prc_sell_w_tax, cost_this_month_avg, free_ship_flg, ship_fee, del_flg, create_datetime, update_datetime) VALUES ('KT-BOWL-002','BR_KITCHEN','ステンレスボウル 5点セット(発注停止)','22','ON_HOLD',b'0',b'1','IG-KT-BOWL',3500.00,3603.00,NULL,NULL,b'0',NOW(),NOW());

-- MS_STK: 'XX' aggregate row (STK_STANDARD/SOLD_QTY/PO_QTY_1/ARR_QTY_1) + WH1 physical row
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','OD-TENT-001',b'1',0,5,42,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','OD-TENT-001',b'0',2,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','OD-TENT-002',b'1',0,20,0,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','OD-TENT-002',b'0',0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','OD-CHAIR-001',b'1',0,10,12,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','OD-CHAIR-001',b'0',50,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','OD-CHAIR-002',b'1',0,10,30,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','OD-CHAIR-002',b'0',5,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','OD-BAG-001',b'1',0,11,8,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','OD-BAG-001',b'0',5,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','OD-BAG-002',b'1',0,20,65,15,10,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','OD-BAG-002',b'0',0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','OD-LAMP-001',b'1',0,0,0,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','OD-LAMP-001',b'0',0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','HM-MUG-001',b'1',0,5,18,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','HM-MUG-001',b'0',2,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','HM-MUG-002',b'1',0,20,3,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','HM-MUG-002',b'0',0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','HM-TOWEL-001',b'1',0,10,40,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','HM-TOWEL-001',b'0',50,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','HM-TOWEL-002',b'1',0,10,22,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','HM-TOWEL-002',b'0',5,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','HM-RUG-001',b'1',0,11,9,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','HM-RUG-001',b'0',5,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','HM-RUG-002',b'1',0,20,55,15,10,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','HM-RUG-002',b'0',0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','KT-PAN-001',b'1',0,5,60,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','KT-PAN-001',b'0',2,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','KT-PAN-002',b'1',0,20,1,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','KT-PAN-002',b'0',0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','KT-KNIFE-001',b'1',0,10,33,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','KT-KNIFE-001',b'0',50,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','KT-KNIFE-002',b'1',0,10,27,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','KT-KNIFE-002',b'0',5,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','KT-BOWL-001',b'1',0,11,14,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','KT-BOWL-001',b'0',5,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, stk_standard, sold_qty, po_qty_1, arr_qty_1, del_flg, create_datetime, update_datetime) VALUES ('XX','KT-BOWL-002',b'1',0,0,0,0,0,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('01','KT-BOWL-002',b'0',0,b'0',NOW(),NOW());

-- TR_PO / TR_PO_DTL: PO history so Supplier + Unit Price can be derived (MS_ITEM has no supplier_cd)
-- PO No. values anonymized for the 9/17 customer demo (docs/demo-data-anonymization.md):
-- the original PO No. scheme embedded the old Supplier name (ALPHA/BETA/GAMMA) directly in
-- the string, which is visible on screen in SKU Detail's Legacy PO History table, so it is
-- replaced with a generic PO-{Brand}-{seq} scheme. supplier_cd/brand_cd/ccy/ordr_date/
-- amt_ttl/qty_po/prc_unit/amt_line/status are all unchanged.
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-01','OFFICIAL','SUP_ALPHA','BR_OUTDOOR','JPY','2026-07-02',3411.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-01',1,'OD-TENT-001',3,1137.00000,3411.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-02','OFFICIAL','SUP_ALPHA','BR_OUTDOOR','JPY','2026-07-03',3822.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-02',1,'OD-TENT-002',3,1274.00000,3822.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-03','OFFICIAL','SUP_ALPHA','BR_OUTDOOR','JPY','2026-07-04',4233.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-03',1,'OD-CHAIR-001',3,1411.00000,4233.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-04','OFFICIAL','SUP_BETA','BR_OUTDOOR','JPY','2026-07-05',4644.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-04',1,'OD-CHAIR-002',3,1548.00000,4644.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-05','OFFICIAL','SUP_BETA','BR_OUTDOOR','JPY','2026-07-06',5055.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-05',1,'OD-BAG-001',3,1685.00000,5055.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-06','OFFICIAL','SUP_BETA','BR_OUTDOOR','JPY','2026-07-07',5466.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-06',1,'OD-BAG-002',3,1822.00000,5466.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-07','OFFICIAL','SUP_ALPHA','BR_OUTDOOR','JPY','2026-07-08',5877.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-07',1,'OD-LAMP-001',3,1959.00000,5877.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-08','OFFICIAL','SUP_GAMMA','BR_HOME','JPY','2026-07-09',6288.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-08',1,'HM-MUG-001',3,2096.00000,6288.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-09','OFFICIAL','SUP_GAMMA','BR_HOME','JPY','2026-07-10',6699.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-09',1,'HM-MUG-002',3,2233.00000,6699.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-10','OFFICIAL','SUP_GAMMA','BR_HOME','JPY','2026-07-11',7110.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-10',1,'HM-TOWEL-001',3,2370.00000,7110.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-11','OFFICIAL','SUP_GAMMA','BR_HOME','JPY','2026-07-12',7521.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-11',1,'HM-TOWEL-002',3,2507.00000,7521.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-12','OFFICIAL','SUP_BETA','BR_HOME','JPY','2026-07-13',7932.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-12',1,'HM-RUG-001',3,2644.00000,7932.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-13','OFFICIAL','SUP_BETA','BR_HOME','JPY','2026-07-14',8343.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-HOME-13',1,'HM-RUG-002',3,2781.00000,8343.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-14','OFFICIAL','SUP_GAMMA','BR_KITCHEN','JPY','2026-07-15',8754.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-14',1,'KT-PAN-001',3,2918.00000,8754.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-15','OFFICIAL','SUP_GAMMA','BR_KITCHEN','JPY','2026-07-16',9165.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-15',1,'KT-PAN-002',3,3055.00000,9165.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-16','OFFICIAL','SUP_ALPHA','BR_KITCHEN','JPY','2026-07-17',9576.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-16',1,'KT-KNIFE-001',3,3192.00000,9576.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-17','OFFICIAL','SUP_ALPHA','BR_KITCHEN','JPY','2026-07-18',9987.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-17',1,'KT-KNIFE-002',3,3329.00000,9987.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-18','OFFICIAL','SUP_ALPHA','BR_KITCHEN','JPY','2026-07-19',10398.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-18',1,'KT-BOWL-001',3,3466.00000,10398.00000,b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-19','OFFICIAL','SUP_ALPHA','BR_KITCHEN','JPY','2026-07-20',10809.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-KITCHEN-19',1,'KT-BOWL-002',3,3603.00000,10809.00000,b'0',NOW(),NOW());

-- Phase 7-C6: Excel / Legacy Concurrency Control Foundation Test Fixtures
-- (docs/excel-legacy-concurrency-control.md 5章). Dedicated PO-CONC-01/02/03
-- rows, deliberately separate from the PO-OUTDOOR/PO-HOME/PO-KITCHEN fixtures
-- above, so this Phase's Backend/E2E tests (which directly mutate these rows
-- via `docker exec gsys-legacy-demo-mysql mysql ...` after a Baseline is
-- captured - 7-C6 14章's explicitly sanctioned Test-only Legacy Demo DB
-- mutation, never through the Portal application's own READ ONLY connection)
-- never interact with the Fulfillment/Follow-up fixture set. deliv_week/
-- deliv_date are populated here (unlike every PO-OUTDOOR/HOME/KITCHEN row
-- above) specifically to exercise those 2 Canonical Snapshot fields.
--
-- Dedicated CC-ITEM-00x item codes (NOT any existing OD-/HM-/KT- code) -
-- found the hard way: RecommendedQtyReadQuery.sql derives an item's
-- unitPrice/supplier/currency from whichever TR_PO has the LATEST ordr_date
-- for that item_cd (ROW_NUMBER() ... ORDER BY p.ordr_date DESC). Reusing an
-- existing item_cd here with a later ordr_date than its PO-OUTDOOR/HOME/
-- KITCHEN fixture would silently steal that resolution for every OTHER test
-- in this codebase relying on that item's known price (this broke
-- PoPreviewServiceIntegrationTest for real during this Phase's
-- implementation before being caught and fixed this way).
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('CC-ITEM-001','BR_OUTDOOR','Concurrency Control Test Item 1','30','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('CC-ITEM-002','BR_OUTDOOR','Concurrency Control Test Item 2','30','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('CC-ITEM-003','BR_OUTDOOR','Concurrency Control Test Item 3','30','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('CC-ITEM-004','BR_OUTDOOR','Concurrency Control Test Item 4','30','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('CC-ITEM-005','BR_OUTDOOR','Concurrency Control Test Item 5','30','NEW',b'0',b'1',b'0',NOW(),NOW());

INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, deliv_week, deliv_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-CONC-01','OFFICIAL','SUP_ALPHA','BR_OUTDOOR','JPY','2026-08-01','35','2026-08-24',6000.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-CONC-01',1,'CC-ITEM-001',5,1000.00000,5000.00000,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-CONC-01',2,'CC-ITEM-002',2,500.00000,1000.00000,b'0',NOW(),NOW());
-- Line-Added scenario fixture: starts with 1 line only.
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, deliv_week, deliv_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-CONC-02','OFFICIAL','SUP_BETA','BR_OUTDOOR','JPY','2026-08-02','35','2026-08-25',3200.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-CONC-02',1,'CC-ITEM-003',4,800.00000,3200.00000,b'0',NOW(),NOW());
-- Line-Removed scenario fixture: starts with 2 lines (the 2nd is soft-deleted
-- by the E2E scenario via del_flg=1, matching Legacy's own Delete & Recreate
-- soft-delete convention rather than a hard DELETE).
INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd, ccy, ordr_date, deliv_week, deliv_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-CONC-03','OFFICIAL','SUP_BETA','BR_OUTDOOR','JPY','2026-08-03','35','2026-08-26',2400.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-CONC-03',1,'CC-ITEM-004',3,600.00000,1800.00000,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-CONC-03',2,'CC-ITEM-005',2,300.00000,600.00000,b'0',NOW(),NOW());

-- MS_FORMULA: stretch goal, per-item Formula for 1 SKU (all others fall back to Legacy Default 4EU)
INSERT INTO ms_formula (id, formula_11, formula_12, formula_13, formula_14, del_flg, create_datetime, update_datetime) VALUES ('OD-TENT-001','IF([AT]<=5,(ROUNDUP([AT]*1-[AR]+[AT]*1/2,0)),IF([AT]<=10,(ROUNDUP([AT]*1-[AR]+[AT]*2/2,0)),ROUNDUP([AT]*1-[AR]+[AT]*3/2,0)))','IF(ROUNDDOWN([AZ]-([AT]*1/2),0)<0,0,ROUNDDOWN([AZ]-([AT]*1/2),0))','IF([AZ]-[BA]+(SUM([U]:[AO]))>[AT]*3/2,[AT]*3/2-(SUM([U]:[AO])),[AZ]-[BA])','IF([BB]<=1,0,ROUNDDOWN([BB],0))',b'0',NOW(),NOW());

-- Phase 7-C7A: Fulfillment READ ONLY Test Fixtures (docs/fulfillment-follow-up-foundation.md
-- 5章 - test-only, safe to reset, never Production data). Reuses the existing
-- PO-OUTDOOR-01/02/05 TR_PO/TR_PO_DTL rows above (Section 5's explicit
-- permission: "既知のOfficial PO/Test Fixtureを使ってREAD ONLY計算Testを
-- 行ってよい"). No TR_ARR rows are added - FulfillmentReadRepository never
-- reads TR_ARR (see its own Javadoc on why: MS_STK/TR_ARR answer a broader,
-- ITEM-level question that this Phase's PO-level Outstanding calculation
-- does not need).

-- PO-OUTDOOR-01 (OD-TENT-001, qty_po=3): fully invoiced and fully stocked in -> FULFILLED.
INSERT INTO tr_inv (supplier_cd, inv_no, status, tran_type, brand_cd, qty_ttl, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('SUP_ALPHA','INV-OUTDOOR-01','STOCK_IN','30','BR_OUTDOOR',3,3411.00,b'0',NOW(),NOW());
INSERT INTO tr_inv_dtl (supplier_cd, inv_no, line_no, item_cd, qty_ordr, qty, qty_stk_in, po_no, del_flg, create_datetime, update_datetime) VALUES ('SUP_ALPHA','INV-OUTDOOR-01',1,'OD-TENT-001',3,3,3,'PO-OUTDOOR-01',b'0',NOW(),NOW());

-- PO-OUTDOOR-02 (OD-TENT-002, qty_po=3): invoiced 3, only 2 physically
-- confirmed stocked in so far (no discrepancy/Credit PO raised yet - this
-- represents "still RECEIVING") -> PARTIAL.
INSERT INTO tr_inv (supplier_cd, inv_no, status, tran_type, brand_cd, qty_ttl, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('SUP_ALPHA','INV-OUTDOOR-02','RECEIVING','30','BR_OUTDOOR',3,3822.00,b'0',NOW(),NOW());
INSERT INTO tr_inv_dtl (supplier_cd, inv_no, line_no, item_cd, qty_ordr, qty, qty_stk_in, po_no, del_flg, create_datetime, update_datetime) VALUES ('SUP_ALPHA','INV-OUTDOOR-02',1,'OD-TENT-002',3,3,2,'PO-OUTDOOR-02',b'0',NOW(),NOW());

-- PO-OUTDOOR-05 (OD-BAG-001, qty_po=3): a discrepancy occurred - invoiced 3,
-- but only 2 actually arrived. Mirrors PrStkInReportImportBatch's exact
-- reconciliation mechanism (docs/fulfillment-follow-up-foundation.md 2章):
-- the ORIGINAL TR_INV_DTL.qty_stk_in is capped at its own invoiced qty (3,
-- never the true received amount), and a linked Credit PO/Invoice
-- ("{originalPoNo}-{arrCode}", PO_TYPE=CREDIT) carries the exact signed
-- difference (2 - 3 = -1) in ITS OWN qty_stk_in. True stock-in = 3 + (-1) = 2.
-- This fixture exists specifically to prove FulfillmentReadRepository nets
-- the two together correctly (without Credit netting this would misread as
-- fully FULFILLED at qty 3, instead of the true PARTIAL at qty 2).
INSERT INTO tr_inv (supplier_cd, inv_no, status, tran_type, brand_cd, qty_ttl, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('SUP_BETA','INV-OUTDOOR-05','RECEIVING','30','BR_OUTDOOR',3,5055.00,b'0',NOW(),NOW());
INSERT INTO tr_inv_dtl (supplier_cd, inv_no, line_no, item_cd, qty_ordr, qty, qty_stk_in, po_no, del_flg, create_datetime, update_datetime) VALUES ('SUP_BETA','INV-OUTDOOR-05',1,'OD-BAG-001',3,3,3,'PO-OUTDOOR-05',b'0',NOW(),NOW());
INSERT INTO tr_po (po_no, status, po_type, supplier_cd, brand_cd, ccy, ordr_date, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-05-ARR001','OFFICIAL','CREDIT','SUP_BETA','BR_OUTDOOR','JPY','2026-07-06',-1685.00,b'0',NOW(),NOW());
INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) VALUES ('PO-OUTDOOR-05-ARR001',1,'OD-BAG-001',-1,1685.00000,-1685.00000,b'0',NOW(),NOW());
INSERT INTO tr_inv (supplier_cd, inv_no, status, tran_type, brand_cd, qty_ttl, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('SUP_BETA','INV-OUTDOOR-05-ARR001','RECEIVING','70','BR_OUTDOOR',-1,-1685.00,b'0',NOW(),NOW());
INSERT INTO tr_inv_dtl (supplier_cd, inv_no, line_no, item_cd, qty_ordr, qty, qty_stk_in, po_no, del_flg, create_datetime, update_datetime) VALUES ('SUP_BETA','INV-OUTDOOR-05-ARR001',1,'OD-BAG-001',-1,-1,-1,'PO-OUTDOOR-05-ARR001',b'0',NOW(),NOW());

-- PO-OUTDOOR-03/04/06/07 are deliberately left WITHOUT any TR_INV/TR_INV_DTL
-- row - the "nothing invoiced yet" OPEN test case needs no new fixture at
-- all (absence of an invoice line is itself the OPEN state).

-- ============================================================================
-- Phase 8-G: Arrival / Warehouse Stock Visibility Foundation Test Fixtures
-- (docs/legacy-warehouse-logistics-logizero-reverse-engineering.md, this
-- Phase's implementation report). Test-only, safe to reset, never Production
-- data - same convention as the Phase 7-C7A block above.
-- ============================================================================

-- Additional MS_STK physical-warehouse rows (WH_CD other than '01') so
-- Warehouse Stock List's WH_CD Filter has more than one code to exercise.
-- Codes reuse the real Legacy WH_CD values confirmed in Phase 8-F (4/5/7 -
-- part of the "sellable" whitelist InvLogizeroStkImportBatch sums,
-- RE Document 8章) - NOT a fabricated numbering scheme. No name is attached
-- to any of these codes anywhere in this seed data (no MS_COMM 'MS_WH'
-- category rows are inserted) because Phase 8-F could not confirm a
-- Source name for any WH_CD beyond '01' (RE Document 8章/17章's explicit
-- instruction not to invent one) - the Frontend must display the bare code.
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('04','OD-TENT-001',b'0',7,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('05','OD-TENT-001',b'0',1,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('04','OD-CHAIR-001',b'0',12,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('07','HM-MUG-001',b'0',9,b'0',NOW(),NOW());
INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime) VALUES ('04','KT-PAN-001',b'0',4,b'0',NOW(),NOW());

-- Brand-diverse Invoice fixtures beyond the existing all-BR_OUTDOOR set
-- above, so Arrival List's Brand/Supplier Filter has BR_HOME/BR_KITCHEN
-- rows to find too. Reuses existing PO-HOME-08/PO-KITCHEN-14 (already
-- seeded above, no invoice yet).
-- PO-HOME-08 (HM-MUG-001, qty_po=3): invoiced, still in transit - no
-- Stock-In Report yet (qty_stk_in left NULL, matching real Legacy's own
-- "not yet set" state - RE Document 6章/schema comment on tr_inv_dtl above).
INSERT INTO tr_inv (supplier_cd, inv_no, status, tran_type, brand_cd, qty_ttl, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('SUP_GAMMA','INV-HOME-08','TRANSIT','30','BR_HOME',3,6288.00,b'0',NOW(),NOW());
INSERT INTO tr_inv_dtl (supplier_cd, inv_no, line_no, item_cd, qty_ordr, qty, qty_stk_in, po_no, del_flg, create_datetime, update_datetime) VALUES ('SUP_GAMMA','INV-HOME-08',1,'HM-MUG-001',3,3,NULL,'PO-HOME-08',b'0',NOW(),NOW());

-- PO-KITCHEN-14 (KT-PAN-001, qty_po=3): fully invoiced and fully stocked in.
INSERT INTO tr_inv (supplier_cd, inv_no, status, tran_type, brand_cd, qty_ttl, amt_ttl, del_flg, create_datetime, update_datetime) VALUES ('SUP_GAMMA','INV-KITCHEN-14','STOCK_IN','30','BR_KITCHEN',3,8754.00,b'0',NOW(),NOW());
INSERT INTO tr_inv_dtl (supplier_cd, inv_no, line_no, item_cd, qty_ordr, qty, qty_stk_in, po_no, del_flg, create_datetime, update_datetime) VALUES ('SUP_GAMMA','INV-KITCHEN-14',1,'KT-PAN-001',3,3,3,'PO-KITCHEN-14',b'0',NOW(),NOW());

-- TR_ARR: one header row per (supplier_cd, po_no, inv_no) above. qty is the
-- Source's own single header-level quantity (schema comment above) - NOT
-- recomputed from tr_inv_dtl here, so it can legitimately differ slightly in
-- shape from the per-SKU sums the Backend derives separately (both are real
-- Source Facts, simply at different granularity; Phase 8-G forbids comparing
-- them against each other as if they were required to match, RE 8-G 7章/11章).
--
-- PO-OUTDOOR-01 / INV-OUTDOOR-01: fully arrived and stocked in.
INSERT INTO tr_arr (supplier_cd, po_no, inv_no, brand_cd, bl_no, vessel_no, qty, etd, eta, eta_wh, stk_in_date, wh_rep_status, wh_rep_result, del_flg, create_datetime, update_datetime) VALUES ('SUP_ALPHA','PO-OUTDOOR-01','INV-OUTDOOR-01','BR_OUTDOOR','BL-OUTDOOR-001','PACIFIC STAR',3,'2026-07-10','2026-07-24','2026-07-26','2026-07-27','RECEIVED','MATCHED',b'0',NOW(),NOW());
-- PO-OUTDOOR-02 / INV-OUTDOOR-02: in transit / partially confirmed (matches
-- the existing RECEIVING Invoice status fixture above).
INSERT INTO tr_arr (supplier_cd, po_no, inv_no, brand_cd, bl_no, vessel_no, qty, etd, eta, eta_wh, stk_in_date, wh_rep_status, wh_rep_result, del_flg, create_datetime, update_datetime) VALUES ('SUP_ALPHA','PO-OUTDOOR-02','INV-OUTDOOR-02','BR_OUTDOOR','BL-OUTDOOR-002','PACIFIC STAR',3,'2026-07-11','2026-07-25',NULL,NULL,'IN_PROGRESS',NULL,b'0',NOW(),NOW());
-- PO-OUTDOOR-05 / INV-OUTDOOR-05: the Credit-netting discrepancy fixture
-- (existing tr_inv/tr_po Credit pair above) - Arrival header itself carries
-- no discrepancy information (real TR_ARR has no such field either).
INSERT INTO tr_arr (supplier_cd, po_no, inv_no, brand_cd, bl_no, vessel_no, qty, etd, eta, eta_wh, stk_in_date, wh_rep_status, wh_rep_result, del_flg, create_datetime, update_datetime) VALUES ('SUP_BETA','PO-OUTDOOR-05','INV-OUTDOOR-05','BR_OUTDOOR','BL-OUTDOOR-005','SOUTHERN CROSS',3,'2026-07-12','2026-07-26','2026-07-28','2026-07-29','RECEIVED','DISCREPANCY_NOTED',b'0',NOW(),NOW());
-- PO-HOME-08 / INV-HOME-08: still in transit, ETA in the future, no ETA_WH/
-- Stock-In yet (all such columns left NULL - a real, not-yet-reached state).
INSERT INTO tr_arr (supplier_cd, po_no, inv_no, brand_cd, bl_no, vessel_no, qty, etd, eta, eta_wh, stk_in_date, wh_rep_status, wh_rep_result, del_flg, create_datetime, update_datetime) VALUES ('SUP_GAMMA','PO-HOME-08','INV-HOME-08','BR_HOME','BL-HOME-008','NORTHERN LIGHT',3,'2026-08-20','2026-09-05',NULL,NULL,NULL,NULL,b'0',NOW(),NOW());
-- PO-KITCHEN-14 / INV-KITCHEN-14: fully arrived and stocked in.
INSERT INTO tr_arr (supplier_cd, po_no, inv_no, brand_cd, bl_no, vessel_no, qty, etd, eta, eta_wh, stk_in_date, wh_rep_status, wh_rep_result, del_flg, create_datetime, update_datetime) VALUES ('SUP_GAMMA','PO-KITCHEN-14','INV-KITCHEN-14','BR_KITCHEN','BL-KITCHEN-014','NORTHERN LIGHT',3,'2026-07-15','2026-07-29','2026-07-30','2026-07-31','RECEIVED','MATCHED',b'0',NOW(),NOW());
-- PO-OUTDOOR-03: an OFFICIAL PO that reached neither Invoice nor Arrival yet
-- (deliberately no tr_inv/tr_arr row). Order Detail's "入荷情報を見る" link
-- (Phase 8-G 12章) filters the Arrival List by this PO Number - the List
-- must show a real, honest empty result for this PO, not a fabricated row.
