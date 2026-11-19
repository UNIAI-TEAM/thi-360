-- UNI-2754: "Bộ câu hỏi" is a row of the subject answer-type quota.
-- Until now the Toán templates declared SINGLE_CHOICE 35 / SHORT_ANSWER 15, where the 35 counted the 32 standalone
-- single-choice questions plus the 3 children of the question set. The quota now names the set: a subject whose
-- quota has a QUESTION_SET row counts the children of its set groups only there (32 / 15 / 3, still 50).
-- Only the six templates that combine answer quotas with a set group are rewritten. Existing exams keep their
-- stored 35 / 15: a subject without a QUESTION_SET row counts set children by their own type, as before.
-- Idempotent: only a Toán section whose quotas are exactly 35 / 15 and that has a QUESTION_SET group changes.
UPDATE exam.exam_template
SET structure = jsonb_set(
        structure,
        '{parts}',
        (
            SELECT jsonb_agg(
                    CASE
                        WHEN part ? 'sections'
                        THEN jsonb_set(
                            part,
                            '{sections}',
                            COALESCE((
                                SELECT jsonb_agg(
                                        CASE
                                            WHEN section ->> 'subjectCode' = 'TOAN'
                                                AND section -> 'answerQuotas' -> 'items' =
                                                    '[{"answerType":"SINGLE_CHOICE","value":35},{"answerType":"SHORT_ANSWER","value":15}]'::jsonb
                                                AND EXISTS (
                                                    SELECT 1
                                                    FROM jsonb_array_elements(section -> 'blocks') AS b(block)
                                                    WHERE block ->> 'questionType' = 'QUESTION_SET'
                                                )
                                            THEN jsonb_set(
                                                section,
                                                '{answerQuotas}',
                                                '{"unit":"COUNT","items":[{"answerType":"SINGLE_CHOICE","value":32},{"answerType":"SHORT_ANSWER","value":15},{"answerType":"QUESTION_SET","value":3}]}'::jsonb,
                                                true
                                            )
                                            ELSE section
                                        END
                                        ORDER BY ord
                                    )
                                FROM jsonb_array_elements(part -> 'sections')
                                    WITH ORDINALITY AS t(section, ord)
                            ), part -> 'sections'),
                            true
                        )
                        ELSE part
                    END
                    ORDER BY ord
                )
            FROM jsonb_array_elements(structure -> 'parts')
                WITH ORDINALITY AS t(part, ord)
        ),
        false)
WHERE code IN ('HSA_FULL', 'HSA_PART_MATH', 'HSA_SUBJECT_TOAN', 'QDA_FULL', 'QDA_PART_MATH', 'QDA_SUBJECT_TOAN');
