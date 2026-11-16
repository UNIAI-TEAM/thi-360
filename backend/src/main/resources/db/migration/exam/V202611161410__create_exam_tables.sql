CREATE SCHEMA IF NOT EXISTS exam;

-- BE-4 W-SCHEMA (UNI-2610): bảng của module exam theo spec mục 5.1 (B9/D21-D26).
-- exam_version, exam_check_run, exam_check_job do BE-5b/BE-6 tạo (ngoài phạm vi W-SCHEMA).
-- Không đặt FK sang schema catalog: bảng tham chiếu giá trị danh mục bằng uuid mà không join
-- qua schema khác (RULES mục 4).

-- Hồ sơ kỳ thi, khóa theo id giá trị nhóm EXAM của danh mục (D2).
CREATE TABLE exam.exam_kind_profile (
    exam_item_id uuid PRIMARY KEY,
    scopes text[] NOT NULL CHECK (scopes <@ '{FULL,PART,SUBJECT}'::text[]),
    template_by text NOT NULL CHECK (template_by IN ('KIND','SUBJECT')),
    timing text NOT NULL CHECK (timing IN ('PER_PART','SHARED')),
    max_score numeric(10,4),
    reference_max numeric(10,4),
    practice_scoring text NOT NULL DEFAULT 'RAW' CHECK (practice_scoring IN ('RAW','LINEAR')),
    tf_scoring jsonb,
    mc_rule text CHECK (mc_rule IS NULL OR mc_rule IN ('ALL_OR_NOTHING','PARTIAL')),
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    revision bigint NOT NULL DEFAULT 0
);

-- Mẫu cấu trúc đề, nạp bằng migration (D4); tham chiếu kỳ thi, môn, tổ hợp bằng mã.
CREATE TABLE exam.exam_template (
    code text PRIMARY KEY,
    kind_code text NOT NULL,
    scope text NOT NULL CHECK (scope IN ('FULL','PART','SUBJECT')),
    part_code text,
    subject_code text,
    structure_year integer,
    matrix_year integer,
    timing text NOT NULL CHECK (timing IN ('PER_PART','SHARED')),
    duration_minutes integer CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    max_score numeric(10,4),
    expert_note text,
    structure jsonb NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT chk_exam_template_scope_fields CHECK (
        (scope = 'FULL' AND part_code IS NULL AND subject_code IS NULL)
        OR (scope = 'PART' AND subject_code IS NULL)
        OR (scope = 'SUBJECT' AND subject_code IS NOT NULL)
    )
);

CREATE INDEX idx_exam_template_kind_scope
    ON exam.exam_template (kind_code, scope, part_code, subject_code);

-- Bài thi (D5, D13). Mã so không phân biệt hoa thường qua code_normalized.
CREATE TABLE exam.exam (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    code_normalized text NOT NULL,
    name text NOT NULL,
    description text,
    instructions text,
    kind_item_id uuid,
    scope text NOT NULL CHECK (scope IN ('FULL','PART','SUBJECT')),
    part_code text,
    subject_item_id uuid,
    template_code text,
    structure_year integer,
    matrix_year integer,
    years_confirmed boolean NOT NULL DEFAULT true,
    timing text NOT NULL CHECK (timing IN ('PER_PART','SHARED')),
    duration_minutes integer,
    max_score numeric(10,4),
    reference_max numeric(10,4),
    practice_scoring text NOT NULL DEFAULT 'RAW' CHECK (practice_scoring IN ('RAW','LINEAR')),
    status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','DISCONTINUED')),
    serving_version integer,
    check_status text NOT NULL DEFAULT 'UNCHECKED' CHECK (check_status IN ('UNCHECKED','VALID','INVALID','NEEDS_RECHECK')),
    check_issue_count integer NOT NULL DEFAULT 0,
    checked_at timestamptz,
    cover_asset_id uuid,
    has_unpublished_changes boolean NOT NULL DEFAULT false,
    question_count integer NOT NULL DEFAULT 0,
    has_essay boolean NOT NULL DEFAULT false,
    has_english_option boolean NOT NULL DEFAULT false,
    combination_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    revision bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_exam_code_normalized UNIQUE (code_normalized),
    CONSTRAINT chk_exam_scope_fields CHECK (
        (scope = 'FULL' AND part_code IS NULL AND subject_item_id IS NULL)
        OR (scope = 'PART' AND part_code IS NOT NULL)
        OR (scope = 'SUBJECT' AND subject_item_id IS NOT NULL)
    )
);

CREATE INDEX idx_exam_kind_status ON exam.exam (kind_item_id, status);
CREATE INDEX idx_exam_created_at ON exam.exam (created_at);

-- Bản làm việc của cấu trúc: một tài liệu jsonb ExamPart[] cho mỗi bài (D5).
CREATE TABLE exam.exam_structure (
    exam_id uuid PRIMARY KEY REFERENCES exam.exam (id) ON DELETE CASCADE,
    parts jsonb NOT NULL,
    structure_hash text,
    base_version integer,
    updated_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Tra cứu thô "nhóm này dùng môn/tổ hợp/kiến thức nào" cho nơi đang dùng và ảnh hưởng khi ngừng.
-- Một dòng cho mỗi nhóm (block). version_no NULL = bản làm việc. Ghi lại mỗi lần lưu/công bố.
-- knowledge_* là phạm vi CÓ HIỆU LỰC (của nhóm, hoặc của môn khi nhóm kế thừa). D23: không còn
-- include_descendants.
CREATE TABLE exam.exam_block_ref (
    id uuid PRIMARY KEY,
    exam_id uuid NOT NULL REFERENCES exam.exam (id) ON DELETE CASCADE,
    version_no integer,
    block_id text NOT NULL,
    part_id text NOT NULL,
    option_code text,
    section_id text,
    subject_item_id uuid,
    question_type text,
    answer_types text[],
    task_type_item_id uuid,
    knowledge_item_ids uuid[],
    knowledge_mode text CHECK (knowledge_mode IS NULL OR knowledge_mode IN ('ANY','ALL')),
    knowledge_inherited boolean NOT NULL DEFAULT false,
    tag_item_ids uuid[],
    competency_item_ids uuid[],
    required_points numeric(8,4),
    combination_item_id uuid
);

-- version_no NULL (bản làm việc) so NULL = NULL nên cần coalesce trong chỉ mục duy nhất.
CREATE UNIQUE INDEX uq_exam_block_ref_exam_version_block
    ON exam.exam_block_ref (exam_id, COALESCE(version_no, -1), block_id);

CREATE INDEX idx_exam_block_ref_subject ON exam.exam_block_ref (subject_item_id);
CREATE INDEX idx_exam_block_ref_task_type ON exam.exam_block_ref (task_type_item_id);
CREATE INDEX idx_exam_block_ref_combination ON exam.exam_block_ref (combination_item_id);
CREATE INDEX idx_exam_block_ref_knowledge ON exam.exam_block_ref USING gin (knowledge_item_ids);
CREATE INDEX idx_exam_block_ref_tags ON exam.exam_block_ref USING gin (tag_item_ids);
CREATE INDEX idx_exam_block_ref_competency ON exam.exam_block_ref USING gin (competency_item_ids);
