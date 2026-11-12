-- Single sign-on (OIDC): link accounts to the identity provider; SSO-only accounts have no password.
-- Run after 003_tasks_anonymous.sql.

ALTER TABLE cactivity.app_user ALTER COLUMN password_hash VARCHAR(200) NULL;
GO

ALTER TABLE cactivity.app_user ADD
    oidc_issuer  NVARCHAR(400) NULL,   -- "iss" of the provider, e.g. https://sso.example.org
    oidc_subject NVARCHAR(255) NULL;   -- "sub": the provider's stable user id
GO

CREATE UNIQUE INDEX UQ_app_user_oidc ON cactivity.app_user (oidc_issuer, oidc_subject)
    WHERE oidc_subject IS NOT NULL;
GO

CREATE INDEX IX_app_user_user_code ON cactivity.app_user (user_code) WHERE user_code IS NOT NULL;
GO
