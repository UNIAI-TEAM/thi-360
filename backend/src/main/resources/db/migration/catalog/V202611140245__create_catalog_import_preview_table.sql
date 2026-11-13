CREATE TABLE catalog.catalog_import_preview (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL REFERENCES catalog.catalog_group (id) ON DELETE CASCADE,
    status smallint NOT NULL DEFAULT 1 CHECK (status IN (1, 2)),
    total_rows integer NOT NULL CHECK (total_rows >= 0),
    valid_rows integer NOT NULL CHECK (valid_rows >= 0),
    error_rows integer NOT NULL CHECK (error_rows >= 0),
    payload jsonb NOT NULL,
    result_payload jsonb,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    confirmed_at timestamptz,
    expires_at timestamptz NOT NULL
);

CREATE INDEX idx_catalog_import_preview_group_expires
    ON catalog.catalog_import_preview (group_id, expires_at);
