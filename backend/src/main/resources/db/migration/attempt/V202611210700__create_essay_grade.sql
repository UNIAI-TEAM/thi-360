-- AT-5 (UNI-2726): manual essay grading queue, claims, drafts, final scores, and reopen history (LT §4, §9.2, F17).
-- No cross-schema foreign keys (grader_id references identity.user_account logically).
-- fk_essay_grade_superseded_by is DEFERRABLE INITIALLY DEFERRED so old row can be marked superseded
-- and flushed before inserting the replacement row with superseded_by IS NULL in the same transaction.

CREATE TABLE attempt.essay_grade (
    id uuid PRIMARY KEY,
    attempt_item_id uuid NOT NULL REFERENCES attempt.attempt_item (id) ON DELETE CASCADE,
    grader_id uuid NOT NULL,
    criteria jsonb,
    points numeric(10,4) CHECK (points IS NULL OR points >= 0),
    comment text,
    grading_guide_version integer,
    state text NOT NULL CHECK (state IN ('DRAFT', 'FINAL')),
    claimed_at timestamptz NOT NULL,
    last_activity_at timestamptz NOT NULL,
    finalized_at timestamptz,
    reopened_reason text,
    superseded_by uuid CONSTRAINT fk_essay_grade_superseded_by REFERENCES attempt.essay_grade (id) DEFERRABLE INITIALLY DEFERRED,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_essay_grade_state_fields CHECK (
        (state = 'FINAL' AND points IS NOT NULL AND finalized_at IS NOT NULL)
        OR (state = 'DRAFT' AND finalized_at IS NULL)
    ),
    CONSTRAINT chk_essay_grade_not_self_superseded CHECK (superseded_by IS NULL OR superseded_by <> id),
    CONSTRAINT chk_essay_grade_activity_order CHECK (last_activity_at >= claimed_at),
    CONSTRAINT chk_essay_grade_criteria_array CHECK (criteria IS NULL OR jsonb_typeof(criteria) = 'array')
);

CREATE UNIQUE INDEX uq_essay_grade_active_item
    ON attempt.essay_grade (attempt_item_id)
    WHERE superseded_by IS NULL;

CREATE INDEX idx_essay_grade_item_history
    ON attempt.essay_grade (attempt_item_id, claimed_at DESC);

CREATE INDEX idx_essay_grade_active_state
    ON attempt.essay_grade (state, last_activity_at)
    WHERE superseded_by IS NULL;

CREATE INDEX idx_essay_grade_superseded_by
    ON attempt.essay_grade (superseded_by)
    WHERE superseded_by IS NOT NULL;
