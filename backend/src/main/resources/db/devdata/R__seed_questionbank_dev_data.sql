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

-- Five HSA science subjects: 48 each (four per grade/level cell), exceeding
-- two sets of 17.  The same one-point sources also cover BCA science/history.
INSERT INTO seed_question (id, code, type, subject_code, grade_code, level_code, content, body, plain_text)
SELECT ('90000000-0000-7000-8003-' || lpad(n::text, 12, '0'))::uuid,
       'CH-SEED-SCI-' || n, 'SINGLE_CHOICE', subject, grade, level,
       '<p>Cau khoa hoc mau ' || n || '</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'shuffleOptions', true, 'options', jsonb_build_array(
           jsonb_build_object('id', 'a', 'content', '<p>Dung</p>', 'correct', true),
           jsonb_build_object('id', 'b', 'content', '<p>Sai</p>', 'correct', false))),
       'Cau khoa hoc mau ' || n || ' Dung Sai'
FROM (
    SELECT (sub - 1) * 48 + (g - 1) * 16 + (l - 1) * 4 + s AS n,
           (ARRAY['LY','HOA','SINH','SU','DIA'])[sub] AS subject,
           (ARRAY['G10','G11','G12'])[g] AS grade,
           (ARRAY['NB','TH','VD','VDC'])[l] AS level
    FROM generate_series(1, 5) sub CROSS JOIN generate_series(1, 3) g
    CROSS JOIN generate_series(1, 4) l CROSS JOIN generate_series(1, 4) s
) generated;

-- BE-4 supplies these TASK_TYPE codes.  LEFT JOIN below tolerates their
-- absence until its dev seed is merged; no fabricated catalog values are used.
INSERT INTO seed_question (id, code, type, subject_code, grade_code, level_code, task_code, content, body, plain_text)
SELECT ('90000000-0000-7000-8004-' || lpad(n::text, 12, '0'))::uuid,
       'CH-SEED-LANG-' || n, 'SINGLE_CHOICE', subject, grade, level, task_code,
       '<p>Cau ngon ngu mau ' || n || '</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'shuffleOptions', true, 'options', jsonb_build_array(
           jsonb_build_object('id', 'a', 'content', '<p>Yes</p>', 'correct', true),
           jsonb_build_object('id', 'b', 'content', '<p>No</p>', 'correct', false))),
       'Cau ngon ngu mau ' || n || ' Yes No'
FROM (
    SELECT (t - 1) * 24 + s AS n,
           CASE WHEN t <= 4 THEN 'ANH' ELSE 'VAN' END AS subject,
           (ARRAY['G10','G11','G12'])[1 + ((s - 1) % 3)] AS grade,
           (ARRAY['NB','TH','VD','VDC'])[1 + ((s - 1) % 4)] AS level,
           (ARRAY['SYNONYM','ANTONYM','DIALOGUE_COMPLETION','READING_COMPREHENSION',
                  'WORD_USAGE_ERROR','PASSAGE_READING'])[t] AS task_code
    FROM generate_series(1, 6) t CROSS JOIN generate_series(1, 24) s
) generated;

-- BCA English: two complete 20-question pools at the required 0.75 points.
INSERT INTO seed_question (id, code, type, subject_code, grade_code, level_code, points, content, body, plain_text)
SELECT ('90000000-0000-7000-8005-' || lpad(n::text, 12, '0'))::uuid,
       'CH-SEED-BCA-' || n, 'SINGLE_CHOICE', 'ANH',
       (ARRAY['G10','G11','G12'])[1 + ((n - 1) % 3)],
       (ARRAY['NB','TH','VD','VDC'])[1 + ((n - 1) % 4)], 0.75,
       '<p>English sample ' || n || '</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'shuffleOptions', true, 'options', jsonb_build_array(
           jsonb_build_object('id', 'a', 'content', '<p>Yes</p>', 'correct', true),
           jsonb_build_object('id', 'b', 'content', '<p>No</p>', 'correct', false))),
       'English sample ' || n || ' Yes No'
