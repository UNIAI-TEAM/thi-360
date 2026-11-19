-- Quyền dùng (TM 8.5): một dòng entitlement cho mỗi (tài khoản, bài thi, nguồn), mỗi lần mua hoặc cấp bù
-- là một entitlement_grant, mỗi lượt giữ hoặc dùng là một entitlement_usage.
-- Cần PostgreSQL 15 trở lên cho NULLS NOT DISTINCT. user_id, exam_id không có khóa ngoại: bảng của module khác.

CREATE TABLE commerce.entitlement (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    exam_id uuid NOT NULL,
    source text NOT NULL CHECK (source IN ('PURCHASE', 'CLASS')),
    source_ref uuid,
    attempts_total integer NOT NULL DEFAULT 0,
    attempts_used integer NOT NULL DEFAULT 0,
    attempts_reserved integer NOT NULL DEFAULT 0,
    attempts_revoked integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    -- Chỉ mục này có user_id đứng đầu: AccountDeletionGuard và phần quyền dùng của hồ sơ học tập tra theo user_id.
    CONSTRAINT uq_entitlement_scope UNIQUE NULLS NOT DISTINCT (user_id, exam_id, source, source_ref),
    -- Khóa phụ để entitlement_grant giữ cùng exam_id với dòng entitlement của nó.
    CONSTRAINT uq_entitlement_id_exam UNIQUE (id, exam_id),
    CONSTRAINT ck_entitlement_counts_non_negative CHECK (
        attempts_total >= 0 AND attempts_used >= 0 AND attempts_reserved >= 0 AND attempts_revoked >= 0),
    CONSTRAINT ck_entitlement_attempts_within_total CHECK (
        attempts_used + attempts_reserved + attempts_revoked <= attempts_total)
);

CREATE TABLE commerce.entitlement_grant (
    id uuid PRIMARY KEY,
    -- Thứ tự cấp, dùng cho FIFO (E21) khi hai lần cấp có cùng granted_at.
    seq bigint GENERATED ALWAYS AS IDENTITY,
    entitlement_id uuid NOT NULL,
    kind text NOT NULL CHECK (kind IN ('PURCHASE', 'COMPENSATION')),
    -- Chưa có khóa ngoại tới purchase_order: CM-3a thêm bằng migration của mình (plan B1).
    order_id uuid,
    order_code text,
    exam_id uuid NOT NULL,
    compensation_attempt_id uuid,
    option_codes text[] NOT NULL DEFAULT '{}',
    attempts integer NOT NULL,
    attempts_used integer NOT NULL DEFAULT 0,
    attempts_reserved integer NOT NULL DEFAULT 0,
    attempts_revoked integer NOT NULL DEFAULT 0,
    benefits jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_view text NOT NULL,
    exam_version_no integer NOT NULL,
    granted_at timestamptz NOT NULL,
    granted_by text NOT NULL,
    reason text,
    -- Đặt khi lần cấp bị hoàn hết (revokeUnused): attempts_revoked vẫn có thể bằng 0 nếu không còn lượt để thu hồi.
    revoked_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_entitlement_grant_entitlement FOREIGN KEY (entitlement_id, exam_id)
        REFERENCES commerce.entitlement (id, exam_id),
    CONSTRAINT uq_entitlement_grant_order_exam UNIQUE (order_id, exam_id),
    CONSTRAINT uq_entitlement_grant_compensation_attempt UNIQUE (compensation_attempt_id),
    CONSTRAINT ck_entitlement_grant_kind_fields CHECK (
        (kind = 'PURCHASE' AND order_id IS NOT NULL AND order_code IS NOT NULL AND compensation_attempt_id IS NULL)
        OR (kind = 'COMPENSATION' AND order_id IS NULL AND order_code IS NULL
            AND compensation_attempt_id IS NOT NULL AND attempts = 1)),
    CONSTRAINT ck_entitlement_grant_attempts_positive CHECK (attempts >= 1),
    CONSTRAINT ck_entitlement_grant_counts_non_negative CHECK (
        attempts_used >= 0 AND attempts_reserved >= 0 AND attempts_revoked >= 0),
    CONSTRAINT ck_entitlement_grant_attempts_within_total CHECK (
        attempts_used + attempts_reserved + attempts_revoked <= attempts)
);

CREATE INDEX idx_entitlement_grant_fifo
    ON commerce.entitlement_grant (entitlement_id, granted_at, seq);

CREATE TABLE commerce.entitlement_usage (
    id uuid PRIMARY KEY,
    grant_id uuid NOT NULL REFERENCES commerce.entitlement_grant (id),
    attempt_id uuid NOT NULL,
    state text NOT NULL CHECK (state IN ('RESERVED', 'USED', 'RELEASED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_entitlement_usage_attempt UNIQUE (attempt_id)
);

CREATE INDEX idx_entitlement_usage_grant
    ON commerce.entitlement_usage (grant_id);
