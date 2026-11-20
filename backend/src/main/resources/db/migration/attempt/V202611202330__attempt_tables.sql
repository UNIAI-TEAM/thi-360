-- AT-1 (UNI-2721): core attempt tables (LT §4, BS §4, BS §17.5).
-- No cross-schema foreign keys (plan B1); numeric(10,4) preserves 0.125 exact points.

CREATE TABLE attempt.attempt (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    exam_id uuid NOT NULL,
    exam_version_no integer NOT NULL,
    entitlement_id uuid NOT NULL,
    grant_id uuid NOT NULL,
    attempt_no integer,
    exam_name text NOT NULL,
    exam_kind_item_id uuid,
    exam_kind_code text NOT NULL,
    scope text NOT NULL,
    part_code text,
    subject_item_id uuid,
    timing text NOT NULL CHECK (timing IN ('PER_PART', 'SHARED')),
    navigation_mode text NOT NULL CHECK (navigation_mode IN ('SEQUENTIAL', 'FREE')),
    choices jsonb NOT NULL DEFAULT '{}'::jsonb,
    seed bigint NOT NULL,
    generation_token uuid NOT NULL,
    state text NOT NULL CHECK (state IN (
        'STARTING', 'IN_PROGRESS', 'GRADING', 'PENDING_REVIEW', 'SCORED', 'GRADING_ERROR', 'ABORTED')),
    regrading boolean NOT NULL DEFAULT false,
    end_reason text CHECK (end_reason IS NULL OR end_reason IN (
        'SUBMITTED', 'TIME_UP', 'CHOICE_TIMEOUT', 'FAULT_CLOSED')),
    abort_reason text CHECK (abort_reason IS NULL OR abort_reason IN (
        'GENERATION_FAILED', 'GENERATION_TIMEOUT', 'GENERATION_BUSY',
        'STALE_STARTING', 'AUDIO_ASSET_MISSING', 'AUDIO_ASSET_INVALID')),
    started_at timestamptz,
    deadline_at timestamptz,
    finished_at timestamptz,
    edit_session_id uuid,
    edit_session_seen_at timestamptz,
    last_position jsonb,
    result_view text NOT NULL,
    practice_scoring text,
    reference_max numeric(10,4),
    max_score numeric(10,4),
    raw_score numeric(10,4),
    display_score numeric(10,4),
    result jsonb,
    graded_at timestamptz,
    grading_error text,
    fault_flag text,
    fault_reason text,
    idempotency_key text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- AC-3 / F6: at most one open attempt per (user, exam).
CREATE UNIQUE INDEX uq_attempt_open_user_exam
    ON attempt.attempt (user_id, exam_id)
    WHERE state IN ('STARTING', 'IN_PROGRESS');

-- F13 / AC-29: sequential attempt_no assigned only once the attempt opens.
CREATE UNIQUE INDEX uq_attempt_no_user_exam
    ON attempt.attempt (user_id, exam_id, attempt_no)
    WHERE attempt_no IS NOT NULL;

CREATE INDEX idx_attempt_user_entitlement_state
    ON attempt.attempt (user_id, entitlement_id, state);

CREATE INDEX idx_attempt_state_created_at
    ON attempt.attempt (state, created_at);

CREATE INDEX idx_attempt_user_started_at
    ON attempt.attempt (user_id, started_at DESC);

CREATE TABLE attempt.attempt_part (
    id uuid PRIMARY KEY,
    attempt_id uuid NOT NULL REFERENCES attempt.attempt (id) ON DELETE CASCADE,
    part_code text NOT NULL,
    part_name text NOT NULL,
    ord integer NOT NULL,
    duration_minutes integer,
    question_count_required integer,
    max_points numeric(10,4),
    state text NOT NULL CHECK (state IN (
        'PENDING', 'AWAITING_CHOICE', 'GENERATING', 'GENERATION_FAILED', 'OPEN', 'CLOSED', 'SKIPPED')),
    choice jsonb,
    choice_confirmed_at timestamptz,
    choice_deadline_at timestamptz,
    seed bigint NOT NULL,
    generating_since timestamptz,
    generation_token uuid,
    started_at timestamptz,
    deadline_at timestamptz,
    closed_at timestamptz,
    close_reason text CHECK (close_reason IS NULL OR close_reason IN (
        'FINISHED', 'TIME_UP', 'CHOICE_TIMEOUT', 'ATTEMPT_SUBMITTED', 'FAULT_CLOSED')),
    generation_tries integer NOT NULL DEFAULT 0,
    generation_error text,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_attempt_part_code UNIQUE (attempt_id, part_code),
    CONSTRAINT uq_attempt_part_ord UNIQUE (attempt_id, ord)
);

CREATE TABLE attempt.attempt_item (
    id uuid PRIMARY KEY,
    attempt_id uuid NOT NULL REFERENCES attempt.attempt (id) ON DELETE CASCADE,
    part_id uuid NOT NULL REFERENCES attempt.attempt_part (id) ON DELETE CASCADE,
    subject_item_id uuid,
    section_id text NOT NULL,
    component_code text,
    block_id text NOT NULL,
    parent_item_id uuid REFERENCES attempt.attempt_item (id) ON DELETE CASCADE,
    ord integer NOT NULL,
    display_no integer,
    unit_count integer NOT NULL DEFAULT 1 CHECK (unit_count >= 0),
    question_id uuid NOT NULL,
    question_version_no integer NOT NULL,
    question_type text NOT NULL,
    points_max numeric(10,4) NOT NULL CHECK (points_max >= 0),
    option_order jsonb,
    in_scope boolean NOT NULL DEFAULT true,
    answer jsonb,
    answer_seq bigint NOT NULL DEFAULT 0 CHECK (answer_seq >= 0),
    answer_received_at timestamptz,
    flagged boolean NOT NULL DEFAULT false,
    earned_points numeric(10,4),
    outcome text CHECK (outcome IS NULL OR outcome IN (
        'CORRECT', 'PARTIAL', 'WRONG', 'BLANK', 'PENDING_REVIEW')),
    grading_detail jsonb,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_attempt_item_part_ord UNIQUE (part_id, ord)
);

CREATE INDEX idx_attempt_item_attempt_part_ord
    ON attempt.attempt_item (attempt_id, part_id, ord);

CREATE INDEX idx_attempt_item_parent
    ON attempt.attempt_item (parent_item_id);

CREATE TABLE attempt.attempt_event (
    id uuid PRIMARY KEY,
    attempt_id uuid NOT NULL REFERENCES attempt.attempt (id) ON DELETE CASCADE,
    type text NOT NULL,
    session_id uuid,
    part_id uuid,
    at timestamptz NOT NULL,
    data jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_attempt_event_attempt_at
    ON attempt.attempt_event (attempt_id, at);