FROM generate_series(1, 40) n;

-- Two BCA essays with the required 25-point score.
INSERT INTO seed_question (id, code, type, subject_code, grade_code, level_code, points, content, body, plain_text)
SELECT ('90000000-0000-7000-8006-' || lpad(n::text, 12, '0'))::uuid,
       'CH-SEED-ESSAY-' || n, 'ESSAY', 'VAN', 'G12', 'VD', 25,
       '<p>Bai van mau ' || n || '</p>',
       jsonb_build_object('type', 'ESSAY', 'gradingGuide', '<p>Huong dan cham</p>', 'rubric', jsonb_build_array(), 'wordLimit', 500),
       'Bai van mau ' || n || ' Huong dan cham'
FROM generate_series(1, 2) n;

-- THPTQG true/false count table: one, two, three, four correct statements.
INSERT INTO seed_question (id, code, type, subject_code, grade_code, level_code, content, body, plain_text)
SELECT ('90000000-0000-7000-8007-' || lpad(n::text, 12, '0'))::uuid,
       'CH-SEED-TF-' || n, 'TRUE_FALSE', 'TOAN', 'G12', 'VD',
       '<p>Dung sai mau ' || n || '</p>',
       jsonb_build_object('type', 'TRUE_FALSE', 'scoringMethod', 'COUNT_TABLE',
           'scoreTable', jsonb_build_array(0.1, 0.25, 0.5, 1), 'statements',
           (SELECT jsonb_agg(jsonb_build_object('id', p::text, 'content', '<p>Menh de ' || p || '</p>', 'answer', p % 2 = 0) ORDER BY p)
            FROM generate_series(1, 4) p)),
       'Dung sai mau ' || n || ' Menh de 1 Menh de 2 Menh de 3 Menh de 4'
FROM generate_series(1, 8) n;

-- Examples of the remaining body types, including Draft and Retired.
INSERT INTO seed_question (id, code, type, status, subject_code, grade_code, level_code, content, body, plain_text)
SELECT ('90000000-0000-7000-8008-' || lpad(n::text, 12, '0'))::uuid,
       'CH-SEED-OTHER-' || n, qtype, state, 'TOAN', 'G12', 'VD', '<p>Cau mau</p>',
       CASE qtype
           WHEN 'MULTIPLE_CHOICE' THEN jsonb_build_object('type', qtype, 'scoringRule', 'ALL_OR_NOTHING',
               'options', jsonb_build_array(
                   jsonb_build_object('id', 'a', 'content', '<p>A</p>', 'correct', true),
                   jsonb_build_object('id', 'b', 'content', '<p>B</p>', 'correct', true)))
           WHEN 'CLOZE' THEN jsonb_build_object('type', qtype, 'passage', '<p>Dien [[b1]]</p>',
               'blanks', jsonb_build_array(jsonb_build_object('id', 'b1', 'answers', jsonb_build_array('2'), 'points', 1)))
           ELSE jsonb_build_object('type', qtype, 'reuseRight', false,
               'left', jsonb_build_array(jsonb_build_object('id', 'l1', 'content', '<p>Mot</p>')),
               'right', jsonb_build_array(jsonb_build_object('id', 'r1', 'content', '<p>One</p>')),
               'pairs', jsonb_build_array(jsonb_build_object('leftId', 'l1', 'rightId', 'r1', 'points', 1))) END,
       CASE qtype WHEN 'MULTIPLE_CHOICE' THEN 'Cau mau A B'
                  WHEN 'CLOZE' THEN 'Cau mau Dien [[b1]]' ELSE 'Cau mau Mot One' END
FROM (SELECT n, (ARRAY['MULTIPLE_CHOICE','CLOZE','MATCHING','MULTIPLE_CHOICE','CLOZE'])[n] AS qtype,
             (ARRAY['READY','READY','READY','DRAFT','RETIRED'])[n] AS state
      FROM generate_series(1, 5) n) generated;

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
