CREATE TABLE exam.exam_check_unit (
    exam_id uuid NOT NULL REFERENCES exam.exam(id) ON DELETE CASCADE,
    unit_key text NOT NULL,
    section_ids text[] NOT NULL,
    input_fingerprint text NOT NULL,
    source_revision bigint NOT NULL,
    outcome text NOT NULL CHECK (outcome IN ('VALID', 'INVALID', 'UNCONFIRMED')),
    issues jsonb NOT NULL,
    witness jsonb,
    checked_at timestamptz NOT NULL,
    duration_ms bigint NOT NULL CHECK (duration_ms >= 0),
    PRIMARY KEY (exam_id, unit_key)
);
