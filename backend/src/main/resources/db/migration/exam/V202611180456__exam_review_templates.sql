-- UNI-2658: a subject-independent review skeleton per supported examination kind.
-- The requested subject is filled when the template is loaded.
ALTER TABLE exam.exam_template DROP CONSTRAINT chk_exam_template_scope_fields;
ALTER TABLE exam.exam_template ADD CONSTRAINT chk_exam_template_scope_fields CHECK (
    (scope = 'FULL' AND part_code IS NULL AND subject_code IS NULL)
    OR (scope = 'PART' AND subject_code IS NULL)
    OR (scope = 'SUBJECT' AND (subject_code IS NOT NULL OR build_mode = 'REVIEW'))
);

INSERT INTO exam.exam_template (code, kind_code, scope, part_code, subject_code,
    timing, duration_minutes, max_score, label, summary, build_mode, is_default, structure, active)
SELECT kind || '_REVIEW', kind, 'SUBJECT', NULL, NULL, 'PER_PART', NULL, NULL,
       kind || ' REVIEW', NULL, 'REVIEW', true,
       jsonb_build_object('parts', jsonb_build_array(jsonb_build_object(
           'id', kind || '_REVIEW_PART', 'order', 1, 'name', 'Review',
           'sections', jsonb_build_array(jsonb_build_object(
               'id', kind || '_REVIEW_SECTION', 'subjectCode', NULL,
               'blocks', jsonb_build_array(jsonb_build_object('id', kind || '_REVIEW_BLOCK')))),
           'options', '[]'::jsonb))) , true
FROM unnest(ARRAY['HSA', 'QDA', 'BCA', 'SP2E', 'SPT']) AS kinds(kind)
ON CONFLICT (code) DO NOTHING;
