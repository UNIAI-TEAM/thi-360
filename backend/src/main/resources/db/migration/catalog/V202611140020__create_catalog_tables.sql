CREATE TABLE catalog.catalog_group (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    code_normalized text NOT NULL,
    name_vi text NOT NULL,
    name_en text,
    description text,
    status smallint NOT NULL DEFAULT 1 CHECK (status IN (1, 2)),
    type smallint NOT NULL DEFAULT 2 CHECK (type IN (1, 2)),
    kind smallint NOT NULL DEFAULT 1 CHECK (kind IN (1, 2, 3)),
    levels jsonb,
    is_system boolean NOT NULL DEFAULT false,
    last_value integer NOT NULL DEFAULT 0 CHECK (last_value >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_catalog_group_code_normalized UNIQUE (code_normalized),
    CONSTRAINT chk_catalog_group_type_system CHECK ((type = 1 AND is_system = true) OR (type = 2 AND is_system = false)),
    CONSTRAINT chk_catalog_group_levels_kind CHECK (
        (kind = 3 AND levels IS NOT NULL AND jsonb_typeof(levels) = 'array' AND jsonb_array_length(levels) BETWEEN 2 AND 6)
        OR (kind <> 3 AND levels IS NULL)
    )
);

CREATE TABLE catalog.catalog_item (
    id uuid PRIMARY KEY,
    group_id uuid NOT NULL REFERENCES catalog.catalog_group (id),
    code text NOT NULL,
    code_normalized text NOT NULL,
    value integer NOT NULL CHECK (value > 0),
    name_vi text NOT NULL,
    name_en text,
    description text,
    display_order integer NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    status smallint NOT NULL DEFAULT 1 CHECK (status IN (1, 2)),
    is_system boolean NOT NULL DEFAULT false,
    parent_id uuid REFERENCES catalog.catalog_item (id),
    depth smallint NOT NULL DEFAULT 1 CHECK (depth BETWEEN 1 AND 6),
    path text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_catalog_item_group_code_normalized UNIQUE (group_id, code_normalized),
    CONSTRAINT uq_catalog_item_group_value UNIQUE (group_id, value),
    CONSTRAINT chk_catalog_item_parent_depth CHECK (
        (parent_id IS NULL AND depth = 1)
        OR (parent_id IS NOT NULL AND depth BETWEEN 2 AND 6)
    )
);

CREATE INDEX idx_catalog_item_group_order
    ON catalog.catalog_item (group_id, display_order, code_normalized);

CREATE INDEX idx_catalog_item_parent_id
    ON catalog.catalog_item (parent_id);

CREATE INDEX idx_catalog_item_group_path
    ON catalog.catalog_item (group_id, path text_pattern_ops);

CREATE OR REPLACE FUNCTION catalog.enforce_catalog_item_tree_rules()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_group_kind smallint;
    v_parent_group_id uuid;
BEGIN
    SELECT kind INTO v_group_kind
    FROM catalog.catalog_group
    WHERE id = NEW.group_id;

    IF v_group_kind IS NULL THEN
        RAISE EXCEPTION 'Catalog group % not found', NEW.group_id;
    END IF;

    IF v_group_kind <> 3 THEN
        IF NEW.parent_id IS NOT NULL OR NEW.depth <> 1 THEN
            RAISE EXCEPTION 'Non-TREE catalog group cannot have parent_id or depth <> 1';
        END IF;
    ELSIF NEW.parent_id IS NOT NULL THEN
        SELECT group_id INTO v_parent_group_id
        FROM catalog.catalog_item
        WHERE id = NEW.parent_id;

        IF v_parent_group_id IS DISTINCT FROM NEW.group_id THEN
            RAISE EXCEPTION 'Catalog item parent must belong to the same group';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_catalog_item_tree_rules
    BEFORE INSERT OR UPDATE OF group_id, parent_id, depth
    ON catalog.catalog_item
    FOR EACH ROW
    EXECUTE FUNCTION catalog.enforce_catalog_item_tree_rules();

CREATE TABLE catalog.catalog_group_ref (
    group_id uuid NOT NULL REFERENCES catalog.catalog_group (id) ON DELETE CASCADE,
    ref_group_id uuid NOT NULL REFERENCES catalog.catalog_group (id),
    position integer NOT NULL DEFAULT 0 CHECK (position >= 0),
    is_default boolean NOT NULL DEFAULT false,
    PRIMARY KEY (group_id, ref_group_id),
    CONSTRAINT chk_catalog_group_ref_not_self CHECK (group_id <> ref_group_id)
);

CREATE INDEX idx_catalog_group_ref_ref_group_id
    ON catalog.catalog_group_ref (ref_group_id);

CREATE TABLE catalog.catalog_item_ref (
    item_id uuid NOT NULL REFERENCES catalog.catalog_item (id) ON DELETE CASCADE,
    ref_item_id uuid NOT NULL REFERENCES catalog.catalog_item (id) ON DELETE CASCADE,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (item_id, ref_item_id)
);

CREATE INDEX idx_catalog_item_ref_ref_item_id
    ON catalog.catalog_item_ref (ref_item_id);

INSERT INTO catalog.catalog_group (id, code, code_normalized, name_vi, name_en, description, status, type, kind, levels, is_system, last_value)
VALUES
    ('00000000-0000-7000-8100-000000000001', 'EXAM', 'exam', 'Kỳ thi', 'Exam', 'Danh mục các kỳ thi trong hệ thống', 1, 1, 1, NULL, true, 0),
    ('00000000-0000-7000-8100-000000000002', 'SUBJECT', 'subject', 'Môn học', 'Subject', 'Danh mục các môn học', 1, 1, 1, NULL, true, 0),
    ('00000000-0000-7000-8100-000000000003', 'GRADE', 'grade', 'Khối lớp', 'Grade', 'Danh mục các khối lớp kiến thức', 1, 1, 1, NULL, true, 0),
    ('00000000-0000-7000-8100-000000000004', 'QUESTION_LEVEL', 'question_level', 'Mức độ câu hỏi', 'Question level', 'Danh mục mức độ nhận thức câu hỏi', 1, 1, 1, NULL, true, 0),
    ('00000000-0000-7000-8100-000000000005', 'QUESTION_FORMAT', 'question_format', 'Dạng câu', 'Question format', 'Danh mục dạng câu hỏi', 1, 1, 1, NULL, true, 0),
    ('00000000-0000-7000-8100-000000000006', 'TASK_TYPE', 'task_type', 'Dạng bài', 'Task type', 'Danh mục dạng bài thi', 1, 1, 1, NULL, true, 0),
    ('00000000-0000-7000-8100-000000000007', 'SUBJECT_COMBINATION', 'subject_combination', 'Mã tổ hợp', 'Subject combination', 'Danh mục mã tổ hợp môn theo kỳ thi', 1, 1, 2, NULL, true, 0),
    ('00000000-0000-7000-8100-000000000008', 'QUESTION_TAG', 'question_tag', 'Nhãn câu hỏi', 'Question tag', 'Danh mục nhãn phân loại câu hỏi', 1, 1, 1, NULL, true, 0),
    ('00000000-0000-7000-8100-000000000009', 'KNOWLEDGE', 'knowledge', 'Cấu trúc kiến thức', 'Knowledge', 'Cây cấu trúc kiến thức theo Chương, Bài, Kiến thức', 1, 1, 3, '["Chương", "Bài", "Kiến thức"]'::jsonb, true, 0)
ON CONFLICT (code_normalized) DO NOTHING;

INSERT INTO catalog.catalog_group_ref (group_id, ref_group_id, position, is_default)
VALUES
    ('00000000-0000-7000-8100-000000000009', '00000000-0000-7000-8100-000000000002', 0, true),
    ('00000000-0000-7000-8100-000000000009', '00000000-0000-7000-8100-000000000003', 1, true)
ON CONFLICT (group_id, ref_group_id) DO NOTHING;
