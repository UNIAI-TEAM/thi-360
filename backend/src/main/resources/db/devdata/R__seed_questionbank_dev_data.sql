-- UNI-2609: deterministic sample questions, loaded only by the dev/test Flyway locations.
-- All text is ASCII so lower(code || ' ' || plain_text) is exactly the result of
-- QuestionSearchNormalizer.searchText for these simple HTML fragments.
CREATE TEMPORARY TABLE seed_question (
    id uuid PRIMARY KEY, code text NOT NULL, type text NOT NULL,
    status text NOT NULL DEFAULT 'READY', parent_id uuid, position integer,
    subject_code text NOT NULL, grade_code text NOT NULL, level_code text NOT NULL,
    task_code text, points numeric NOT NULL DEFAULT 1, content text NOT NULL,
    body jsonb NOT NULL, plain_text text NOT NULL
) ON COMMIT DROP;

-- HSA Math: every grade/level cell has 12 single-choice and 5 short-answer
-- questions, in addition to four mixed three-child context sets.  This exceeds
-- twice each subject-level marginal (35/15, 3/20/27 and 2/21/20/7).
INSERT INTO seed_question (id, code, type, subject_code, grade_code, level_code, content, body, plain_text)
SELECT ('90000000-0000-7000-8000-' || lpad(n::text, 12, '0'))::uuid,
       'CH-SEED-' || lpad(n::text, 6, '0'), qtype, 'TOAN', grade, level,
       '<p>Phep tinh mau ' || n || '</p>',
       CASE WHEN qtype = 'SINGLE_CHOICE' THEN
           jsonb_build_object('type', qtype, 'shuffleOptions', true, 'options', jsonb_build_array(
               jsonb_build_object('id', 'a', 'content', '<p>2</p>', 'correct', true),
               jsonb_build_object('id', 'b', 'content', '<p>3</p>', 'correct', false)))
       ELSE jsonb_build_object('type', qtype, 'answers', jsonb_build_array('2'), 'inputHint', NULL) END,
       'Phep tinh mau ' || n || CASE WHEN qtype = 'SINGLE_CHOICE' THEN ' 2 3' ELSE ' 2' END
FROM (
    SELECT (g - 1) * 68 + (l - 1) * 17 + s AS n,
           (ARRAY['G10','G11','G12'])[g] AS grade,
           (ARRAY['NB','TH','VD','VDC'])[l] AS level,
           CASE WHEN s <= 12 THEN 'SINGLE_CHOICE' ELSE 'SHORT_ANSWER' END AS qtype
    FROM generate_series(1, 3) g CROSS JOIN generate_series(1, 4) l
    CROSS JOIN generate_series(1, 17) s
) generated;

-- The set snapshot embeds exactly the same IDs, bodies and classification as
-- the separately stored children; the child rows are the counting units.
INSERT INTO seed_question (id, code, type, subject_code, grade_code, level_code, points, content, body, plain_text)
SELECT ('90000000-0000-7000-8001-' || lpad(s::text, 12, '0'))::uuid,
       'CH-SEED-SET-' || s, 'QUESTION_SET', 'TOAN', 'G12', 'VD', 3,
       '<p>Bo cau hoi mau ' || s || '</p>',
       jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>Ngu lieu chung</p>',
           'children', (SELECT jsonb_agg(jsonb_build_object(
               'id', ('90000000-0000-7000-8002-' || lpad((s * 10 + p)::text, 12, '0'))::uuid,
               'type', CASE WHEN p = 3 THEN 'SHORT_ANSWER' ELSE 'SINGLE_CHOICE' END,
               'content', '<p>Cau con ' || p || '</p>', 'points', 1,
               'gradeItemId', '00000000-0000-7000-8200-000000000303',
               'levelItemId', '00000000-0000-7000-8200-000000000403',
               'knowledgeItemIds', jsonb_build_array(),
               'body', CASE WHEN p = 3 THEN jsonb_build_object('type', 'SHORT_ANSWER', 'answers', jsonb_build_array('2'))
                   ELSE jsonb_build_object('type', 'SINGLE_CHOICE', 'shuffleOptions', true, 'options', jsonb_build_array(
                       jsonb_build_object('id', 'a', 'content', '<p>2</p>', 'correct', true),
                       jsonb_build_object('id', 'b', 'content', '<p>3</p>', 'correct', false))) END
           ) ORDER BY p) FROM generate_series(1, 3) p)),
       'Bo cau hoi mau ' || s || ' Ngu lieu chung Cau con 1 2 3 Cau con 2 2 3 Cau con 3 2'
FROM generate_series(1, 4) s;

INSERT INTO seed_question (id, code, type, parent_id, position, subject_code, grade_code, level_code, content, body, plain_text)
SELECT (child->>'id')::uuid, q.code || '.' || p, child->>'type', q.id, p - 1,
       'TOAN', 'G12', 'VD', child->>'content', child->'body',
       'Cau con ' || p || CASE WHEN p = 3 THEN ' 2' ELSE ' 2 3' END
FROM seed_question q CROSS JOIN LATERAL jsonb_array_elements(q.body->'children') WITH ORDINALITY c(child, p)
WHERE q.type = 'QUESTION_SET';

-- Resolve catalog values by group and code, rather than assuming catalog UUIDs.
CREATE TEMPORARY TABLE seed_resolved ON COMMIT DROP AS
SELECT q.*, subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id, task.id AS task_id
FROM seed_question q
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = q.subject_code
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = q.grade_code
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = q.level_code
LEFT JOIN catalog.catalog_item task ON task.group_id = '00000000-0000-7000-8100-000000000006' AND task.code = q.task_code;

INSERT INTO questionbank.question (id, code, type, status, parent_id, position, subject_item_id,
    grade_item_id, level_item_id, task_type_item_id, points, current_version_no, working_version_no,
    content_text, search_text)
SELECT id, code, type, status, parent_id, position, subject_id, grade_id, level_id, task_id, points,
       CASE WHEN status = 'DRAFT' THEN NULL ELSE 1 END,
       CASE WHEN status = 'DRAFT' THEN 1 ELSE NULL END,
       plain_text, lower(code || ' ' || plain_text)
FROM seed_resolved WHERE parent_id IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO questionbank.question (id, code, type, status, parent_id, position, subject_item_id,
    grade_item_id, level_item_id, task_type_item_id, points, current_version_no, working_version_no,
    content_text, search_text)
SELECT id, code, type, status, parent_id, position, subject_id, grade_id, level_id, task_id, points,
       CASE WHEN status = 'DRAFT' THEN NULL ELSE 1 END,
       CASE WHEN status = 'DRAFT' THEN 1 ELSE NULL END,
       plain_text, lower(code || ' ' || plain_text)
FROM seed_resolved WHERE parent_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO questionbank.question_version (question_id, version_no, snapshot, ready_at)
SELECT id, 1, jsonb_build_object('subjectItemId', subject_id, 'gradeItemId', grade_id,
    'levelItemId', level_id, 'taskTypeItemId', task_id, 'points', points, 'knowledge', '[]'::jsonb,
    'tagItemIds', '[]'::jsonb, 'content', content, 'commonMistakes', '[]'::jsonb,
    'attachmentIds', '[]'::jsonb, 'body', body),
    CASE WHEN status = 'READY' THEN now() ELSE NULL END
FROM seed_resolved ON CONFLICT DO NOTHING;

INSERT INTO questionbank.question_source_revision (subject_item_id, revision)
SELECT DISTINCT subject_id, 1 FROM seed_resolved WHERE status = 'READY'
ON CONFLICT DO NOTHING;
