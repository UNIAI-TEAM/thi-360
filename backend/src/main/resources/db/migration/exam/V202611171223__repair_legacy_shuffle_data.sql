-- UNI-2634: repair rows written before the always-shuffle cutover.
-- Only dead shuffle/order keys are removed. No revision, source revision, or
-- structure hash is changed, so published/check state is not invalidated here.
CREATE OR REPLACE FUNCTION pg_temp.remove_legacy_shuffle_keys(value jsonb, removed text[]) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE jsonb_typeof(value)
        WHEN 'object' THEN COALESCE((
            SELECT jsonb_object_agg(key, pg_temp.remove_legacy_shuffle_keys(child, removed))
            FROM jsonb_each(value) AS fields(key, child)
            WHERE NOT (key = ANY(removed))
        ), '{}'::jsonb)
        WHEN 'array' THEN COALESCE((
            SELECT jsonb_agg(pg_temp.remove_legacy_shuffle_keys(child, removed) ORDER BY ordinal)
            FROM jsonb_array_elements(value) WITH ORDINALITY AS elements(child, ordinal)
        ), '[]'::jsonb)
        ELSE value
    END
$$;

CREATE OR REPLACE FUNCTION pg_temp.repair_hsa_math_child_answer_type(value jsonb) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE jsonb_typeof(value)
        WHEN 'object' THEN (
            SELECT CASE
                WHEN value ->> 'templateGroupCode' = 'HSA_MATH_CONTEXT'
                     AND value ->> 'childAnswerType' IS NULL
                THEN jsonb_set(object_value, '{childAnswerType}', to_jsonb('SINGLE_CHOICE'::text), true)
                ELSE object_value
            END
            FROM (
                SELECT COALESCE((
                    SELECT jsonb_object_agg(key, pg_temp.repair_hsa_math_child_answer_type(child))
                    FROM jsonb_each(value) AS fields(key, child)
                ), '{}'::jsonb) AS object_value
            ) AS rebuilt
        )
        WHEN 'array' THEN COALESCE((
            SELECT jsonb_agg(pg_temp.repair_hsa_math_child_answer_type(child) ORDER BY ordinal)
            FROM jsonb_array_elements(value) WITH ORDINALITY AS elements(child, ordinal)
        ), '[]'::jsonb)
        ELSE value
    END
$$;

-- Question bodies: all historical versions, including QUESTION_SET children.
UPDATE questionbank.question_version
SET snapshot = pg_temp.remove_legacy_shuffle_keys(
        snapshot, ARRAY['shuffleOptions', 'shuffleLeft', 'shuffleRight'])
WHERE snapshot IS DISTINCT FROM pg_temp.remove_legacy_shuffle_keys(
        snapshot, ARRAY['shuffleOptions', 'shuffleLeft', 'shuffleRight']);

-- Templates and working structures are raw JSON at their read boundaries.
UPDATE exam.exam_template
SET structure = pg_temp.repair_hsa_math_child_answer_type(
        pg_temp.remove_legacy_shuffle_keys(structure, ARRAY['groupOrder', 'shuffle', 'blockShuffleRule']))
WHERE structure IS DISTINCT FROM pg_temp.repair_hsa_math_child_answer_type(
        pg_temp.remove_legacy_shuffle_keys(structure, ARRAY['groupOrder', 'shuffle', 'blockShuffleRule']));

UPDATE exam.exam_structure
SET parts = pg_temp.repair_hsa_math_child_answer_type(
        pg_temp.remove_legacy_shuffle_keys(parts, ARRAY['groupOrder', 'shuffle', 'blockShuffleRule']))
WHERE parts IS DISTINCT FROM pg_temp.repair_hsa_math_child_answer_type(
        pg_temp.remove_legacy_shuffle_keys(parts, ARRAY['groupOrder', 'shuffle', 'blockShuffleRule']));

-- Published parts are returned as JsonNode by the version-detail endpoint, so
-- remove only the dead keys that would otherwise remain reachable there.
UPDATE exam.exam_version
SET parts = pg_temp.remove_legacy_shuffle_keys(
        parts, ARRAY['groupOrder', 'shuffle', 'blockShuffleRule'])
WHERE parts IS DISTINCT FROM pg_temp.remove_legacy_shuffle_keys(
        parts, ARRAY['groupOrder', 'shuffle', 'blockShuffleRule']);
