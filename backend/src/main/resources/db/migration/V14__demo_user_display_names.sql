-- Prototype PostgreSQL only. Phase 7-H (User表示 audit): the Target design
-- (docs/target-production-procurement-workflow.md, Technical Design 7章)
-- assumes Email+Password Login with per-User display, and portal_user.
-- display_name has always existed (V4) specifically for this - it was just
-- seeded (V5/V8) with generic role-shaped placeholders ("購買担当（デモ）"
-- etc.) instead of a person's name, which reads oddly on the Header/Audit
-- Timeline (both already prefer display_name over username - App.tsx/
-- OrderHistoryService - no code change needed here, only the seed data).
--
-- Fictional Demo names only - not real employees, no PII. purchase01/admin01
-- are this Demo's actual login accounts (README/demo docs); sales_admin/
-- sys_admin are pre-7-C1 role holdovers kept only so old
-- audit_event.performed_by values already using MySQL Username strings stay
-- resolvable (V8 comment) - given fictional names too for the same reason
-- (any historical Audit row referencing them should also read as a person,
-- not a role placeholder).
UPDATE portal_user SET display_name = '佐藤 太郎' WHERE username = 'purchase01';
UPDATE portal_user SET display_name = '鈴木 花子' WHERE username = 'admin01';
UPDATE portal_user SET display_name = '高橋 健一' WHERE username = 'sales_admin';
UPDATE portal_user SET display_name = '田中 美咲' WHERE username = 'sys_admin';
