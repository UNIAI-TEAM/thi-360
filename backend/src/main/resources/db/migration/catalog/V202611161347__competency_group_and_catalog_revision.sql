-- BE-1 (UNI-2607): nhóm hệ thống COMPETENCY (cây một cấp, spec D3) + bộ đếm revision toàn danh mục.

-- 1. Cho phép cây một cấp: nhóm chỉ tạo qua API vẫn yêu cầu >= 2 cấp (kiểm tra trong Java),
--    nhóm hệ thống khai báo bằng migration được phép một cấp.
ALTER TABLE catalog.catalog_group
    DROP CONSTRAINT chk_catalog_group_levels_kind;
ALTER TABLE catalog.catalog_group
    ADD CONSTRAINT chk_catalog_group_levels_kind CHECK (
        (kind = 3 AND levels IS NOT NULL AND jsonb_typeof(levels) = 'array' AND jsonb_array_length(levels) BETWEEN 1 AND 6)
        OR (kind <> 3 AND levels IS NULL)
    );

-- 2. Nhóm COMPETENCY: cây một cấp, liên kết mặc định tới SUBJECT và EXAM (D3).
INSERT INTO catalog.catalog_group
    (id, code, code_normalized, name_vi, name_en, description, status, type, kind, levels, is_system, last_value)
VALUES
    ('00000000-0000-7000-8100-00000000000a',
     'COMPETENCY', 'competency', 'Năng lực', 'Competency',
     'Danh mục năng lực (ví dụ V-ACT); mỗi giá trị gắn một môn và các kỳ thi', 1, 1, 3,
     '["Năng lực"]'::jsonb, true, 0)
ON CONFLICT (code_normalized) DO NOTHING;

INSERT INTO catalog.catalog_group_ref (group_id, ref_group_id, position, is_default)
VALUES
    ('00000000-0000-7000-8100-00000000000a', '00000000-0000-7000-8100-000000000002', 0, true),
    ('00000000-0000-7000-8100-00000000000a', '00000000-0000-7000-8100-000000000001', 1, true)
ON CONFLICT (group_id, ref_group_id) DO NOTHING;

-- 3. Bộ đếm revision: tăng một lần cho mỗi câu lệnh ghi vào bảng danh mục.
--    CatalogLookup.revision() đọc số này; dùng cho dấu vân tay đầu vào (spec 5.4) và cache.
CREATE TABLE catalog.catalog_revision (
    id smallint PRIMARY KEY CHECK (id = 1),
    revision bigint NOT NULL DEFAULT 0
);

INSERT INTO catalog.catalog_revision (id, revision) VALUES (1, 0)
ON CONFLICT (id) DO NOTHING;

CREATE OR REPLACE FUNCTION catalog.bump_catalog_revision()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE catalog.catalog_revision SET revision = revision + 1 WHERE id = 1;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_catalog_group_revision
    AFTER INSERT OR UPDATE OR DELETE ON catalog.catalog_group
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.bump_catalog_revision();

CREATE TRIGGER trg_catalog_item_revision
    AFTER INSERT OR UPDATE OR DELETE ON catalog.catalog_item
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.bump_catalog_revision();

CREATE TRIGGER trg_catalog_group_ref_revision
    AFTER INSERT OR UPDATE OR DELETE ON catalog.catalog_group_ref
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.bump_catalog_revision();

CREATE TRIGGER trg_catalog_item_ref_revision
    AFTER INSERT OR UPDATE OR DELETE ON catalog.catalog_item_ref
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.bump_catalog_revision();

CREATE TRIGGER trg_combination_version_revision
    AFTER INSERT OR UPDATE OR DELETE ON catalog.combination_version
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.bump_catalog_revision();

CREATE TRIGGER trg_combination_subject_revision
    AFTER INSERT OR UPDATE OR DELETE ON catalog.combination_subject
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.bump_catalog_revision();

CREATE TRIGGER trg_catalog_import_preview_revision
    AFTER INSERT OR UPDATE OR DELETE ON catalog.catalog_import_preview
    FOR EACH STATEMENT EXECUTE FUNCTION catalog.bump_catalog_revision();
