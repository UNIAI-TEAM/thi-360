-- UNI-2716 (CM-3a): Đơn hàng, thanh toán, sự kiện VNPAY, lịch sử đơn hàng (TM 8.1, 16.3, BS 14).
-- Tạo theo hình dạng hỗ trợ bộ đề (product_type, bundle_id, purchase_order_item) để CM-6 không phải sửa lại bảng.
-- Bổ sung khóa ngoại từ promotion_redemption.order_id và entitlement_grant.order_id tới purchase_order(id) (plan B1).

CREATE TABLE commerce.purchase_order (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    user_id uuid NOT NULL,
    buyer_username text NOT NULL,
    buyer_full_name text NOT NULL,
    product_type text NOT NULL DEFAULT 'EXAM',
    exam_id uuid,
    exam_version_no integer,
    bundle_id uuid,
    bundle_version_no integer,
    option_codes text[] NOT NULL DEFAULT '{}',
    product_code text NOT NULL,
    product_name text NOT NULL,
    list_price_vnd bigint NOT NULL,
    promotion_id uuid REFERENCES commerce.promotion (id),
    promotion_discount_vnd bigint NOT NULL DEFAULT 0,
    voucher_id uuid REFERENCES commerce.promotion (id),
    voucher_code text,
    voucher_discount_vnd bigint NOT NULL DEFAULT 0,
    discount_applied text NOT NULL,
    amount_due_vnd bigint NOT NULL,
    attempts_granted integer NOT NULL,
    benefits jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_view text NOT NULL,
    payment_method text NOT NULL,
    state text NOT NULL,
    replaced_by_order_id uuid REFERENCES commerce.purchase_order (id),
    needs_review boolean NOT NULL DEFAULT false,
    review_reason text,
    expires_at timestamptz,
    paid_at timestamptz,
    fulfilled_at timestamptz,
    fulfillment_error text,
    fulfillment_tries integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_purchase_order_code UNIQUE (code),
    CONSTRAINT ck_purchase_order_product_type CHECK (product_type IN ('EXAM', 'BUNDLE')),
    CONSTRAINT ck_purchase_order_exam_version CHECK (exam_version_no IS NULL OR exam_version_no >= 1),
    CONSTRAINT ck_purchase_order_bundle_version CHECK (bundle_version_no IS NULL OR bundle_version_no >= 1),
    CONSTRAINT ck_purchase_order_product_owner CHECK (
        (product_type = 'EXAM' AND exam_id IS NOT NULL AND exam_version_no IS NOT NULL AND bundle_id IS NULL)
        OR (product_type = 'BUNDLE' AND exam_id IS NULL AND bundle_id IS NOT NULL)),
    CONSTRAINT ck_purchase_order_list_price CHECK (list_price_vnd >= 0),
    CONSTRAINT ck_purchase_order_promo_discount CHECK (promotion_discount_vnd >= 0),
    CONSTRAINT ck_purchase_order_voucher_discount CHECK (voucher_discount_vnd >= 0),
    CONSTRAINT ck_purchase_order_discount_applied CHECK (
        discount_applied IN ('NONE', 'PROMOTION_ONLY', 'VOUCHER_ONLY', 'COMBINED')),
    CONSTRAINT ck_purchase_order_amount_due CHECK (amount_due_vnd >= 0),
    CONSTRAINT ck_purchase_order_attempts CHECK (attempts_granted >= 1),
    CONSTRAINT ck_purchase_order_payment_method CHECK (payment_method IN ('VNPAY', 'FREE')),
    CONSTRAINT ck_purchase_order_state CHECK (
        state IN ('PENDING_PAYMENT', 'PAID', 'FULFILLED', 'EXPIRED', 'REPLACED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT ck_purchase_order_fulfillment_tries CHECK (fulfillment_tries >= 0)
);

-- E13: mỗi tài khoản chỉ có tối đa một đơn chờ thanh toán cho mỗi sản phẩm.
CREATE UNIQUE INDEX uq_purchase_order_pending_user_product
    ON commerce.purchase_order (user_id, product_code)
    WHERE state = 'PENDING_PAYMENT';

-- Phục vụ /me/orders (mới nhất trước) và tra cứu chỉ mục của OrderDeletionGuard.
CREATE INDEX idx_purchase_order_user_created
    ON commerce.purchase_order (user_id, created_at DESC, id DESC);

CREATE INDEX idx_purchase_order_state_paid_at
    ON commerce.purchase_order (state, paid_at)
    WHERE state = 'PAID';

CREATE INDEX idx_purchase_order_replaced_by
    ON commerce.purchase_order (replaced_by_order_id)
    WHERE replaced_by_order_id IS NOT NULL;

CREATE TABLE commerce.purchase_order_item (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES commerce.purchase_order (id) ON DELETE CASCADE,
    exam_id uuid NOT NULL,
    exam_version_no integer NOT NULL,
    exam_code text NOT NULL,
    exam_name text NOT NULL,
    option_codes text[] NOT NULL DEFAULT '{}',
    attempts_granted integer NOT NULL,
    benefits jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_view text NOT NULL,
    position integer NOT NULL DEFAULT 1,
    CONSTRAINT uq_purchase_order_item_order_exam UNIQUE (order_id, exam_id),
    CONSTRAINT ck_purchase_order_item_exam_version CHECK (exam_version_no >= 1),
    CONSTRAINT ck_purchase_order_item_attempts CHECK (attempts_granted >= 1)
);

CREATE INDEX idx_purchase_order_item_order
    ON commerce.purchase_order_item (order_id, position);

CREATE TABLE commerce.payment (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES commerce.purchase_order (id),
    attempt_seq integer NOT NULL,
    txn_ref text NOT NULL,
    vnp_create_date text NOT NULL,
    vnp_expire_date text NOT NULL,
    amount_vnd bigint NOT NULL,
    state text NOT NULL,
    is_duplicate boolean NOT NULL DEFAULT false,
    excess_reason text,
    late_success_review text,
    late_success_at timestamptz,
    vnp_transaction_no text,
    bank_code text,
    card_type text,
    pay_date text,
    response_code text,
    transaction_status text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_txn_ref UNIQUE (txn_ref),
    CONSTRAINT uq_payment_order_seq UNIQUE (order_id, attempt_seq),
    CONSTRAINT ck_payment_attempt_seq CHECK (attempt_seq >= 1),
    CONSTRAINT ck_payment_amount_vnd CHECK (amount_vnd > 0),
    CONSTRAINT ck_payment_state CHECK (state IN ('CREATED', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT ck_payment_excess_reason CHECK (
        excess_reason IS NULL OR excess_reason IN ('AFTER_COUNTED', 'REJECTED_AFTER_RECONCILE')),
    CONSTRAINT ck_payment_late_success_review CHECK (
        late_success_review IS NULL OR late_success_review IN ('PENDING', 'ACCEPTED', 'REJECTED', 'AUTO_EXCESS'))
);

-- Bỏ qua rỗng và '0' vì VNPAY sandbox trả '0' cho giao dịch bị hủy.
CREATE UNIQUE INDEX uq_payment_vnp_transaction_no
    ON commerce.payment (vnp_transaction_no)
    WHERE vnp_transaction_no IS NOT NULL AND btrim(vnp_transaction_no) <> '' AND vnp_transaction_no <> '0';

-- BS 14: mỗi đơn có tối đa một khoản được tính (SUCCEEDED và is_duplicate = false).
CREATE UNIQUE INDEX uq_payment_counted_per_order
    ON commerce.payment (order_id)
    WHERE state = 'SUCCEEDED' AND is_duplicate = false;

CREATE INDEX idx_payment_order_created
    ON commerce.payment (order_id, created_at DESC);

CREATE TABLE commerce.payment_event (
    id uuid PRIMARY KEY,
    payment_id uuid REFERENCES commerce.payment (id),
    source text NOT NULL,
    raw jsonb NOT NULL,
    signature_valid boolean NOT NULL,
    received_at timestamptz NOT NULL,
    outcome text NOT NULL,
    CONSTRAINT ck_payment_event_source CHECK (source IN ('IPN', 'RETURN', 'QUERYDR', 'REFUND_API'))
);

CREATE INDEX idx_payment_event_payment
    ON commerce.payment_event (payment_id, received_at DESC)
    WHERE payment_id IS NOT NULL;

CREATE INDEX idx_payment_event_received
    ON commerce.payment_event (received_at DESC);

CREATE TABLE commerce.order_history (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES commerce.purchase_order (id) ON DELETE CASCADE,
    from_state text,
    to_state text NOT NULL,
    actor text NOT NULL,
    reason text,
    at timestamptz NOT NULL
);

CREATE INDEX idx_order_history_order
    ON commerce.order_history (order_id, at ASC);

ALTER TABLE commerce.promotion_redemption
    ADD CONSTRAINT fk_promotion_redemption_order
    FOREIGN KEY (order_id) REFERENCES commerce.purchase_order (id);

ALTER TABLE commerce.entitlement_grant
    ADD CONSTRAINT fk_entitlement_grant_order
    FOREIGN KEY (order_id) REFERENCES commerce.purchase_order (id);
