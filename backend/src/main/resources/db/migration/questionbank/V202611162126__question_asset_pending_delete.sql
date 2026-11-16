-- BE-2b fix r1 (R1-3/R1-8, UNI-2614): ý định xóa object storage bền vững.
-- Transaction dọn mồ côi xóa dòng `question_asset` VÀ ghi khóa object vào bảng này;
-- sau khi commit, worker xóa object rồi mới gỡ dòng ý định (ack). Mỗi lần quét thử
-- lại các ý định còn dư trước — object không còn bị mất vĩnh viễn nếu lần xóa đầu lỗi
-- hoặc tiến trình dừng giữa chừng.

CREATE TABLE questionbank.question_asset_pending_delete (
    object_key text PRIMARY KEY,
    asset_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
