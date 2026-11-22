-- UNI-2720 (CM-6): Bảng bộ đề (bundle, bundle_item, bundle_version), mở rộng storefront_product,
-- và bổ sung khóa ngoại bundle_id trên purchase_order và promotion_scope_item (TM 16.1, 16.3, AC-1).

CREATE TABLE commerce.bundle (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    code_normalized text NOT NULL,
    name text NOT NULL,
    summary text,
    description text,
    exam_kind_item_id uuid NOT NULL,
    list_price_vnd bigint,
    attempts_per_exam integer,
    benefit_ai_explanation boolean NOT NULL DEFAULT false,
    benefit_strength_weakness boolean NOT NULL DEFAULT false,
    benefit_practice boolean NOT NULL DEFAULT false,
    benefit_note text,
    result_view text NOT NULL DEFAULT 'SCORE_AND_ANSWERS',
    status text NOT NULL DEFAULT 'DRAFT',
    has_unpublished_changes boolean NOT NULL DEFAULT false,
    serving_version integer,
    revision bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    created_by uuid,
    created_by_username text,
    updated_at timestamptz NOT NULL,
    updated_by uuid,
    updated_by_username text,
    discontinued_at timestamptz,
    discontinued_by uuid,
    CONSTRAINT uq_bundle_code_normalized UNIQUE (code_normalized),
    CONSTRAINT ck_bundle_list_price CHECK (list_price_vnd IS NULL OR list_price_vnd >= 0),
    CONSTRAINT ck_bundle_attempts CHECK (attempts_per_exam IS NULL OR (attempts_per_exam >= 1 AND attempts_per_exam <= 100)),
    CONSTRAINT ck_bundle_result_view CHECK (result_view IN ('SCORE', 'SCORE_AND_ANSWERS', 'FULL_SOLUTION')),
    CONSTRAINT ck_bundle_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISCONTINUED')),
    CONSTRAINT ck_bundle_serving_version CHECK (serving_version IS NULL OR serving_version >= 1)
);

CREATE INDEX idx_bundle_status_updated
    ON commerce.bundle (status, updated_at DESC, id);

CREATE INDEX idx_bundle_exam_kind
    ON commerce.bundle (exam_kind_item_id);

CREATE TABLE commerce.bundle_item (
    id uuid PRIMARY KEY,
    bundle_id uuid NOT NULL REFERENCES commerce.bundle (id) ON DELETE CASCADE,
    exam_id uuid NOT NULL,
    position integer NOT NULL,
    CONSTRAINT ck_bundle_item_position CHECK (position >= 1),
    CONSTRAINT uq_bundle_item_bundle_exam UNIQUE (bundle_id, exam_id),
    CONSTRAINT uq_bundle_item_bundle_position UNIQUE (bundle_id, position)
);

CREATE INDEX idx_bundle_item_exam
    ON commerce.bundle_item (exam_id);

CREATE TABLE commerce.bundle_version (
    id uuid PRIMARY KEY,
    bundle_id uuid NOT NULL REFERENCES commerce.bundle (id) ON DELETE CASCADE,
    version_no integer NOT NULL,
    snapshot jsonb NOT NULL,
    published_at timestamptz NOT NULL,
    published_by uuid,
    published_by_username text,
    CONSTRAINT ck_bundle_version_no CHECK (version_no >= 1),
    CONSTRAINT uq_bundle_version_bundle_no UNIQUE (bundle_id, version_no)
);

ALTER TABLE commerce.storefront_product
    DROP CONSTRAINT ck_storefront_product_scope;

ALTER TABLE commerce.storefront_product
    ADD CONSTRAINT ck_storefront_product_scope CHECK (scope IN ('FULL', 'PART', 'SUBJECT', 'BUNDLE'));

ALTER TABLE commerce.storefront_product
    ADD CONSTRAINT uq_storefront_product_bundle UNIQUE (bundle_id);

-- Khóa ngoại bắt buộc theo AC-1 (bundle_id NULL giữ nguyên; non-null mồ côi làm migration dừng báo lỗi).
ALTER TABLE commerce.purchase_order
    ADD CONSTRAINT fk_purchase_order_bundle FOREIGN KEY (bundle_id) REFERENCES commerce.bundle (id);

ALTER TABLE commerce.promotion_scope_item
    ADD CONSTRAINT fk_promotion_scope_item_bundle FOREIGN KEY (bundle_id) REFERENCES commerce.bundle (id);
