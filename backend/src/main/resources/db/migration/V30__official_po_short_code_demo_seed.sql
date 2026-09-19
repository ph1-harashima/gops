-- BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): baseline
-- Demo/Test fixture data for official_po_short_code (V29), so the existing
-- Local/Demo/Test Supplier/Brand fixtures (backend/demo-data/02-seed.sql's
-- MS_SUPPL/MS_BRAND rows: SUP_ALPHA/SUP_BETA/SUP_GAMMA,
-- BR_OUTDOOR/BR_HOME/BR_KITCHEN) can auto-number an Official PO No.
-- end-to-end without every test/demo walkthrough having to register a Short
-- Code by hand first.
--
-- This is Demo/Test-only fixture data, in the exact same spirit as
-- V5__seed_portal_users.sql's demo accounts - NOT G-OPS "inventing" a real
-- Gulliver-decided abbreviation (BR-08's explicit prohibition). In a real
-- Production-candidate environment, an ADMIN would register Gulliver's own
-- decided values via the Official PO Short Code Master screen instead of
-- relying on this seed.
INSERT INTO official_po_short_code (code_type, business_code, short_code, is_active, created_by, created_at, updated_by, updated_at) VALUES
    ('SUPPLIER', 'SUP_ALPHA', 'ALP', true, 'demo-seed', now(), 'demo-seed', now()),
    ('SUPPLIER', 'SUP_BETA',  'BET', true, 'demo-seed', now(), 'demo-seed', now()),
    ('SUPPLIER', 'SUP_GAMMA', 'GAM', true, 'demo-seed', now(), 'demo-seed', now()),
    ('BRAND',    'BR_OUTDOOR','OUT', true, 'demo-seed', now(), 'demo-seed', now()),
    ('BRAND',    'BR_HOME',   'HOM', true, 'demo-seed', now(), 'demo-seed', now()),
    ('BRAND',    'BR_KITCHEN','KIT', true, 'demo-seed', now(), 'demo-seed', now());
