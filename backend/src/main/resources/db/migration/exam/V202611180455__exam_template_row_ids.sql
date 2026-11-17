-- UNI-2658: template row identifiers survive copying into an exam and subsequent edits.
-- Preserve existing identifiers; generate each missing identifier only once.
CREATE OR REPLACE FUNCTION pg_temp.exam_section_ids(sections jsonb) RETURNS jsonb
LANGUAGE sql VOLATILE AS $$
    SELECT COALESCE(jsonb_agg(
        CASE WHEN section->>'id' IS NULL
             THEN jsonb_set(section, '{id}', to_jsonb(gen_random_uuid()::text), true)
             ELSE section END ORDER BY position), '[]'::jsonb)
    FROM jsonb_array_elements(COALESCE(sections, '[]'::jsonb)) WITH ORDINALITY AS s(section, position)
$$;

DO $$
DECLARE
    template record;
    part jsonb;
    option_item jsonb;
    parts jsonb;
    options jsonb;
    part_index integer;
    option_index integer;
BEGIN
    FOR template IN SELECT code, structure FROM exam.exam_template LOOP
        parts := template.structure->'parts';
        IF jsonb_typeof(parts) IS DISTINCT FROM 'array' THEN CONTINUE; END IF;
        FOR part_index IN 0..jsonb_array_length(parts) - 1 LOOP
            part := parts->part_index;
            IF part->>'id' IS NULL THEN
                part := jsonb_set(part, '{id}', to_jsonb(gen_random_uuid()::text), true);
            END IF;
            IF jsonb_typeof(part->'sections') = 'array' THEN
                part := jsonb_set(part, '{sections}', pg_temp.exam_section_ids(part->'sections'));
            END IF;
            IF jsonb_typeof(part->'subjectPool') = 'array' THEN
                part := jsonb_set(part, '{subjectPool}', pg_temp.exam_section_ids(part->'subjectPool'));
            END IF;
            options := part->'options';
            IF jsonb_typeof(options) = 'array' THEN
                FOR option_index IN 0..jsonb_array_length(options) - 1 LOOP
                    option_item := options->option_index;
                    IF jsonb_typeof(option_item->'sections') = 'array' THEN
                        option_item := jsonb_set(option_item, '{sections}', pg_temp.exam_section_ids(option_item->'sections'));
                    END IF;
                    options := jsonb_set(options, ARRAY[option_index::text], option_item);
                END LOOP;
                part := jsonb_set(part, '{options}', options);
            END IF;
            parts := jsonb_set(parts, ARRAY[part_index::text], part);
        END LOOP;
        UPDATE exam.exam_template SET structure = jsonb_set(template.structure, '{parts}', parts)
        WHERE code = template.code AND structure IS DISTINCT FROM jsonb_set(template.structure, '{parts}', parts);
    END LOOP;
END
$$;
