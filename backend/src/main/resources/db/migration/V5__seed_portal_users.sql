-- Prototype PostgreSQL only. Demo accounts for local Prototype login only -
-- NOT linked to any real identity, NOT a production/shared credential.
-- Password for all three accounts: DemoPass123! (BCrypt-hashed below).
-- This is a local-only Prototype demo password, safe to commit (Critical
-- Safety Rules 0.4 Credential Guard - not a production/shared/external secret).

INSERT INTO portal_user (username, display_name, password_hash, role) VALUES
    ('purchase01',  '購買担当（デモ）',   '$2a$10$UsD.12ENDE2F5G2PYSkB.OPT/KfZXt1jVaqi0Js/ayiINNSA5L1ZO', 'PURCHASE'),
    ('sales_admin', '営業管理（デモ）',   '$2a$10$CoGpXfnsN7FsKqrbBl/AxOiJWJ2CWhseSyWtNnDBjS.1EoK3rZMPm', 'SALES_ADMIN'),
    ('sys_admin',   'システム管理（デモ）', '$2a$10$tmmq/K075BUP8Z/mYyK7beQKXcdNiJ0TynzquwDrS5G.T35m.Tp8C', 'SYS_ADMIN');
