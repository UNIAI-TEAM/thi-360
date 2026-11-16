CREATE TABLE exam.exam_version (
    id uuid PRIMARY KEY,
    exam_id uuid NOT NULL REFERENCES exam.exam(id),
    version_no integer NOT NULL CHECK (version_no > 0),
    config jsonb NOT NULL,
    parts jsonb NOT NULL,
    published_path_ids text[] NOT NULL,
    serving_path_ids text[] NOT NULL,
    check_run_id uuid NOT NULL,
    state text NOT NULL CHECK (state IN ('SERVING', 'RETIRED')),
    status text NOT NULL CHECK (status IN ('PUBLISHED', 'DISCONTINUED')),
    published_at timestamptz NOT NULL,
    published_by uuid NOT NULL,
    discontinued_at timestamptz,
    discontinued_by uuid,
    note text,
    CONSTRAINT uq_exam_version_number UNIQUE (exam_id, version_no),
    CONSTRAINT chk_exam_version_discontinued CHECK (
        (status = 'PUBLISHED' AND discontinued_at IS NULL AND discontinued_by IS NULL)
        OR (status = 'DISCONTINUED' AND discontinued_at IS NOT NULL AND discontinued_by IS NOT NULL)
    )
);
