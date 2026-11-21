-- UNI-2718 (CM-5): Bảng hoàn tiền (commerce.refund), báo cáo đối soát (commerce.reconciliation_report)
-- và các chỉ mục đọc bổ trợ cho danh sách đơn hàng, người mua và hàng chờ đối soát (TM 8.4, 9.1, 9.4, BS 14).

CREATE TABLE commerce.refund (
    id uuid PRIMARY KEY,
    vnp_request_id text,
    payment_id uuid NOT NULL REFERENCES commerce.payment (id),
    order_id uuid NOT NULL REFERENCES commerce.purchase_order (id),
    amount_vnd bigint NOT NULL,
    kind text NOT NULL,
    state text NOT NULL,
    reason text NOT NULL,
    verified_system_error boolean NOT NULL,
    external_ref text,
    gateway_message text,
    mark_failed_evidence text,
    mark_failed_reason text,
    vnp_response_code text,
    vnp_transaction_status text,
    vnp_transaction_no text,
    requested_by text NOT NULL,
    requested_by_id uuid,
    requested_at timestamptz NOT NULL,
    finished_at timestamptz,
    idempotency_key text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_refund_vnp_request_id UNIQUE (vnp_request_id),
    CONSTRAINT uq_refund_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_refund_amount_vnd CHECK (amount_vnd > 0),
    CONSTRAINT ck_refund_kind CHECK (kind IN ('API', 'MANUAL')),
    CONSTRAINT ck_refund_state CHECK (state IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT ck_refund_vnp_request_id_api CHECK (
        kind <> 'API' OR (vnp_request_id IS NOT NULL AND char_length(vnp_request_id) = 32)),
    CONSTRAINT ck_refund_verified_system_error CHECK (verified_system_error = true),
    CONSTRAINT ck_refund_external_ref_manual CHECK (
        kind <> 'MANUAL' OR (external_ref IS NOT NULL AND btrim(external_ref) <> ''))
);

-- Mỗi giao dịch thanh toán chỉ có tối đa 1 yêu cầu hoàn đang xử lý hoặc chưa rõ kết quả (TM 9.4).
CREATE UNIQUE INDEX uq_refund_active_per_payment
    ON commerce.refund (payment_id)
    WHERE state IN ('PROCESSING', 'UNKNOWN');

CREATE INDEX idx_refund_payment_state
    ON commerce.refund (payment_id, state);

CREATE INDEX idx_refund_order_requested
    ON commerce.refund (order_id, requested_at DESC);

CREATE INDEX idx_refund_state_active
    ON commerce.refund (state)
    WHERE state IN ('PROCESSING', 'UNKNOWN');

CREATE TABLE commerce.reconciliation_report (
    id uuid PRIMARY KEY,
    report_date date NOT NULL,
    generated_at timestamptz NOT NULL,
    paid_unfulfilled_count integer NOT NULL DEFAULT 0,
    unknown_payment_count integer NOT NULL DEFAULT 0,
    unrefunded_excess_count integer NOT NULL DEFAULT 0,
    pending_late_success_count integer NOT NULL DEFAULT 0,
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_reconciliation_report_date UNIQUE (report_date)
);

CREATE INDEX idx_reconciliation_report_date_desc
    ON commerce.reconciliation_report (report_date DESC);

-- Chỉ mục đọc bổ trợ không thay đổi dữ liệu hiện có.
CREATE INDEX idx_purchase_order_created_desc
    ON commerce.purchase_order (created_at DESC, id DESC);

CREATE INDEX idx_purchase_order_item_exam_order
    ON commerce.purchase_order_item (exam_id, order_id);

CREATE INDEX idx_payment_late_success_pending
    ON commerce.payment (order_id)
    WHERE late_success_review = 'PENDING';
