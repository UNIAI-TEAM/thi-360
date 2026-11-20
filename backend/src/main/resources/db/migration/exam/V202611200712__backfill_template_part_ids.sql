-- UNI-2700 (FU-BE6-3): templates that still carry a literal part id (the *_REVIEW_PART templates of the review kinds)
-- get a UUID part id, like every other template part since V202611180455/V202611180652.
-- The UUID is derived from the template code and the old id, so every environment ends with the same id.
-- Templates only: an exam that loaded a template owns a copy of its structure; its part ids, block references and
-- published versions are not touched.
DO $$
DECLARE
    template record;
    parts jsonb;
    part jsonb;
    part_index integer;
    changed boolean;
BEGIN
    FOR template IN SELECT code, structure FROM exam.exam_template LOOP
        parts := template.structure->'parts';
        IF jsonb_typeof(parts) IS DISTINCT FROM 'array' THEN CONTINUE; END IF;
        changed := false;
        FOR part_index IN 0..jsonb_array_length(parts) - 1 LOOP
            part := parts->part_index;
            IF part->>'id' IS NOT NULL
               AND part->>'id' !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
                parts := jsonb_set(parts, ARRAY[part_index::text, 'id'],
                    to_jsonb(md5(template.code || ':' || (part->>'id'))::uuid::text));
                changed := true;
            END IF;
        END LOOP;
        IF changed THEN
            UPDATE exam.exam_template SET structure = jsonb_set(template.structure, '{parts}', parts)
            WHERE code = template.code;
        END IF;
    END LOOP;
END
$$;
