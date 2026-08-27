-- Legacy READ ONLY guarantee (Technical Design 4.1, Requirements MD absolute condition).
--
-- The application connects to the Legacy Demo MySQL using this SELECT-only user,
-- never the root/admin account. INSERT/UPDATE/DELETE/DDL are not granted.
-- This is layer 1 of the three-layer READ ONLY guarantee (see LegacyDataSourceConfig
-- and LegacyReadOnlyIntegrationTest for layers 2 and 3).

CREATE USER IF NOT EXISTS 'gsys_portal_ro'@'%' IDENTIFIED BY 'gsys_portal_ro_demo_pw';
GRANT SELECT ON legacy_demo.* TO 'gsys_portal_ro'@'%';
REVOKE INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX ON legacy_demo.* FROM 'gsys_portal_ro'@'%';
FLUSH PRIVILEGES;
