-- Test only. Lives in db/test-migration, which only application-test.yml adds to the Flyway locations, so it never
-- reaches a real database. Version 9000 keeps it above every real migration.
CREATE TABLE IF NOT EXISTS kernel_probe (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uk_kernel_probe_name UNIQUE (name)
);
