-- Development/test sample questions only. Stable IDs and codes make repeatable runs safe.
-- English ASCII content deliberately keeps search_text identical to the Java normalizer.
CREATE TEMP TABLE seed_question_rows (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    type text NOT NULL,
    subject_id uuid NOT NULL,
    grade_id uuid NOT NULL,
    level_id uuid NOT NULL,
    task_type_id uuid,
    points numeric(8,4) NOT NULL,
    content text NOT NULL,
    body jsonb NOT NULL,
    parent_id uuid,
    position integer
) ON COMMIT DROP;

-- HSA Mathematics: each grade x level x answer-type cell has 12 independent
-- questions, giving ample headroom for the 35/15 answer and 3/20/27 grade quotas.
INSERT INTO seed_question_rows
SELECT md5('thi360-seed-math-' || grade.code || '-' || level.code || '-' || kind.type || '-' || n)::uuid,
       'SEED-MATH-' || grade.code || '-' || level.code || '-' || kind.suffix || '-' || lpad(n::text, 3, '0'),
       kind.type, subject.id, grade.id, level.id, NULL, 1,
       '<p>Math example ' || n || '</p>',
       CASE WHEN kind.type = 'SINGLE_CHOICE' THEN
           jsonb_build_object('type', kind.type, 'shuffleOptions', false, 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false)))
       ELSE jsonb_build_object('type', kind.type, 'answers', jsonb_build_array('1'), 'inputHint', '') END,
       NULL, NULL
FROM catalog.catalog_item subject
CROSS JOIN catalog.catalog_item grade
CROSS JOIN catalog.catalog_item level
CROSS JOIN (VALUES ('SINGLE_CHOICE', 'SC'), ('SHORT_ANSWER', 'SA')) kind(type, suffix)
CROSS JOIN generate_series(1, 12) n
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'TOAN'
  AND grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code IN ('G10', 'G11', 'G12')
  AND level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code IN ('NB', 'TH', 'VD', 'VDC');

-- Sets contain one child of each requested answer type. Their IDs are also
-- stored in the parent snapshot, and child snapshots carry the same version.
WITH sets AS (
    SELECT n, md5('thi360-seed-math-set-' || n)::uuid AS id,
           md5('thi360-seed-math-set-' || n || '-sc')::uuid AS sc_id,
           md5('thi360-seed-math-set-' || n || '-sa')::uuid AS sa_id,
           subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id
    FROM generate_series(1, 12) n
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'TOAN'
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003'
        AND grade.code = (ARRAY['G10', 'G11', 'G12'])[1 + (n - 1) / 4]
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004'
        AND level.code = (ARRAY['NB', 'TH', 'VD', 'VDC'])[1 + (n - 1) % 4]
), children AS (
    SELECT s.*, c.pos, c.child_id, c.type,
           CASE WHEN c.type = 'SINGLE_CHOICE' THEN
               jsonb_build_object('type', c.type, 'shuffleOptions', false, 'options',
                   jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                     jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false)))
           ELSE jsonb_build_object('type', c.type, 'answers', jsonb_build_array('1'), 'inputHint', '') END AS body
    FROM sets s
    CROSS JOIN LATERAL (VALUES (0, s.sc_id, 'SINGLE_CHOICE'), (1, s.sa_id, 'SHORT_ANSWER')) c(pos, child_id, type)
), parent_rows AS (
    SELECT s.id, 'SEED-MATH-SET-' || lpad(s.n::text, 3, '0') AS code, 'QUESTION_SET' AS type,
           s.subject_id, s.grade_id, s.level_id, NULL::uuid AS task_type_id, 2::numeric AS points,
           '<p>Math set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>Math context</p>', 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', c.type, 'content', '<p>Math child</p>',
                   'body', c.body, 'points', 1, 'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.n, s.subject_id, s.grade_id, s.level_id
)
INSERT INTO seed_question_rows
SELECT * FROM parent_rows
UNION ALL
SELECT child_id, 'SEED-MATH-SET-' || lpad(n::text, 3, '0') || '-' || pos, type,
       subject_id, grade_id, level_id, NULL, 1, '<p>Math child</p>', body, id, pos
FROM children;

-- Science and BCA practice: each grade/level cell has independent examples.
INSERT INTO seed_question_rows
SELECT md5('thi360-seed-' || subject.code || '-' || grade.code || '-' || level.code || '-' || n)::uuid,
       'SEED-' || subject.code || '-' || grade.code || '-' || level.code || '-' || lpad(n::text, 3, '0'),
       'SINGLE_CHOICE', subject.id, grade.id, level.id, NULL,
       CASE WHEN subject.code = 'ANH' THEN 0.75 ELSE 1 END,
       '<p>Practice example ' || n || '</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'shuffleOptions', false, 'options',
           jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                             jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))),
       NULL, NULL
