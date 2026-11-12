-- Accounts, login tokens and server-managed settings (branding, capture policy).
-- Run after 001_init.sql.

CREATE TABLE cactivity.app_user (
    id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_app_user PRIMARY KEY,
    username      VARCHAR(50)    NOT NULL,
    display_name  NVARCHAR(100)  NOT NULL,
    user_code     VARCHAR(50)    NULL,              -- officer code / employee ID, printed on the stamp
    role          VARCHAR(10)    NOT NULL,          -- ADMIN | OFFICER
    password_hash VARCHAR(200)   NOT NULL,          -- pbkdf2$<iterations>$<salt>$<hash>
    is_active     BIT            NOT NULL CONSTRAINT DF_app_user_is_active DEFAULT 1,
    created_at    DATETIME2(3)   NOT NULL CONSTRAINT DF_app_user_created_at DEFAULT SYSUTCDATETIME(),
    updated_at    DATETIME2(3)   NOT NULL CONSTRAINT DF_app_user_updated_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_app_user_username UNIQUE (username),
    CONSTRAINT CK_app_user_role CHECK (role IN ('ADMIN', 'OFFICER'))
);
GO

CREATE TABLE cactivity.auth_token (
    id           BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_auth_token PRIMARY KEY,
    token_hash   CHAR(64)       NOT NULL,           -- SHA-256 hex of the bearer token
    user_id      BIGINT         NOT NULL CONSTRAINT FK_auth_token_user REFERENCES cactivity.app_user (id),
    kind         VARCHAR(10)    NOT NULL,           -- MOBILE | PANEL
    device_name  NVARCHAR(100)  NULL,
    created_at   DATETIME2(3)   NOT NULL CONSTRAINT DF_auth_token_created_at DEFAULT SYSUTCDATETIME(),
    last_used_at DATETIME2(3)   NULL,
    expires_at   DATETIME2(3)   NULL,               -- NULL = no expiry (mobile)
    revoked_at   DATETIME2(3)   NULL,
    CONSTRAINT UQ_auth_token_token_hash UNIQUE (token_hash),
    CONSTRAINT CK_auth_token_kind CHECK (kind IN ('MOBILE', 'PANEL'))
);
GO

CREATE INDEX IX_auth_token_user ON cactivity.auth_token (user_id);
GO

CREATE TABLE cactivity.setting (
    setting_key VARCHAR(100)   NOT NULL CONSTRAINT PK_setting PRIMARY KEY,
    value       NVARCHAR(MAX)  NOT NULL,            -- JSON or plain text
    updated_at  DATETIME2(3)   NOT NULL CONSTRAINT DF_setting_updated_at DEFAULT SYSUTCDATETIME()
);
GO

ALTER TABLE cactivity.capture
    ADD user_id BIGINT NULL CONSTRAINT FK_capture_user REFERENCES cactivity.app_user (id);
GO

CREATE INDEX IX_capture_user_id ON cactivity.capture (user_id, taken_at DESC);
GO
