-- Photo/video activity metadata. The files themselves live on the NAS (storage_path is relative to STORAGE_ROOT).
-- All objects of this app live in the "cactivity" schema.
-- Run manually against the target database.

IF SCHEMA_ID(N'cactivity') IS NULL
    EXEC (N'CREATE SCHEMA cactivity');
GO

CREATE TABLE cactivity.capture (
    id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_capture PRIMARY KEY,
    client_id     VARCHAR(64)       NOT NULL,
    media_type    VARCHAR(10)       NOT NULL,          -- PHOTO | VIDEO
    taken_at      DATETIMEOFFSET(3) NOT NULL,          -- capture time on the device, with zone offset
    timezone      VARCHAR(64)       NULL,              -- e.g. Asia/Jakarta
    latitude      DECIMAL(10,7)     NULL,
    longitude     DECIMAL(10,7)     NULL,
    accuracy_m    REAL              NULL,
    altitude_m    FLOAT             NULL,
    note          NVARCHAR(500)     NULL,              -- activity description
    user_code     VARCHAR(50)       NULL,              -- officer code / employee ID
    device_model  NVARCHAR(100)     NULL,
    platform      VARCHAR(20)       NULL,              -- android | ios
    storage_path  NVARCHAR(400)     NOT NULL,          -- photo/2026/11/12/<client_id>.jpg
    file_size     BIGINT            NOT NULL,
    original_name NVARCHAR(200)     NULL,
    uploaded_at   DATETIME2(3)      NOT NULL CONSTRAINT DF_capture_uploaded_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_capture_client_id UNIQUE (client_id),
    CONSTRAINT CK_capture_media_type CHECK (media_type IN ('PHOTO', 'VIDEO'))
);
GO

CREATE INDEX IX_capture_taken_at ON cactivity.capture (taken_at DESC);
CREATE INDEX IX_capture_user ON cactivity.capture (user_code, taken_at DESC);
GO
