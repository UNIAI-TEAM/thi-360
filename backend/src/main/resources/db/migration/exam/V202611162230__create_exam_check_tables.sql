CREATE TABLE exam.exam_check_run (
    id uuid PRIMARY KEY,
    exam_id uuid NOT NULL UNIQUE REFERENCES exam.exam(id) ON DELETE CASCADE,
    exam_revision bigint NOT NULL,
    structure_hash text,
    config_snapshot jsonb NOT NULL,
    profile_revision bigint,
    catalog_revision bigint NOT NULL,
    input_fingerprint text NOT NULL,
    source_revisions jsonb NOT NULL,
    result jsonb NOT NULL,
    witness jsonb NOT NULL,
    checked_at timestamptz NOT NULL,
    duration_ms bigint NOT NULL CHECK (duration_ms >= 0)
);

CREATE TABLE exam.exam_check_job (
    id uuid PRIMARY KEY,
    exam_id uuid NOT NULL REFERENCES exam.exam(id) ON DELETE CASCADE,
    state text NOT NULL CHECK (state IN ('RUNNING', 'DONE', 'FAILED')),
    started_by uuid NOT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz,
    result jsonb,
    error_code text
);

CREATE UNIQUE INDEX exam_check_job_one_running_per_exam
    ON exam.exam_check_job (exam_id) WHERE state = 'RUNNING';
CREATE INDEX exam_check_job_finished_at ON exam.exam_check_job (finished_at)
    WHERE state IN ('DONE', 'FAILED');
