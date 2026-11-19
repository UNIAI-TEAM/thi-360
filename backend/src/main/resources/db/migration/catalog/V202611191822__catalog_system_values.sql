-- UNI-2759 (AD-34, AD-35): the 52 values the six fixed groups hold today become system values.
-- Groups: EXAM, SUBJECT, GRADE, QUESTION_LEVEL, QUESTION_FORMAT, TASK_TYPE. Codes and `value` are the
-- basis for per-subject forms, so an admin may rename a system value (nameVi, nameEn) and reorder it,
-- but the API rejects any change of code, value, status, position, and delete (CATALOG_ITEM_SYSTEM).
--
-- Matching rule, per listed value (names an admin changed are never overwritten):
--   (a) same (group, code_normalized)        -> is_system = true.
--   (b) else same id under a renamed code    -> no usage: restore the canonical code and set is_system = true;
--                                               in use (same check as a code change through the API): keep the
--                                               current code, set is_system = true and RAISE NOTICE it.
--   (c) else                                 -> INSERT with the listed id, code, names, order and value
--                                               (value = max + 1 of the group when the listed one is taken).
-- Re-running the script changes nothing: every row lands in (a).
-- Revision counter: the statement triggers of catalog.catalog_item bump catalog.catalog_revision.
DO $migration$
DECLARE
    r record;
    existing_id uuid;
    existing_group uuid;
    existing_code text;
    in_use boolean;
    new_value integer;
