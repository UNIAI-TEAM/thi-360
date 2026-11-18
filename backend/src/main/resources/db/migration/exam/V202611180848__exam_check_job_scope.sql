ALTER TABLE exam.exam_check_job
    ADD COLUMN check_scope text NOT NULL DEFAULT 'EXAM',
    ADD COLUMN target_id text,
    ADD CONSTRAINT exam_check_job_scope_check CHECK (
        (check_scope = 'EXAM' AND target_id IS NULL)
        OR (check_scope IN ('PART', 'SECTION') AND target_id IS NOT NULL)
    );
