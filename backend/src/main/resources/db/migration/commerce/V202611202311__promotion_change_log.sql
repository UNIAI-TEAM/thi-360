-- UNI-2713 (CM-2): Lịch sử thay đổi khuyến mãi trong cùng transaction với mỗi lệnh ghi (BS §10).

CREATE TABLE commerce.promotion_change_log (
    id uuid PRIMARY KEY,
    promotion_id uuid NOT NULL REFERENCES commerce.promotion (id),
    action text NOT NULL,
    actor_id uuid,
    actor_username text,
    changes jsonb NOT NULL DEFAULT '[]'::jsonb,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT ck_promotion_change_log_action CHECK (
        action IN ('CREATED', 'UPDATED', 'STOPPED', 'RESUMED', 'DELETED'))
);

CREATE INDEX idx_promotion_change_log_promo_time
    ON commerce.promotion_change_log (promotion_id, occurred_at DESC, id DESC);
