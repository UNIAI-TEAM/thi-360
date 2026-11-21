-- UNI-2717 (CM-3b): Cột next_query_at và các chỉ mục cho worker querydr và worker hết hạn đơn (TM 8.4, 8.6, plan B2).

ALTER TABLE commerce.payment
    ADD COLUMN next_query_at timestamptz;

-- Dòng CREATED/UNKNOWN hiện có giữ next_query_at = NULL nên được chọn ngay theo thứ tự NULLS FIRST.
CREATE INDEX idx_payment_open_querydr
    ON commerce.payment (next_query_at NULLS FIRST, created_at)
    WHERE state IN ('CREATED', 'UNKNOWN');

CREATE INDEX idx_purchase_order_pending_expires
    ON commerce.purchase_order (expires_at)
    WHERE state = 'PENDING_PAYMENT' AND expires_at IS NOT NULL;
