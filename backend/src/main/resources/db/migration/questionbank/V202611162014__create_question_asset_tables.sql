-- BE-2b W-STORE (UNI-2614): tài nguyên câu hỏi (ảnh, tệp đính kèm) theo spec 4.1.
-- `question_asset` giữ siêu dữ liệu + khóa object storage (không lộ ra API); nội dung tệp
-- nằm trong bucket MinIO riêng tư với tiền tố khóa `questions/` (quyết định lane 8.2).
-- `question_asset_ref` ghi tài nguyên mà từng phiên bản câu dùng; FK tới `question_asset`
-- chặn tham chiếu lơ lửng khi worker dọn tệp mồ côi (spec 4.1, plan D/BE-2b).

CREATE TABLE questionbank.question_asset (
    id uuid PRIMARY KEY,
    kind text NOT NULL CHECK (kind IN ('IMAGE', 'ATTACHMENT')),
    object_key text NOT NULL,
    file_name text NOT NULL,
    mime text NOT NULL,
    size bigint NOT NULL CHECK (size >= 0),
    sha256 bytea NOT NULL,
    width integer,
    height integer,
    uploaded_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_question_asset_object_key UNIQUE (object_key)
);

-- Tài nguyên mà từng phiên bản câu dùng (ghi khi lưu phiên bản, cùng transaction).
-- Không FK tới question_version: worker dọn chỉ cần FK tới asset; quan hệ phiên bản giữ
-- lỏng theo spec 4.1 (cột asset_id, question_id, version_no).
CREATE TABLE questionbank.question_asset_ref (
    asset_id uuid NOT NULL REFERENCES questionbank.question_asset (id),
    question_id uuid NOT NULL REFERENCES questionbank.question (id),
    version_no integer NOT NULL,
    PRIMARY KEY (asset_id, question_id, version_no),
    CONSTRAINT chk_question_asset_ref_version CHECK (version_no > 0)
);

-- Tra cứu ngược: tài nguyên của một phiên bản (xóa/ghi lại khi lưu tại chỗ).
CREATE INDEX idx_question_asset_ref_version
    ON questionbank.question_asset_ref (question_id, version_no);