FROM catalog.catalog_item subject
CROSS JOIN catalog.catalog_item grade
CROSS JOIN catalog.catalog_item level
CROSS JOIN generate_series(1, 6) n
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002'
  AND subject.code IN ('LY', 'HOA', 'SINH', 'SU', 'DIA', 'ANH')
  AND grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code IN ('G10', 'G11', 'G12')
  AND level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code IN ('NB', 'TH', 'VD', 'VDC');

-- BE-4 supplies these TASK_TYPE values. LEFT JOIN tolerates their absence;
-- a fresh database seeded after BE-4 receives the corresponding classifications.
INSERT INTO seed_question_rows
SELECT md5('thi360-seed-task-' || task.code || '-' || grade.code || '-' || level.code || '-' || n)::uuid,
       'SEED-TASK-' || task.code || '-' || grade.code || '-' || level.code || '-' || n,
       'SINGLE_CHOICE', subject.id, grade.id, level.id, task_item.id, 1,
       '<p>Language example ' || n || '</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'shuffleOptions', false, 'options',
           jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                             jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))),
       NULL, NULL
FROM (VALUES ('ANH', 'SYNONYM'), ('ANH', 'ANTONYM'), ('ANH', 'DIALOGUE_COMPLETION'),
             ('ANH', 'READING_COMPREHENSION'), ('VAN', 'WORD_USAGE_ERROR'), ('VAN', 'PASSAGE_READING')) task(subject_code, code)
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = task.subject_code
LEFT JOIN catalog.catalog_item task_item ON task_item.group_id = '00000000-0000-7000-8100-000000000006' AND task_item.code = task.code
CROSS JOIN catalog.catalog_item grade
CROSS JOIN catalog.catalog_item level
CROSS JOIN generate_series(1, 2) n
WHERE grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code IN ('G10', 'G11', 'G12')
  AND level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code IN ('NB', 'TH', 'VD', 'VDC');

-- BCA literature essay and THPTQG count-table scoring example.
INSERT INTO seed_question_rows
SELECT md5('thi360-seed-special-' || kind.type || '-' || grade.code || '-' || level.code || '-' || n)::uuid,
       'SEED-' || kind.type || '-' || grade.code || '-' || level.code || '-' || n,
       kind.type, subject.id, grade.id, level.id, NULL,
       CASE WHEN kind.type = 'ESSAY' THEN 25 ELSE 1 END,
       '<p>Practice example ' || n || '</p>',
       CASE WHEN kind.type = 'ESSAY' THEN
           jsonb_build_object('type', kind.type, 'gradingGuide', '<p>Give credit for a clear argument.</p>', 'rubric', '[]'::jsonb, 'wordLimit', 500)
       ELSE jsonb_build_object('type', kind.type, 'scoringMethod', 'COUNT_TABLE', 'scoreTable', jsonb_build_array(0.1, 0.25, 0.5, 1),
           'statements', jsonb_build_array(
               jsonb_build_object('id', 'a', 'content', '<p>First</p>', 'answer', true),
               jsonb_build_object('id', 'b', 'content', '<p>Second</p>', 'answer', false),
               jsonb_build_object('id', 'c', 'content', '<p>Third</p>', 'answer', true),
               jsonb_build_object('id', 'd', 'content', '<p>Fourth</p>', 'answer', false))) END,
       NULL, NULL
FROM (VALUES ('ESSAY', 'VAN'), ('TRUE_FALSE', 'TOAN')) kind(type, subject_code)
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = kind.subject_code
CROSS JOIN catalog.catalog_item grade
CROSS JOIN catalog.catalog_item level
CROSS JOIN generate_series(1, 2) n
WHERE grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code IN ('G10', 'G11', 'G12')
  AND level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code IN ('NB', 'TH', 'VD', 'VDC');

