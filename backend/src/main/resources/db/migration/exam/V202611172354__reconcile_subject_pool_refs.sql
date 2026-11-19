-- UNI-2650: v2 codeSource parts share one copy of each subject's blocks.
-- Discard obsolete combination-specific projections and detach retained projections
-- from the old option and combination. Published versions retain their snapshots.
WITH pool_parts AS (
    SELECT s.exam_id, part
    FROM exam.exam_structure s
    CROSS JOIN LATERAL jsonb_array_elements(
        CASE WHEN jsonb_typeof(s.parts) = 'array' THEN s.parts ELSE s.parts->'parts' END
    ) part
    WHERE part->'codeSource' IS NOT NULL AND part->'codeSource' <> 'null'::jsonb
)
DELETE FROM exam.exam_block_ref r
USING pool_parts p
WHERE r.exam_id = p.exam_id AND r.version_no IS NULL AND r.part_id = p.part->>'id'
  AND (r.option_code IS NOT NULL OR r.combination_item_id IS NOT NULL)
  AND NOT EXISTS (
      SELECT 1 FROM jsonb_array_elements(COALESCE(p.part->'subjectPool', '[]'::jsonb)) section
      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(section->'blocks', '[]'::jsonb)) block
      WHERE section->>'id' = r.section_id AND block->>'id' = r.block_id
  );

WITH pool_parts AS (
    SELECT s.exam_id, part
    FROM exam.exam_structure s
    CROSS JOIN LATERAL jsonb_array_elements(
        CASE WHEN jsonb_typeof(s.parts) = 'array' THEN s.parts ELSE s.parts->'parts' END
    ) part
    WHERE part->'codeSource' IS NOT NULL AND part->'codeSource' <> 'null'::jsonb
)
UPDATE exam.exam_block_ref r
SET option_code = NULL, combination_item_id = NULL
FROM pool_parts p
WHERE r.exam_id = p.exam_id AND r.version_no IS NULL AND r.part_id = p.part->>'id'
  AND (r.option_code IS NOT NULL OR r.combination_item_id IS NOT NULL);
