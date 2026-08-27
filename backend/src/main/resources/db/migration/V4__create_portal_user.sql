-- Prototype PostgreSQL only. Technical Design 5.7 / 7章.
-- Backs Spring Security session Form Login. NOT linked to Legacy MS_USER.

CREATE TABLE portal_user (
    id                  BIGSERIAL PRIMARY KEY,
    username             VARCHAR(50)     NOT NULL,
    display_name         VARCHAR(100)    NOT NULL,
    password_hash        VARCHAR(200)    NOT NULL,
    role                  VARCHAR(30)     NOT NULL,
    enabled               BOOLEAN         NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_portal_user_username UNIQUE (username)
);
