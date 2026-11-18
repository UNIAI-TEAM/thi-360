-- UNI-2650: structure v2 persistence fields.
ALTER TABLE exam.exam_structure
    ADD COLUMN IF NOT EXISTS exam_quotas jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE exam.exam
    ADD COLUMN IF NOT EXISTS build_mode text NOT NULL DEFAULT 'STANDARD'
        CHECK (build_mode IN ('STANDARD', 'REVIEW'));

ALTER TABLE exam.exam_template
    ADD COLUMN IF NOT EXISTS label text,
    ADD COLUMN IF NOT EXISTS summary text,
    ADD COLUMN IF NOT EXISTS build_mode text NOT NULL DEFAULT 'STANDARD'
        CHECK (build_mode IN ('STANDARD', 'REVIEW')),
    ADD COLUMN IF NOT EXISTS is_default boolean NOT NULL DEFAULT false;

UPDATE exam.exam_template
SET label = code,
    summary = expert_note;

WITH ranked AS (
    SELECT code,
           row_number() OVER (
               PARTITION BY kind_code, scope, part_code, subject_code, build_mode
               ORDER BY structure_year DESC NULLS LAST, code
           ) = 1 AS default_value
    FROM exam.exam_template
)
UPDATE exam.exam_template t
SET is_default = ranked.default_value
FROM ranked
WHERE ranked.code = t.code;
