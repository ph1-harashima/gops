-- Prototype PostgreSQL only. Gulliver UI最終仕上げ #2: portal_user.email was
-- seeded (V8) as <username>@portal-demo.invalid (admin01@..., purchase01@...
-- etc.), which reads fine as data but looks like a raw Login ID once shown
-- on a proposal-quality Screenshot (Mail Preview From/To/CC, audit display).
-- V14 already gave these same four accounts fictional display_name values
-- for the identical reason - this migration does the same for email,
-- keeping the same fictional-person naming (no real employees, no PII) and
-- the same .invalid reserved TLD (RFC 2606) so nothing here could ever
-- resolve to a real mailbox.
--
-- Username (Login ID) is NOT changed - Authentication/Authorization keys off
-- portal_user.username, never email, this Phase (V8 comment, 7-C1 report).
-- Production is unaffected: this table only exists in the Prototype
-- Postgres instances Local/Demo/Test point at (Safety Gate never starts a
-- "production" profile in this environment), and these four rows are this
-- Demo's own fixed accounts (V14 comment), not derived from Legacy G-SYS.
UPDATE portal_user SET email = 'suzuki.hanako@portal-demo.invalid'     WHERE username = 'admin01';
UPDATE portal_user SET email = 'sato.taro@portal-demo.invalid'        WHERE username = 'purchase01';
UPDATE portal_user SET email = 'takahashi.kenichi@portal-demo.invalid' WHERE username = 'sales_admin';
UPDATE portal_user SET email = 'tanaka.misaki@portal-demo.invalid'    WHERE username = 'sys_admin';
