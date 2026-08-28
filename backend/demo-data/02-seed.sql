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
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_BRAND','BR_OUTDOOR','Brand Master','アウトドアブランドA',b'0',NOW(),NOW());
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_BRAND','BR_HOME','Brand Master','ホームブランドA',b'0',NOW(),NOW());
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_BRAND','BR_KITCHEN','Brand Master','キッチンブランドA',b'0',NOW(),NOW());

-- Supplier master (MS_SUPPL)
-- Names anonymized for the 9/17 customer demo (docs/demo-data-anonymization.md).
-- Codes (SUP_ALPHA/SUP_BETA/SUP_GAMMA) and all numeric/master data are unchanged.
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_SUPPL','SUP_ALPHA','Supplier Master','誠和商事株式会社',b'0',NOW(),NOW());
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_SUPPL','SUP_BETA','Supplier Master','中央トレーディング株式会社',b'0',NOW(),NOW());
INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime) VALUES ('MS_SUPPL','SUP_GAMMA','Supplier Master','さくら物産株式会社',b'0',NOW(),NOW());

-- MS_ITEM
-- DESCRIPTION (item name) anonymized/refreshed for the 9/17 customer demo
-- (docs/demo-data-anonymization.md). item_cd/brand_cd/lead_time/item_status/
-- discon/sale_flg are all unchanged.
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('OD-TENT-001','BR_OUTDOOR','ファミリーテント L グリーン','30','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('OD-TENT-002','BR_OUTDOOR','ソロテント S ブルー','30','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('OD-CHAIR-001','BR_OUTDOOR','アウトドアチェア ブラック','45','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('OD-CHAIR-002','BR_OUTDOOR','アウトドアチェア ベージュ','45','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('OD-BAG-001','BR_OUTDOOR','トレッキングデイパック 20L','20','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('OD-BAG-002','BR_OUTDOOR','トレッキングデイパック 30L','20','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('OD-LAMP-001','BR_OUTDOOR','コンパクトLEDランタン(廃番)','15','DISCON',b'1',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('HM-MUG-001','BR_HOME','マグカップ ホワイト','10','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('HM-MUG-002','BR_HOME','マグカップ ブラック','10','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('HM-TOWEL-001','BR_HOME','モダンバスタオル グレー','25','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('HM-TOWEL-002','BR_HOME','モダンバスタオル ネイビー','25','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('HM-RUG-001','BR_HOME','リビングラグ 130x190','35','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('HM-RUG-002','BR_HOME','リビングラグ 200x250','35','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('KT-PAN-001','BR_KITCHEN','ステンレスフライパン 26cm','12','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('KT-PAN-002','BR_KITCHEN','ステンレスフライパン 20cm','12','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('KT-KNIFE-001','BR_KITCHEN','ステンレス三徳包丁','18','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('KT-KNIFE-002','BR_KITCHEN','ステンレスペティナイフ','18','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('KT-BOWL-001','BR_KITCHEN','ステンレスボウル 3点セット','22','NEW',b'0',b'1',b'0',NOW(),NOW());
INSERT INTO ms_item (item_cd, brand_cd, description, lead_time, item_status, discon, sale_flg, del_flg, create_datetime, update_datetime) VALUES ('KT-BOWL-002','BR_KITCHEN','ステンレスボウル 5点セット(発注停止)','22','ON_HOLD',b'0',b'1',b'0',NOW(),NOW());

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

-- MS_FORMULA: stretch goal, per-item Formula for 1 SKU (all others fall back to Legacy Default 4EU)
INSERT INTO ms_formula (id, formula_11, formula_12, formula_13, formula_14, del_flg, create_datetime, update_datetime) VALUES ('OD-TENT-001','IF([AT]<=5,(ROUNDUP([AT]*1-[AR]+[AT]*1/2,0)),IF([AT]<=10,(ROUNDUP([AT]*1-[AR]+[AT]*2/2,0)),ROUNDUP([AT]*1-[AR]+[AT]*3/2,0)))','IF(ROUNDDOWN([AZ]-([AT]*1/2),0)<0,0,ROUNDDOWN([AZ]-([AT]*1/2),0))','IF([AZ]-[BA]+(SUM([U]:[AO]))>[AT]*3/2,[AT]*3/2-(SUM([U]:[AO])),[AZ]-[BA])','IF([BB]<=1,0,ROUNDDOWN([BB],0))',b'0',NOW(),NOW());
