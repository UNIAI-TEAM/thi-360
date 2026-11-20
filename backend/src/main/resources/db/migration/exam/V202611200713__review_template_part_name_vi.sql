-- UNI-2700 (Advisor extra item 8, HSA-B Q4): the single part of every review template was named "Review" (English).
-- Exam content is not bilingual, so the template data carries the Vietnamese name. Templates only: parts of exams
-- that already loaded the template keep their own name.
UPDATE exam.exam_template
SET structure = jsonb_set(structure, '{parts}', (
    SELECT jsonb_agg(CASE WHEN part->>'name' = 'Review' THEN jsonb_set(part, '{name}', to_jsonb('Bài ôn'::text)) ELSE part END
                     ORDER BY position)
    FROM jsonb_array_elements(structure->'parts') WITH ORDINALITY AS p(part, position)))
WHERE build_mode = 'REVIEW'
  AND jsonb_typeof(structure->'parts') = 'array'
  AND EXISTS (SELECT 1 FROM jsonb_array_elements(structure->'parts') AS p(part) WHERE part->>'name' = 'Review');
