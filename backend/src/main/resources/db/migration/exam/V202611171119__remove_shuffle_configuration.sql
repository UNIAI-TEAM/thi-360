-- Always-shuffle replaces per-question and template ordering settings. Preserve array order
-- and all unrelated JSON fields. Working structures whose hash changes require a new check.
CREATE FUNCTION pg_temp.remove_json_keys(value jsonb, removed text[]) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE jsonb_typeof(value)
        WHEN 'object' THEN COALESCE((
            SELECT jsonb_object_agg(key, pg_temp.remove_json_keys(child, removed))
            FROM jsonb_each(value) AS fields(key, child)
            WHERE NOT (key = ANY(removed))
        ), '{}'::jsonb)
        WHEN 'array' THEN COALESCE((
            SELECT jsonb_agg(pg_temp.remove_json_keys(child, removed) ORDER BY ordinal)
            FROM jsonb_array_elements(value) WITH ORDINALITY AS elements(child, ordinal)
        ), '[]'::jsonb)
        ELSE value
    END
$$;

UPDATE exam.exam_template
SET structure = pg_temp.remove_json_keys(structure, ARRAY['groupOrder', 'shuffle'])
WHERE structure IS DISTINCT FROM pg_temp.remove_json_keys(structure, ARRAY['groupOrder', 'shuffle']);

UPDATE exam.exam_structure
SET parts = pg_temp.remove_json_keys(parts, ARRAY['groupOrder', 'shuffle']), structure_hash = NULL
WHERE parts IS DISTINCT FROM pg_temp.remove_json_keys(parts, ARRAY['groupOrder', 'shuffle']);

UPDATE exam.exam_version
SET parts = pg_temp.remove_json_keys(parts, ARRAY['groupOrder', 'shuffle'])
WHERE parts IS DISTINCT FROM pg_temp.remove_json_keys(parts, ARRAY['groupOrder', 'shuffle']);

UPDATE questionbank.question_version
SET snapshot = pg_temp.remove_json_keys(snapshot, ARRAY['shuffleOptions'])
WHERE snapshot IS DISTINCT FROM pg_temp.remove_json_keys(snapshot, ARRAY['shuffleOptions']);
