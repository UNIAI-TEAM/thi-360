-- BE-2a (UNI-2609): bảng ngân hàng câu hỏi theo spec 4.1.
-- Câu con của bộ câu hỏi là dòng `question` riêng (parent_id + position), có phân loại riêng
-- (khối lớp, độ khó, MAPID) và phiên bản riêng trong `question_version` — đơn vị đếm tham
-- chiếu bằng id câu con (D22, sửa theo chỉ đạo của lead 2026-11-16).
-- `question_asset*` và `question_import*` do BE-2b / BE-3 tạo.

CREATE SCHEMA IF NOT EXISTS questionbank;

CREATE TABLE questionbank.question (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    type text NOT NULL CHECK (type IN (
        'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'SHORT_ANSWER', 'CLOZE',
        'QUESTION_SET', 'TRUE_FALSE', 'MATCHING', 'ESSAY')),
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'READY', 'RETIRED')),
    parent_id uuid REFERENCES questionbank.question (id),
    position integer,
    subject_item_id uuid,
    grade_item_id uuid,
    level_item_id uuid,
    task_type_item_id uuid,
    competency_item_id uuid,
    points numeric(8, 4) NOT NULL DEFAULT 1 CHECK (points > 0),
    current_version_no integer,
    working_version_no integer,
    source_code text,
    source_code_normalized text,
    content_text text NOT NULL DEFAULT '',
    search_text text NOT NULL DEFAULT '',
    has_solution boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    revision bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz,
    CONSTRAINT uq_question_code UNIQUE (code),
    CONSTRAINT chk_question_parent_position CHECK (
        (parent_id IS NULL AND position IS NULL)
        OR (parent_id IS NOT NULL AND position IS NOT NULL AND position >= 0)),
    CONSTRAINT chk_question_current_version CHECK (current_version_no IS NULL OR current_version_no > 0),
    CONSTRAINT chk_question_working_version CHECK (working_version_no IS NULL OR working_version_no > 0)
);

-- Chỉ câu độc lập (parent_id IS NULL) tham gia chính sách trùng mã nguồn của nhập tệp;
-- source_code_normalized = bỏ khoảng trắng đầu cuối, viết hoa (spec 4.1, 4.5).
CREATE UNIQUE INDEX uq_question_source_code_normalized
    ON questionbank.question (source_code_normalized)
    WHERE deleted_at IS NULL AND parent_id IS NULL AND source_code_normalized IS NOT NULL;

CREATE INDEX idx_question_status_subject_type
    ON questionbank.question (status, subject_item_id, type);

CREATE INDEX idx_question_created_at
    ON questionbank.question (created_at);

CREATE INDEX idx_question_parent_position
    ON questionbank.question (parent_id, position);

-- Tìm kiếm ILIKE '%…%' bỏ dấu trên cột đã chuẩn hóa (D16).
CREATE INDEX idx_question_search_text_gin
    ON questionbank.question USING gin (search_text gin_trgm_ops);

-- snapshot = QuestionSnapshot: phân loại (id giá trị danh mục), điểm, nội dung, body,
-- lời giải, lỗi sai thường gặp, tệp đính kèm; với câu QUESTION_SET body mang
-- children[] lồng (id câu con + phân loại ghi đè). Câu con có dòng riêng với
-- version_no khớp version_no của bộ (spec 4.1, 4.4).
CREATE TABLE questionbank.question_version (
    question_id uuid NOT NULL REFERENCES questionbank.question (id),
    version_no integer NOT NULL,
    snapshot jsonb NOT NULL,
    saved_by uuid,
    saved_at timestamptz NOT NULL DEFAULT now(),
    ready_at timestamptz,
    PRIMARY KEY (question_id, version_no),
    CONSTRAINT chk_question_version_no CHECK (version_no > 0)
);

CREATE TABLE questionbank.question_knowledge (
    question_id uuid NOT NULL REFERENCES questionbank.question (id),
    knowledge_item_id uuid NOT NULL,
    is_primary boolean NOT NULL DEFAULT false,
    PRIMARY KEY (question_id, knowledge_item_id)
);

-- Đúng một dòng chính khi có ít nhất một dòng (spec 4.1).
CREATE UNIQUE INDEX uq_question_knowledge_primary
    ON questionbank.question_knowledge (question_id) WHERE is_primary;

CREATE INDEX idx_question_knowledge_item
    ON questionbank.question_knowledge (knowledge_item_id);

CREATE TABLE questionbank.question_tag (
    question_id uuid NOT NULL REFERENCES questionbank.question (id),
    tag_item_id uuid NOT NULL,
    PRIMARY KEY (question_id, tag_item_id)
);

CREATE INDEX idx_question_tag_item
    ON questionbank.question_tag (tag_item_id);

-- Revision nguồn câu theo môn (spec 4.1, D14): tăng trong cùng transaction mỗi khi tập
-- câu Sẵn sàng của môn đổi. Đọc/ghi qua JdbcTemplate, không có entity.
CREATE TABLE questionbank.question_source_revision (
    subject_item_id uuid PRIMARY KEY,
    revision bigint NOT NULL DEFAULT 0
);

-- Sổ câu đã dùng (D18): một dòng cho mỗi bộ (câu, phiên bản, loại, bài). BE-5b/BE-6 ghi;
-- BE-2a chỉ đọc để chặn xóa và hiện màn nơi đang dùng.
CREATE TABLE questionbank.question_usage_ledger (
    question_id uuid NOT NULL REFERENCES questionbank.question (id),
    version_no integer NOT NULL,
    kind text NOT NULL CHECK (kind IN ('PREVIEW', 'PUBLISH_WITNESS', 'ATTEMPT')),
    exam_id uuid NOT NULL,
    first_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (question_id, version_no, kind, exam_id)
);

CREATE INDEX idx_question_usage_ledger_exam
    ON questionbank.question_usage_ledger (exam_id);

-- Mã câu CH-000123: bộ đếm toàn cục của module.
CREATE SEQUENCE questionbank.question_code_seq START WITH 1;
