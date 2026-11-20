-- UNI-2713 (CM-2): Khuyến mãi tự động và voucher (TM 7.1).
-- user_id, exam_id, exam_item_id, class_id, bundle_id và order_id chưa có khóa ngoại sang module khác
-- hoặc bảng của task sau (CM-3a thêm FK promotion_redemption.order_id; CM-6 thêm FK bundle_id).
-- CỐ Ý KHÔNG đặt CHECK (used_count <= total_limit): tiền về muộn (TM 8.6) được phép vượt giới hạn
-- và ghi nhận vào over_limit_count.

CREATE TABLE commerce.promotion (
    id uuid PRIMARY KEY,
    kind text NOT NULL,
    name text NOT NULL,
    description text,
    code text,
    code_normalized text,
    discount_type text NOT NULL,
    discount_value bigint NOT NULL,
    max_discount_vnd bigint,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    total_limit integer,
    per_user_limit integer,
    combinable boolean,
    scope text NOT NULL,
    audience text,
    status text NOT NULL,
    used_count integer NOT NULL DEFAULT 0,
    over_limit_count integer NOT NULL DEFAULT 0,
    revision bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    created_by uuid,
    created_by_username text,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    updated_by_username text,
    deleted_at timestamptz,
    deleted_by uuid,
    CONSTRAINT uq_promotion_code_normalized UNIQUE (code_normalized),
    CONSTRAINT ck_promotion_kind CHECK (kind IN ('AUTO', 'VOUCHER')),
    CONSTRAINT ck_promotion_discount_type CHECK (discount_type IN ('PERCENT', 'AMOUNT')),
    CONSTRAINT ck_promotion_discount_value CHECK (discount_value > 0),
    CONSTRAINT ck_promotion_max_discount_vnd CHECK (max_discount_vnd IS NULL OR max_discount_vnd > 0),
    CONSTRAINT ck_promotion_time_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_promotion_total_limit CHECK (total_limit IS NULL OR total_limit >= 1),
    CONSTRAINT ck_promotion_per_user_limit CHECK (per_user_limit IS NULL OR per_user_limit >= 1),
    CONSTRAINT ck_promotion_scope CHECK (scope IN ('ALL', 'EXAM_KIND', 'EXAM', 'BUNDLE')),
    CONSTRAINT ck_promotion_audience CHECK (audience IS NULL OR audience IN ('ALL', 'NEW_ACCOUNT', 'SELECTED')),
    CONSTRAINT ck_promotion_status CHECK (status IN ('ACTIVE', 'STOPPED')),
    CONSTRAINT ck_promotion_used_count CHECK (used_count >= 0),
    CONSTRAINT ck_promotion_over_limit_count CHECK (over_limit_count >= 0),
    CONSTRAINT ck_promotion_percent_range CHECK (
        discount_type <> 'PERCENT' OR (discount_value >= 1 AND discount_value <= 100)),
    CONSTRAINT ck_promotion_max_discount_percent CHECK (
        max_discount_vnd IS NULL OR discount_type = 'PERCENT'),
    CONSTRAINT ck_promotion_kind_fields CHECK (
        (kind = 'AUTO'
            AND code IS NULL AND code_normalized IS NULL
            AND per_user_limit IS NULL AND audience IS NULL AND combinable IS NULL)
        OR (kind = 'VOUCHER'
            AND code IS NOT NULL AND code_normalized IS NOT NULL
            AND audience IS NOT NULL AND combinable IS NOT NULL))
);

CREATE INDEX idx_promotion_active_lookup
    ON commerce.promotion (kind, status, starts_at, ends_at)
    WHERE deleted_at IS NULL;

CREATE TABLE commerce.promotion_scope_item (
    id uuid PRIMARY KEY,
    promotion_id uuid NOT NULL REFERENCES commerce.promotion (id) ON DELETE CASCADE,
    exam_item_id uuid,
    exam_id uuid,
    bundle_id uuid,
    CONSTRAINT ck_promotion_scope_item_exactly_one CHECK (
        (CASE WHEN exam_item_id IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN exam_id IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN bundle_id IS NOT NULL THEN 1 ELSE 0 END) = 1)
);

CREATE UNIQUE INDEX uq_promotion_scope_item_exam_item
    ON commerce.promotion_scope_item (promotion_id, exam_item_id)
    WHERE exam_item_id IS NOT NULL;
CREATE UNIQUE INDEX uq_promotion_scope_item_exam
    ON commerce.promotion_scope_item (promotion_id, exam_id)
    WHERE exam_id IS NOT NULL;
CREATE UNIQUE INDEX uq_promotion_scope_item_bundle
    ON commerce.promotion_scope_item (promotion_id, bundle_id)
    WHERE bundle_id IS NOT NULL;
CREATE INDEX idx_promotion_scope_item_promotion
    ON commerce.promotion_scope_item (promotion_id);

CREATE TABLE commerce.promotion_audience (
    id uuid PRIMARY KEY,
    promotion_id uuid NOT NULL REFERENCES commerce.promotion (id) ON DELETE CASCADE,
    class_id uuid,
    user_id uuid,
    CONSTRAINT ck_promotion_audience_exactly_one CHECK (
        (CASE WHEN class_id IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN user_id IS NOT NULL THEN 1 ELSE 0 END) = 1)
);

CREATE UNIQUE INDEX uq_promotion_audience_class
    ON commerce.promotion_audience (promotion_id, class_id)
    WHERE class_id IS NOT NULL;
CREATE UNIQUE INDEX uq_promotion_audience_user
    ON commerce.promotion_audience (promotion_id, user_id)
    WHERE user_id IS NOT NULL;
CREATE INDEX idx_promotion_audience_promotion
    ON commerce.promotion_audience (promotion_id);

CREATE TABLE commerce.promotion_redemption (
    id uuid PRIMARY KEY,
    promotion_id uuid NOT NULL REFERENCES commerce.promotion (id),
    user_id uuid NOT NULL,
    order_id uuid NOT NULL,
    order_code text,
    discount_vnd bigint NOT NULL,
    state text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_promotion_redemption_promo_order UNIQUE (promotion_id, order_id),
    CONSTRAINT ck_promotion_redemption_discount CHECK (discount_vnd >= 0),
    CONSTRAINT ck_promotion_redemption_state CHECK (state IN ('RESERVED', 'CONFIRMED', 'RELEASED'))
);

CREATE INDEX idx_promotion_redemption_promo_user_state
    ON commerce.promotion_redemption (promotion_id, user_id, state);
CREATE INDEX idx_promotion_redemption_promo_state
    ON commerce.promotion_redemption (promotion_id, state);
CREATE INDEX idx_promotion_redemption_order
    ON commerce.promotion_redemption (order_id);
