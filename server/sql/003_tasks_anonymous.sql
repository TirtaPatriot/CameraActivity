-- Task types managed in the admin panel, anonymous uploads per device,
-- and silently recorded facts (mock location, sharing). Run after 002_accounts_settings.sql.

CREATE TABLE cactivity.task_type (
    id          BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_task_type PRIMARY KEY,
    name        NVARCHAR(100) NOT NULL,
    icon        VARCHAR(20)   NOT NULL CONSTRAINT DF_task_type_icon DEFAULT 'other',  -- meter|inspect|replace|leak|seal|other
    sort_order  INT           NOT NULL CONSTRAINT DF_task_type_sort_order DEFAULT 0,
    is_active   BIT           NOT NULL CONSTRAINT DF_task_type_is_active DEFAULT 1,
    created_at  DATETIME2(3)  NOT NULL CONSTRAINT DF_task_type_created_at DEFAULT SYSUTCDATETIME()
);
GO

ALTER TABLE cactivity.capture ADD
    task_type_id     BIGINT        NULL CONSTRAINT FK_capture_task_type REFERENCES cactivity.task_type (id),
    task_name        NVARCHAR(100) NULL,             -- name as shown on the device at capture time
    is_mock_location BIT           NOT NULL CONSTRAINT DF_capture_is_mock_location DEFAULT 0,
    device_id        VARCHAR(64)   NULL,             -- random id per app install (anonymous uploads)
    shared_at        DATETIMEOFFSET(3) NULL;          -- shared from the app before upload
GO

CREATE INDEX IX_capture_device ON cactivity.capture (device_id, taken_at DESC);
GO
