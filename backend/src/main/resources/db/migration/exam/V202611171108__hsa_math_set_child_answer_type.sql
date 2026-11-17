-- BA 2026-11-17 (spec 12): câu con của chùm Toán HSA là Một đáp án.
-- Đặt childAnswerType = 'SINGLE_CHOICE' cho nhóm chùm HSA_MATH_CONTEXT của các
-- mẫu Toán HSA để màn ma trận chia sẵn chỉ tiêu 35/15 thay vì báo
-- "nhóm chưa xác định dạng".
UPDATE exam.exam_template
SET structure = jsonb_set(
        structure,
        '{parts}',
        (
            SELECT jsonb_agg(
                    jsonb_set(
                        part,
                        '{sections}',
                        COALESCE((
                            SELECT jsonb_agg(
                                    jsonb_set(
                                        section,
                                        '{blocks}',
                                        COALESCE((
                                            SELECT jsonb_agg(
                                                    CASE
                                                        WHEN block ->> 'templateGroupCode' = 'HSA_MATH_CONTEXT'
                                                        THEN jsonb_set(block, '{childAnswerType}', '"SINGLE_CHOICE"'::jsonb, true)
                                                        ELSE block
                                                    END
                                                    ORDER BY ord
                                                )
                                            FROM jsonb_array_elements(section -> 'blocks')
                                                WITH ORDINALITY AS t(block, ord)
                                        ), section -> 'blocks'),
                                        true
                                    )
                                    ORDER BY ord
                                )
                            FROM jsonb_array_elements(part -> 'sections')
                                WITH ORDINALITY AS t(section, ord)
                        ), part -> 'sections'),
                        true
                    )
                    ORDER BY ord
                )
            FROM jsonb_array_elements(structure -> 'parts')
                WITH ORDINALITY AS t(part, ord)
        ),
        false)
WHERE code IN ('HSA_FULL', 'HSA_PART_MATH');