-- Reading-comprehension groups select whole sets, rather than isolated items.
WITH sets AS (
    SELECT subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id,
           task.code AS task_code, task_item.id AS task_type_id,
           md5('thi360-seed-reading-' || task.code || grade.code || level.code)::uuid AS id,
           'SEED-READING-' || task.code || '-' || grade.code || '-' || level.code AS code
    FROM (VALUES ('ANH', 'READING_COMPREHENSION'), ('VAN', 'PASSAGE_READING')) task(subject_code, code)
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = task.subject_code
    LEFT JOIN catalog.catalog_item task_item ON task_item.group_id = '00000000-0000-7000-8100-000000000006' AND task_item.code = task.code
    CROSS JOIN catalog.catalog_item grade
    CROSS JOIN catalog.catalog_item level
    WHERE grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code IN ('G10', 'G11', 'G12')
      AND level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code IN ('NB', 'TH', 'VD', 'VDC')
), children AS (
    SELECT s.*, n AS pos, md5(s.id::text || '-' || n)::uuid AS child_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'shuffleOptions', false, 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))) AS body
    FROM sets s CROSS JOIN generate_series(0, 1) n
), parents AS (
    SELECT s.id, s.code, 'QUESTION_SET' AS type, s.subject_id, s.grade_id, s.level_id, s.task_type_id,
           2::numeric AS points, '<p>Reading set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>Reading context</p>', 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', 'SINGLE_CHOICE',
                   'content', '<p>Reading child</p>', 'body', c.body, 'points', 1,
                   'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.code, s.subject_id, s.grade_id, s.level_id, s.task_type_id
)
INSERT INTO seed_question_rows
SELECT * FROM parents
UNION ALL
SELECT child_id, code || '-' || pos, 'SINGLE_CHOICE', subject_id, grade_id, level_id,
       task_type_id, 1, '<p>Reading child</p>', body, id, pos
FROM children;

-- Parents must exist before their child rows satisfy the self-reference.
WITH inserted AS (
    INSERT INTO questionbank.question
        (id, code, type, status, parent_id, position, subject_item_id, grade_item_id,
         level_item_id, task_type_item_id, points, current_version_no, working_version_no,
         content_text, search_text)
    SELECT id, code, type, 'READY', NULL, NULL, subject_id, grade_id, level_id, task_type_id,
           points, 1, NULL,
           CASE type WHEN 'SINGLE_CHOICE' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' One Two'
                     WHEN 'SHORT_ANSWER' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' 1'
                     WHEN 'ESSAY' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' Give credit for a clear argument.'
                     WHEN 'TRUE_FALSE' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' First Second Third Fourth'
                     WHEN 'QUESTION_SET' THEN CASE WHEN code LIKE 'SEED-READING-%' THEN 'Reading set Reading context Reading child One Two Reading child One Two'
                                                   ELSE 'Math set Math context Math child One Two Math child 1' END
                     ELSE 'Math set Math context Math child One Two Math child 1' END,
           lower(code || ' ' ||
               CASE type WHEN 'SINGLE_CHOICE' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' One Two'
                         WHEN 'SHORT_ANSWER' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' 1'
                         WHEN 'ESSAY' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' Give credit for a clear argument.'
                         WHEN 'TRUE_FALSE' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' First Second Third Fourth'
                         WHEN 'QUESTION_SET' THEN CASE WHEN code LIKE 'SEED-READING-%' THEN 'Reading set Reading context Reading child One Two Reading child One Two'
                                                       ELSE 'Math set Math context Math child One Two Math child 1' END
                         ELSE 'Math set Math context Math child One Two Math child 1' END)
    FROM seed_question_rows WHERE parent_id IS NULL
    ON CONFLICT DO NOTHING RETURNING subject_item_id
)
INSERT INTO questionbank.question_source_revision (subject_item_id, revision)
SELECT subject_item_id, count(*) FROM inserted GROUP BY subject_item_id
ON CONFLICT (subject_item_id) DO UPDATE
SET revision = questionbank.question_source_revision.revision + EXCLUDED.revision;

INSERT INTO questionbank.question
    (id, code, type, status, parent_id, position, subject_item_id, grade_item_id,
     level_item_id, task_type_item_id, points, current_version_no, working_version_no,
     content_text, search_text)
SELECT id, code, type, 'READY', parent_id, position, subject_id, grade_id, level_id, task_type_id,
       points, 1, NULL,
       regexp_replace(content, '<[^>]+>', '', 'g') || CASE type WHEN 'SINGLE_CHOICE' THEN ' One Two' ELSE ' 1' END,
       lower(code || ' ' || regexp_replace(content, '<[^>]+>', '', 'g') ||
             CASE type WHEN 'SINGLE_CHOICE' THEN ' One Two' ELSE ' 1' END)
FROM seed_question_rows WHERE parent_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO questionbank.question_version (question_id, version_no, snapshot, ready_at)
SELECT s.id, 1, jsonb_build_object(
    'subjectItemId', s.subject_id, 'gradeItemId', s.grade_id, 'levelItemId', s.level_id,
    'taskTypeItemId', s.task_type_id, 'competencyItemId', NULL, 'points', s.points,
    'knowledge', '[]'::jsonb, 'tagItemIds', '[]'::jsonb, 'content', s.content,
    'solution', NULL, 'commonMistakes', '[]'::jsonb, 'attachmentIds', '[]'::jsonb, 'body', s.body), now()
FROM seed_question_rows s
JOIN questionbank.question q ON q.id = s.id
ON CONFLICT DO NOTHING;
