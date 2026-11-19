-- FU-BA-Q15: 50 prompts and ten points for each of these 2026 subjects.
-- Preserve expert_note; the per-prompt value is derived from the agreed totals.
WITH corrected AS (
    SELECT t.code,
           jsonb_set(t.structure, '{parts}', jsonb_agg(
               jsonb_set(
                   jsonb_set(part.value, '{maxPoints}',
                       to_jsonb(CASE WHEN t.code = 'SP2E_ANH_2026' THEN 10.0
                                     WHEN part.ordinality = 1 THEN 5.2 ELSE 4.8 END)),
                   '{sections}', (
                       SELECT jsonb_agg(jsonb_set(section.value, '{blocks}', (
                           SELECT jsonb_agg(
                               CASE WHEN block.value->>'questionType' = 'TRUE_FALSE'
                                   THEN jsonb_set(
                                       jsonb_set(block.value, '{requiredPointsPerQuestion}', '0.8'::jsonb),
                                       '{expectedScoring}', '{"trueFalseMethod":"PER_STATEMENT"}'::jsonb)
                                   ELSE jsonb_set(block.value, '{requiredPointsPerQuestion}', '0.2'::jsonb)
                               END ORDER BY block.ordinality)
                           FROM jsonb_array_elements(section.value->'blocks') WITH ORDINALITY AS block(value, ordinality)
                       )) ORDER BY section.ordinality)
                       FROM jsonb_array_elements(part.value->'sections') WITH ORDINALITY AS section(value, ordinality)
                   )
               ) ORDER BY part.ordinality)) AS structure
    FROM exam.exam_template t
    CROSS JOIN LATERAL jsonb_array_elements(t.structure->'parts') WITH ORDINALITY AS part(value, ordinality)
    WHERE t.code IN ('SP2E_ANH_2026', 'SP2E_SU_2026', 'SP2E_ECON_LAW_2026')
    GROUP BY t.code, t.structure
)
UPDATE exam.exam_template t
SET structure = corrected.structure
FROM corrected
WHERE t.code = corrected.code;
