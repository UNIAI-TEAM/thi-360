-- Giao bài thi cho Lớp học (UNI-2774, TM §15 Q10, C12, C15):
-- Mỗi lần giao một bài thi cho một lớp là một dòng class_exam_assignment; mỗi học viên nhận một
-- entitlement_grant kind = 'CLASS' gắn vào entitlement (user_id, exam_id, 'CLASS', class_id).
-- class_id, exam_id, assigned_by, revoked_by không đặt khóa ngoại xuyên schema theo RULES 4.

CREATE TABLE commerce.class_exam_assignment (
    id uuid PRIMARY KEY,
    class_id uuid NOT NULL,
    exam_id uuid NOT NULL,
    exam_version_no integer NOT NULL,
    exam_code text NOT NULL,
    exam_name text NOT NULL,
    attempts_granted integer NOT NULL,
    result_view text NOT NULL,
    option_codes text[] NOT NULL DEFAULT '{}',
    benefits jsonb NOT NULL DEFAULT '{}'::jsonb,
    auto_grant_new_members boolean NOT NULL DEFAULT true,
    note text,
    status text NOT NULL DEFAULT 'ACTIVE',
    assigned_by uuid NOT NULL,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    revoked_by uuid,
    revoked_at timestamptz,
    revoke_reason text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_class_exam_assignment_attempts CHECK (attempts_granted BETWEEN 1 AND 100),
    CONSTRAINT ck_class_exam_assignment_result_view CHECK (
        result_view IN ('SCORE', 'SCORE_AND_ANSWERS', 'FULL_SOLUTION')),
    CONSTRAINT ck_class_exam_assignment_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_class_exam_assignment_revoked_fields CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL))
);

CREATE INDEX idx_class_exam_assignment_class_assigned
    ON commerce.class_exam_assignment (class_id, assigned_at DESC, id DESC);

CREATE INDEX idx_class_exam_assignment_active_auto
    ON commerce.class_exam_assignment (class_id)
    WHERE status = 'ACTIVE' AND auto_grant_new_members = true;

ALTER TABLE commerce.entitlement_grant
    ADD COLUMN class_assignment_id uuid REFERENCES commerce.class_exam_assignment (id);

ALTER TABLE commerce.entitlement_grant
    DROP CONSTRAINT IF EXISTS entitlement_grant_kind_check,
    DROP CONSTRAINT ck_entitlement_grant_kind_fields;

ALTER TABLE commerce.entitlement_grant
    ADD CONSTRAINT ck_entitlement_grant_kind CHECK (
        kind IN ('PURCHASE', 'COMPENSATION', 'CLASS')),
    ADD CONSTRAINT uq_entitlement_grant_class_assignment UNIQUE (class_assignment_id, entitlement_id),
    ADD CONSTRAINT ck_entitlement_grant_kind_fields CHECK (
        (kind = 'PURCHASE' AND order_id IS NOT NULL AND order_code IS NOT NULL
            AND compensation_attempt_id IS NULL AND class_assignment_id IS NULL)
        OR (kind = 'COMPENSATION' AND order_id IS NULL AND order_code IS NULL
            AND compensation_attempt_id IS NOT NULL AND class_assignment_id IS NULL AND attempts = 1)
        OR (kind = 'CLASS' AND order_id IS NULL AND order_code IS NULL
            AND compensation_attempt_id IS NULL AND class_assignment_id IS NOT NULL));

CREATE INDEX idx_entitlement_grant_class_assignment
    ON commerce.entitlement_grant (class_assignment_id)
    WHERE class_assignment_id IS NOT NULL;