BEGIN
    FOR r IN
        SELECT *
        FROM (VALUES
            -- EXAM
            ('00000000-0000-7000-8100-000000000001'::uuid, 'EXAM', 'HSA', 1, '00000000-0000-7000-8200-000000000101'::uuid, 'Đánh giá năng lực ĐHQG Hà Nội (HSA)', 'VNU Hanoi Competency Assessment (HSA)', 0),
            ('00000000-0000-7000-8100-000000000001'::uuid, 'EXAM', 'QDA', 2, '00000000-0000-7000-8200-000000000102'::uuid, 'Đánh giá năng lực Bộ Quốc phòng (QDA)', 'Ministry of Defence Competency Assessment (QDA)', 1),
            ('00000000-0000-7000-8100-000000000001'::uuid, 'EXAM', 'BCA', 3, '00000000-0000-7000-8400-000000000103'::uuid, 'Đánh giá tuyển sinh Công an nhân dân (BCA)', 'People''s Public Security admission assessment (BCA)', 2),
            ('00000000-0000-7000-8100-000000000001'::uuid, 'EXAM', 'VACT', 4, '00000000-0000-7000-8400-000000000104'::uuid, 'Đánh giá năng lực ĐHQG TP.HCM (V-ACT)', 'VNU-HCM Competency Assessment (V-ACT)', 3),
            ('00000000-0000-7000-8100-000000000001'::uuid, 'EXAM', 'SPT', 5, '00000000-0000-7000-8400-000000000105'::uuid, 'Đánh giá năng lực ĐH Sư phạm Hà Nội (SPT)', 'HNUE competency assessment (SPT)', 4),
            ('00000000-0000-7000-8100-000000000001'::uuid, 'EXAM', 'SP2E', 6, '00000000-0000-7000-8400-000000000106'::uuid, 'Đánh giá năng lực ĐH Sư phạm Hà Nội 2 (SP2E)', 'HNUE2 competency assessment (SP2E)', 5),
            ('00000000-0000-7000-8100-000000000001'::uuid, 'EXAM', 'THPTQG', 7, '00000000-0000-7000-8400-000000000107'::uuid, 'Tốt nghiệp THPT (THPTQG)', 'National high school graduation exam (THPTQG)', 6),
            ('00000000-0000-7000-8100-000000000001'::uuid, 'EXAM', 'HSCA', 8, '00000000-0000-7000-8400-000000000108'::uuid, 'Đánh giá năng lực H-SCA', 'H-SCA assessment', 7),
            ('00000000-0000-7000-8100-000000000001'::uuid, 'EXAM', 'VSAT', 9, '00000000-0000-7000-8400-000000000109'::uuid, 'Đánh giá năng lực V-SAT', 'V-SAT assessment', 8),
            -- SUBJECT (TIN/INFORMATICS, GDKTPL/ECON_LAW, CNCN/TECH_INDUSTRY, CNNN/TECH_AGRICULTURE stay as listed twins, AD-35)
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'TOAN', 1, '00000000-0000-7000-8200-000000000201'::uuid, 'Toán học', 'Mathematics', 0),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'VAN', 2, '00000000-0000-7000-8200-000000000202'::uuid, 'Ngữ văn', 'Literature', 1),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'ANH', 3, '00000000-0000-7000-8200-000000000203'::uuid, 'Tiếng Anh', 'English', 2),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'LY', 4, '00000000-0000-7000-8200-000000000204'::uuid, 'Vật lí', 'Physics', 3),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'HOA', 5, '00000000-0000-7000-8200-000000000205'::uuid, 'Hóa học', 'Chemistry', 4),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'SINH', 6, '00000000-0000-7000-8200-000000000206'::uuid, 'Sinh học', 'Biology', 5),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'SU', 7, '00000000-0000-7000-8200-000000000207'::uuid, 'Lịch sử', 'History', 6),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'DIA', 8, '00000000-0000-7000-8200-000000000208'::uuid, 'Địa lí', 'Geography', 7),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'TIN', 9, '00000000-0000-7000-8200-000000000209'::uuid, 'Tin học', 'Informatics', 8),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'GDKTPL', 10, '00000000-0000-7000-8200-000000000210'::uuid, 'Giáo dục kinh tế và pháp luật', 'Economic and Legal Education', 9),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'CNCN', 11, '00000000-0000-7000-8200-000000000211'::uuid, 'Công nghệ (định hướng công nghiệp)', 'Technology (industrial)', 10),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'CNNN', 12, '00000000-0000-7000-8200-000000000212'::uuid, 'Công nghệ (định hướng nông nghiệp)', 'Technology (agricultural)', 11),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'VIETNAMESE', 13, '00000000-0000-7000-8400-000000000213'::uuid, 'Tiếng Việt', 'Vietnamese', 12),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'ECON_LAW', 14, '00000000-0000-7000-8400-000000000214'::uuid, 'Giáo dục kinh tế và pháp luật', 'Economic and Legal Education', 13),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'INFORMATICS', 15, '00000000-0000-7000-8400-000000000215'::uuid, 'Tin học', 'Informatics', 14),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'TECH_INDUSTRY', 16, '00000000-0000-7000-8400-000000000216'::uuid, 'Công nghệ (định hướng công nghiệp)', 'Technology (industrial)', 15),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'TECH_AGRICULTURE', 17, '00000000-0000-7000-8400-000000000217'::uuid, 'Công nghệ (định hướng nông nghiệp)', 'Technology (agricultural)', 16),
            ('00000000-0000-7000-8100-000000000002'::uuid, 'SUBJECT', 'SCIENTIFIC_THINKING', 18, '00000000-0000-7000-8400-000000000218'::uuid, 'Tư duy khoa học', 'Scientific thinking', 17),
            -- GRADE
            ('00000000-0000-7000-8100-000000000003'::uuid, 'GRADE', 'G10', 1, '00000000-0000-7000-8200-000000000301'::uuid, 'Lớp 10', 'Grade 10', 0),
            ('00000000-0000-7000-8100-000000000003'::uuid, 'GRADE', 'G11', 2, '00000000-0000-7000-8200-000000000302'::uuid, 'Lớp 11', 'Grade 11', 1),
            ('00000000-0000-7000-8100-000000000003'::uuid, 'GRADE', 'G12', 3, '00000000-0000-7000-8200-000000000303'::uuid, 'Lớp 12', 'Grade 12', 2),
            ('00000000-0000-7000-8100-000000000003'::uuid, 'GRADE', 'GRADE_MIXED', 4, '00000000-0000-7000-8400-000000000304'::uuid, 'Liên lớp', 'Mixed grade', 3),
            -- QUESTION_LEVEL
            ('00000000-0000-7000-8100-000000000004'::uuid, 'QUESTION_LEVEL', 'NB', 1, '00000000-0000-7000-8200-000000000401'::uuid, 'Nhận biết', 'Remembering', 0),
            ('00000000-0000-7000-8100-000000000004'::uuid, 'QUESTION_LEVEL', 'TH', 2, '00000000-0000-7000-8200-000000000402'::uuid, 'Thông hiểu', 'Understanding', 1),
            ('00000000-0000-7000-8100-000000000004'::uuid, 'QUESTION_LEVEL', 'VD', 3, '00000000-0000-7000-8200-000000000403'::uuid, 'Vận dụng', 'Applying', 2),
            ('00000000-0000-7000-8100-000000000004'::uuid, 'QUESTION_LEVEL', 'VDC', 4, '00000000-0000-7000-8200-000000000404'::uuid, 'Vận dụng cao', 'High-level Applying', 3),
            -- QUESTION_FORMAT
            ('00000000-0000-7000-8100-000000000005'::uuid, 'QUESTION_FORMAT', 'SINGLE_CHOICE', 1, '00000000-0000-7000-8200-000000000501'::uuid, 'Trắc nghiệm một đáp án', 'Single choice', 0),
            ('00000000-0000-7000-8100-000000000005'::uuid, 'QUESTION_FORMAT', 'SHORT_ANSWER', 2, '00000000-0000-7000-8200-000000000502'::uuid, 'Trả lời ngắn', 'Short answer', 1),
            ('00000000-0000-7000-8100-000000000005'::uuid, 'QUESTION_FORMAT', 'MULTIPLE_CHOICE', 3, '00000000-0000-7000-8400-000000000503'::uuid, 'Trắc nghiệm nhiều đáp án', 'Multiple choice', 2),
            ('00000000-0000-7000-8100-000000000005'::uuid, 'QUESTION_FORMAT', 'CLOZE', 4, '00000000-0000-7000-8400-000000000504'::uuid, 'Điền khuyết', 'Cloze', 3),
            ('00000000-0000-7000-8100-000000000005'::uuid, 'QUESTION_FORMAT', 'QUESTION_SET', 5, '00000000-0000-7000-8400-000000000505'::uuid, 'Bộ câu hỏi', 'Question set', 4),
            ('00000000-0000-7000-8100-000000000005'::uuid, 'QUESTION_FORMAT', 'TRUE_FALSE', 6, '00000000-0000-7000-8400-000000000506'::uuid, 'Đúng/Sai', 'True/False', 5),
            ('00000000-0000-7000-8100-000000000005'::uuid, 'QUESTION_FORMAT', 'MATCHING', 7, '00000000-0000-7000-8400-000000000507'::uuid, 'Ghép đôi', 'Matching', 6),
            ('00000000-0000-7000-8100-000000000005'::uuid, 'QUESTION_FORMAT', 'ESSAY', 8, '00000000-0000-7000-8400-000000000508'::uuid, 'Tự luận', 'Essay', 7),
            -- TASK_TYPE
            ('00000000-0000-7000-8100-000000000006'::uuid, 'TASK_TYPE', 'SINGLE', 1, '00000000-0000-7000-8200-000000000601'::uuid, 'Câu đơn', 'Single question', 0),
            ('00000000-0000-7000-8100-000000000006'::uuid, 'TASK_TYPE', 'CLUSTER', 2, '00000000-0000-7000-8200-000000000602'::uuid, 'Câu chùm', 'Question cluster', 1),
            ('00000000-0000-7000-8100-000000000006'::uuid, 'TASK_TYPE', 'SYNONYM', 3, '00000000-0000-7000-8600-000000000601'::uuid, 'Đồng nghĩa', 'Synonym', 2),
            ('00000000-0000-7000-8100-000000000006'::uuid, 'TASK_TYPE', 'ANTONYM', 4, '00000000-0000-7000-8600-000000000602'::uuid, 'Trái nghĩa', 'Antonym', 3),
            ('00000000-0000-7000-8100-000000000006'::uuid, 'TASK_TYPE', 'DIALOGUE_COMPLETION', 5, '00000000-0000-7000-8600-000000000603'::uuid, 'Hoàn thành hội thoại', 'Dialogue completion', 4),
            ('00000000-0000-7000-8100-000000000006'::uuid, 'TASK_TYPE', 'READING_COMPREHENSION', 6, '00000000-0000-7000-8600-000000000604'::uuid, 'Đọc hiểu', 'Reading comprehension', 5),
            ('00000000-0000-7000-8100-000000000006'::uuid, 'TASK_TYPE', 'WORD_USAGE_ERROR', 7, '00000000-0000-7000-8600-000000000605'::uuid, 'Nhận diện lỗi dùng từ', 'Word usage error', 6),
            ('00000000-0000-7000-8100-000000000006'::uuid, 'TASK_TYPE', 'PASSAGE_READING', 8, '00000000-0000-7000-8600-000000000606'::uuid, 'Đọc ngữ liệu', 'Passage reading', 7),
            ('00000000-0000-7000-8100-000000000006'::uuid, 'TASK_TYPE', 'SENTENCE_COMPLETION', 9, '5b100b3c-4336-51b7-8277-3cc4223367ae'::uuid, 'Hoàn thành câu', 'Sentence completion', 8)
        ) AS s(group_id, group_code, code, item_value, id, name_vi, name_en, display_order)
        ORDER BY s.group_id, s.item_value
    LOOP
        -- (a) the code is already there: keep id, names and value, only mark it system.
        existing_id := NULL;
        SELECT i.id INTO existing_id
        FROM catalog.catalog_item i
        WHERE i.group_id = r.group_id AND i.code_normalized = lower(r.code);
        IF existing_id IS NOT NULL THEN
            UPDATE catalog.catalog_item
            SET is_system = true, version = version + 1, updated_at = now()
            WHERE id = existing_id AND NOT is_system;
            CONTINUE;
        END IF;

        -- (b) the canonical id exists under another code (renamed through PATCH on that environment).
        existing_group := NULL;
        SELECT i.group_id, i.code INTO existing_group, existing_code
        FROM catalog.catalog_item i
        WHERE i.id = r.id;
        IF existing_group IS NOT NULL THEN
            IF existing_group <> r.group_id THEN
                RAISE EXCEPTION 'catalog item % belongs to group %, expected % (%)',
                    r.id, existing_group, r.group_id, r.group_code;
            END IF;

            -- Same sources the API asks before a code change: CatalogUsageProvider of catalog (tree refs,
            -- combinations), exam and questionbank.
            in_use := EXISTS (SELECT 1 FROM catalog.catalog_item_ref x WHERE x.ref_item_id = r.id)
                OR (r.group_code = 'EXAM' AND (
                        EXISTS (SELECT 1 FROM exam.exam e WHERE e.kind_item_id = r.id)
                        OR EXISTS (SELECT 1 FROM catalog.combination_version cv WHERE cv.exam_item_id = r.id)))
                OR (r.group_code = 'SUBJECT' AND (
                        EXISTS (SELECT 1 FROM exam.exam e WHERE e.subject_item_id = r.id)
                        OR EXISTS (SELECT 1 FROM exam.exam_block_ref b WHERE b.subject_item_id = r.id)
                        OR EXISTS (SELECT 1 FROM catalog.combination_subject cs WHERE cs.subject_item_id = r.id)))
                OR EXISTS (
                        SELECT 1 FROM questionbank.question q
                        WHERE q.deleted_at IS NULL
                          AND r.id = CASE r.group_code
                                  WHEN 'SUBJECT' THEN q.subject_item_id
                                  WHEN 'GRADE' THEN q.grade_item_id
                                  WHEN 'QUESTION_LEVEL' THEN q.level_item_id
                                  WHEN 'TASK_TYPE' THEN q.task_type_item_id
                              END);

            IF in_use THEN
                UPDATE catalog.catalog_item
                SET is_system = true, version = version + 1, updated_at = now()
                WHERE id = r.id;
                RAISE NOTICE 'catalog system value kept under renamed code (in use): group=%, id=%, canonical code=%, current code=%',
                    r.group_code, r.id, r.code, existing_code;
            ELSE
                UPDATE catalog.catalog_item
                SET code = r.code, code_normalized = lower(r.code), is_system = true,
                    version = version + 1, updated_at = now()
                WHERE id = r.id;
                RAISE NOTICE 'catalog system value code restored: group=%, id=%, code % -> %',
                    r.group_code, r.id, existing_code, r.code;
            END IF;
            CONTINUE;
        END IF;

        -- (c) missing: insert with the listed id and code; value moves to max + 1 when it is taken.
        new_value := r.item_value;
        IF EXISTS (SELECT 1 FROM catalog.catalog_item i WHERE i.group_id = r.group_id AND i.value = new_value) THEN
            SELECT GREATEST(g.last_value, COALESCE((SELECT MAX(i.value) FROM catalog.catalog_item i WHERE i.group_id = g.id), 0)) + 1
            INTO new_value
            FROM catalog.catalog_group g
            WHERE g.id = r.group_id;
            RAISE NOTICE 'catalog system value % % takes value % (listed value % is taken)',
                r.group_code, r.code, new_value, r.item_value;
        END IF;
        INSERT INTO catalog.catalog_item
            (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order,
             status, is_system, parent_id, depth, path)
        VALUES
            (r.id, r.group_id, r.code, lower(r.code), new_value, r.name_vi, r.name_en, NULL, r.display_order,
             1, true, NULL, 1, '/' || r.id::text || '/');
    END LOOP;

    -- New API-created values must never collide with the values above.
    UPDATE catalog.catalog_group g
    SET last_value = m.max_value
    FROM (
        SELECT i.group_id, MAX(i.value) AS max_value
        FROM catalog.catalog_item i
        WHERE i.group_id IN (
            '00000000-0000-7000-8100-000000000001', '00000000-0000-7000-8100-000000000002',
            '00000000-0000-7000-8100-000000000003', '00000000-0000-7000-8100-000000000004',
            '00000000-0000-7000-8100-000000000005', '00000000-0000-7000-8100-000000000006')
        GROUP BY i.group_id
    ) m
    WHERE g.id = m.group_id AND g.last_value < m.max_value;
END
$migration$;
