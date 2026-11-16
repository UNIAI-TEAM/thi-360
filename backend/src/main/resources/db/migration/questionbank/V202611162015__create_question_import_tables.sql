-- BE-3 (UNI-2615): nhập câu hỏi từ tệp theo spec 4.1, 4.5.
-- `question_import` = lịch sử nhập (một dòng mỗi lần commit thành công, kiểu ImportHistoryEntry);
-- `question_import_upload` = tệp tải lên chờ nhập, giữ 24 h, trạng thái
-- UPLOADED -> COMMITTING -> COMMITTED (spec 4.5). Tệp trên MinIO bị xóa sau khi commit;
-- dòng upload giữ lại để replay và truy vết.

CREATE TABLE questionbank.question_import (
    id uuid PRIMARY KEY,
    file_name text NOT NULL,
    created_count integer NOT NULL DEFAULT 0,
    single_count integer NOT NULL DEFAULT 0,
    set_count integer NOT NULL DEFAULT 0,
    set_child_count integer NOT NULL DEFAULT 0,
    skipped_duplicate_count integer NOT NULL DEFAULT 0,
    new_version_count integer NOT NULL DEFAULT 0,
    error_row_count integer NOT NULL DEFAULT 0,
    missing_knowledge_count integer NOT NULL DEFAULT 0,
    errors jsonb NOT NULL DEFAULT '[]'::jsonb,
    duration_ms bigint NOT NULL DEFAULT 0,
    imported_by uuid,
    imported_at timestamptz NOT NULL DEFAULT now()
);

-- Lịch sử mới nhất trước, chặn ở 200 dòng khi đọc (spec 4.5, quyết định 8.9).
CREATE INDEX idx_question_import_imported_at
    ON questionbank.question_import (imported_at DESC);

CREATE TABLE questionbank.question_import_upload (
    id uuid PRIMARY KEY,
    object_key text NOT NULL,
    file_name text NOT NULL,
    file_type text NOT NULL CHECK (file_type IN ('XLSX', 'CSV')),
    sheet_name text,
    row_count integer NOT NULL,
    columns jsonb NOT NULL DEFAULT '[]'::jsonb,
    status text NOT NULL DEFAULT 'UPLOADED'
        CHECK (status IN ('UPLOADED', 'COMMITTING', 'COMMITTED')),
    import_id uuid REFERENCES questionbank.question_import (id),
    expires_at timestamptz NOT NULL,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    committed_at timestamptz
);

-- Job dọn tệp hết hạn: chỉ quét các dòng còn chờ nhập (COMMITTING quá hạn tồn tại duy nhất
-- trong một transaction commit đang chạy; COMMITTED giữ lại để truy vết).
CREATE INDEX idx_question_import_upload_cleanup
    ON questionbank.question_import_upload (expires_at) WHERE status = 'UPLOADED';
