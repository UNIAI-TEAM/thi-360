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

-- Provisional BE-2 codes: keep all classifications here for reconciliation.
CREATE TEMP TABLE seed_2b_tags (purpose text PRIMARY KEY, code text NOT NULL) ON COMMIT DROP;
INSERT INTO seed_2b_tags (purpose, code) VALUES
    ('literature_social', 'VAN_NGHI_LUAN_XA_HOI'),
    ('literature_information', 'VAN_THONG_TIN'),
    ('literature_scientific_political', 'VAN_KHOA_HOC_CHINH_LUAN'),
    ('literature_poetry', 'VAN_THO'),
    ('literature_prose', 'VAN_VAN_XUOI'),
    ('literature_information_argument', 'VAN_THONG_TIN_NGHI_LUAN'),
    ('literature_argument', 'VAN_NGHI_LUAN'),
    ('literature_children', 'VAN_THIEU_NHI'),
    ('literature_story', 'VAN_TRUYEN'),
    ('literature_science', 'VAN_KHOA_HOC'),
    ('literature_odd_word', 'VAN_TU_KHAC_NHOM'),
    ('literature_word_error', 'VAN_TU_SAI'),
    ('literature_word_pair', 'VAN_CAP_TU_DIEN'),
    ('literature_single_passage', 'VAN_NGU_LIEU_MOT_CAU'),
    ('english_completion', 'ANH_HOAN_THANH_CAU'),
    ('english_synonym', 'ANH_DONG_NGHIA'),
    ('english_antonym', 'ANH_TRAI_NGHIA'),
    ('english_rewrite', 'ANH_VIET_LAI_CAU'),
    ('english_combine', 'ANH_KET_HOP_CAU'),
    ('english_dialogue', 'ANH_HOI_THOAI'),
    ('english_dialogue_order', 'ANH_SAP_XEP_HOI_THOAI'),
    ('english_cloze', 'ANH_DOC_DIEN'),
    ('english_reading_one', 'ANH_DOC_HIEU_1'),
    ('english_reading_two', 'ANH_DOC_HIEU_2'),
    ('english_logic', 'ANH_TU_DUY_LOGIC'),
    ('english_notice', 'ANH_THONG_BAO'),
    ('english_inference', 'ANH_SUY_LUAN'),
    ('english_sentence_cloze', 'ANH_DOC_DIEN_CAU'),
    ('english_word_cloze', 'ANH_DIEN_TU'),
    ('english_phrase_cloze', 'ANH_DIEN_CUM_CAU'),
    ('english_communication', 'ANH_GIAO_TIEP'),
    ('english_paragraph_order', 'ANH_SAP_XEP_DOAN'),
    ('english_pronunciation', 'ANH_PHAT_AM'),
    ('english_stress', 'ANH_TRONG_AM'),
    ('english_vocabulary', 'ANH_TU_VUNG_NGU_PHAP'),
    ('english_reading', 'ANH_DOC_HIEU'),
    ('english_word_formation', 'ANH_WORD_FORMATION'),
    ('math_synthesis', 'TOAN_DU_LIEU_TONG_HOP'),
    ('history_source', 'SU_TU_LIEU_LICH_SU'),
    ('geography_skill', 'DIA_CAU_KY_NANG');

CREATE TEMP TABLE seed_2b_question_tags (
    question_id uuid NOT NULL,
    tag_item_id uuid NOT NULL,
    PRIMARY KEY (question_id, tag_item_id)
) ON COMMIT DROP;

CREATE TEMP TABLE seed_2b_knowledge (
    question_id uuid PRIMARY KEY,
    knowledge_item_id uuid NOT NULL
) ON COMMIT DROP;

CREATE TEMP TABLE seed_inserted_questions (
    id uuid PRIMARY KEY,
    subject_id uuid NOT NULL,
    status text NOT NULL
) ON COMMIT DROP;

CREATE TEMP TABLE seed_inserted_children (id uuid PRIMARY KEY) ON COMMIT DROP;

CREATE TEMP TABLE seed_be14_classification (
    question_id uuid PRIMARY KEY,
    competency_code text NOT NULL,
    tag_code text
) ON COMMIT DROP;

-- HSA Mathematics: each grade x level x answer-type cell has 12 independent
-- questions, giving ample headroom for the 35/15 answer and 3/20/27 grade quotas.
INSERT INTO seed_question_rows
SELECT md5('thi360-seed-math-' || grade.code || '-' || level.code || '-' || kind.type || '-' || n)::uuid,
       'SEED-MATH-' || grade.code || '-' || level.code || '-' || kind.suffix || '-' || lpad(n::text, 3, '0'),
       kind.type, subject.id, grade.id, level.id, NULL, 1,
       '<p>Math example ' || n || '</p>',
       CASE WHEN kind.type = 'SINGLE_CHOICE' THEN
           jsonb_build_object('type', kind.type, 'options',
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
    FROM generate_series(1, 120) n
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'TOAN'
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003'
        AND grade.code = (ARRAY['G10', 'G11', 'G12'])[1 + (n - 1) / 40]
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004'
        AND level.code = (ARRAY['NB', 'TH', 'VD', 'VDC'])[1 + (n - 1) % 4]
), children AS (
    SELECT s.*, c.pos, c.child_id, c.type,
           CASE WHEN c.type = 'SINGLE_CHOICE' THEN
               jsonb_build_object('type', c.type, 'options',
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
), hsa_sets AS (
    SELECT n, md5('thi360-seed-hsa-math-set-' || n)::uuid AS id,
           md5('thi360-seed-hsa-math-set-' || n || '-a')::uuid AS first_id,
           md5('thi360-seed-hsa-math-set-' || n || '-b')::uuid AS second_id,
           subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id
    FROM generate_series(1, 120) n
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'TOAN'
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003'
        AND grade.code = (ARRAY['G10', 'G11', 'G12'])[1 + (n - 1) / 40]
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004'
        AND level.code = (ARRAY['NB', 'TH', 'VD', 'VDC'])[1 + ((n - 1) / 10) % 4]
), hsa_children AS (
    SELECT s.*, c.pos, c.child_id,
           'SINGLE_CHOICE'::text AS type,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))) AS body
    FROM hsa_sets s
    CROSS JOIN LATERAL (VALUES (0, s.first_id), (1, s.second_id)) c(pos, child_id)
), hsa_parent_rows AS (
    SELECT s.id, 'SEED-HSA-MATH-SET-' || lpad(s.n::text, 3, '0') AS code, 'QUESTION_SET' AS type,
           s.subject_id, s.grade_id, s.level_id, NULL::uuid AS task_type_id, 2::numeric AS points,
           '<p>HSA Math set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>HSA Math context</p>', 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', c.type, 'content', '<p>HSA Math child</p>',
                   'body', c.body, 'points', 1, 'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM hsa_sets s JOIN hsa_children c ON c.id = s.id
    GROUP BY s.id, s.n, s.subject_id, s.grade_id, s.level_id
)
INSERT INTO seed_question_rows
SELECT * FROM parent_rows
UNION ALL
SELECT child_id, 'SEED-MATH-SET-' || lpad(n::text, 3, '0') || '-' || pos, type,
       subject_id, grade_id, level_id, NULL, 1, '<p>Math child</p>', body, id, pos
FROM children
UNION ALL
SELECT * FROM hsa_parent_rows
UNION ALL
SELECT child_id, 'SEED-HSA-MATH-SET-' || lpad(n::text, 3, '0') || '-' || pos, type,
       subject_id, grade_id, level_id, NULL, 1, '<p>HSA Math child</p>', body, id, pos
FROM hsa_children;

-- Science and BCA practice: each grade/level cell has independent examples.
INSERT INTO seed_question_rows
SELECT md5('thi360-seed-' || subject.code || '-' || grade.code || '-' || level.code || '-' || n)::uuid,
       'SEED-' || subject.code || '-' || grade.code || '-' || level.code || '-' || lpad(n::text, 3, '0'),
       'SINGLE_CHOICE', subject.id, grade.id, level.id, NULL,
       CASE WHEN subject.code = 'ANH' THEN 0.75 ELSE 1 END,
       '<p>Practice example ' || n || '</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
           jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                             jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))),
       NULL, NULL
FROM catalog.catalog_item subject
CROSS JOIN catalog.catalog_item grade
CROSS JOIN catalog.catalog_item level
CROSS JOIN generate_series(1, 36) n
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
       jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
           jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                             jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))),
       NULL, NULL
FROM (VALUES ('ANH', 'SYNONYM'), ('ANH', 'ANTONYM'), ('ANH', 'DIALOGUE_COMPLETION'),
             ('ANH', 'READING_COMPREHENSION'), ('VAN', 'WORD_USAGE_ERROR'), ('VAN', 'PASSAGE_READING')) task(subject_code, code)
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = task.subject_code
LEFT JOIN catalog.catalog_item task_item ON task_item.group_id = '00000000-0000-7000-8100-000000000006' AND task_item.code = task.code
CROSS JOIN catalog.catalog_item grade
CROSS JOIN catalog.catalog_item level
CROSS JOIN generate_series(1, 48) n
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
           md5('thi360-seed-reading-' || task.code || '-' || grade.code || '-' || level.code || '-' || sn)::uuid AS id,
           'SEED-READING-' || task.code || '-' || grade.code || '-' || level.code || '-' || lpad(sn::text, 2, '0') AS code
    FROM (VALUES ('ANH', 'READING_COMPREHENSION'), ('VAN', 'PASSAGE_READING')) task(subject_code, code)
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = task.subject_code
    LEFT JOIN catalog.catalog_item task_item ON task_item.group_id = '00000000-0000-7000-8100-000000000006' AND task_item.code = task.code
    CROSS JOIN catalog.catalog_item grade
    CROSS JOIN catalog.catalog_item level
    CROSS JOIN generate_series(1, 24) sn
    WHERE grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code IN ('G10', 'G11', 'G12')
      AND level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code IN ('NB', 'TH', 'VD', 'VDC')
), children AS (
    SELECT s.*, n AS pos, md5(s.id::text || '-' || n)::uuid AS child_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
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

-- Five-child sets support HSA/QDA slots; independent copies avoid reusing a
-- passage across slots. SPT uses the science and information types at 0.3 and
-- the legacy SP2E reading blocks use tagged 7/8-child sets at 0.2.
WITH sets AS (
    SELECT spec.purpose, spec.points, n, spec.child_count,
           md5('thi360-2b-literature-' || spec.purpose || '-' || spec.points || '-' || n)::uuid AS id,
           subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id
    FROM seed_2b_tags spec_tag
    JOIN (SELECT purpose, 1::numeric AS points, 5 AS child_count FROM seed_2b_tags
          WHERE starts_with(purpose, 'literature_')
          UNION ALL VALUES
              ('literature_science', 0.3::numeric, 5), ('literature_information', 0.3::numeric, 5),
              ('literature_information', 0.2::numeric, 8), ('literature_poetry', 0.2::numeric, 8),
              ('literature_story', 0.2::numeric, 7), ('literature_prose', 0.2::numeric, 7))
         spec ON spec.purpose = spec_tag.purpose
    JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008'
        AND tag.code = spec_tag.code
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'VAN'
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
    CROSS JOIN generate_series(1, 3) n
    UNION ALL
    SELECT 'literature_sp2e', 0.2::numeric, n, n + 5,
           md5('thi360-2b-literature-sp2e-' || n)::uuid, subject.id, grade.id, level.id
    FROM generate_series(1, 4) n
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'VAN'
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
), children AS (
    SELECT s.*, pos, md5(s.id::text || '-' || pos)::uuid AS child_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))) AS body
    FROM sets s CROSS JOIN LATERAL generate_series(0, s.child_count - 1) pos
), parents AS (
    SELECT s.id, 'SEED-2B-LITERATURE-' || s.purpose || '-' || s.points || '-' || s.n AS code,
           'QUESTION_SET' AS type, s.subject_id, s.grade_id, s.level_id, NULL::uuid AS task_type_id,
           s.points * s.child_count AS points, '<p>Reading set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage',
               CASE WHEN s.purpose = 'literature_sp2e' THEN
                   (ARRAY['<p>A gardener saves seeds for the next season and shares them with neighbors.</p>',
                          '<p>A village library opens each evening so families can read together.</p>',
                          '<p>A river path changes as people plant trees along its banks.</p>',
                          '<p>A student records the stories of craftspeople in the local market.</p>'])[s.n]
               ELSE '<p>Reading context ' || s.purpose || ' ' || s.points || ' ' || s.n || '</p>' END, 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', 'SINGLE_CHOICE',
                   'content', '<p>Reading child</p>', 'body', c.body, 'points', s.points,
                   'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.purpose, s.points, s.n, s.child_count, s.subject_id, s.grade_id, s.level_id
)
INSERT INTO seed_question_rows
SELECT * FROM parents
UNION ALL
SELECT child_id, 'SEED-2B-LITERATURE-' || purpose || '-' || points || '-' || n || '-' || pos,
       'SINGLE_CHOICE', subject_id, grade_id, level_id, NULL, points,
       '<p>Reading child</p>', body, id, pos
FROM children;

INSERT INTO seed_2b_question_tags (question_id, tag_item_id)
SELECT q.id, tag.id
FROM seed_question_rows q
JOIN seed_2b_tags t ON starts_with(q.code, 'SEED-2B-LITERATURE-' || t.purpose || '-')
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008'
    AND tag.code = t.code;

-- Untagged reading sources support the retained templates as well as the new ones.
WITH sets AS (
    SELECT subject.code AS subject_code, subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id,
           p.points, sz.child_count, n,
           md5('thi360-2b-generic-reading-' || subject.code || '-' || p.points || '-' || sz.child_count || '-' || n)::uuid AS id
    FROM catalog.catalog_item subject
    CROSS JOIN (VALUES (0.2::numeric), (0.25::numeric), (0.3::numeric), (0.4::numeric)) p(points)
    CROSS JOIN (VALUES (5), (6)) sz(child_count)
    CROSS JOIN generate_series(1, 3) n
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
    WHERE subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code IN ('ANH', 'VAN')
), children AS (
    SELECT s.*, pos, md5(s.id::text || '-' || pos)::uuid AS child_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))) AS body
    FROM sets s CROSS JOIN LATERAL generate_series(0, s.child_count - 1) pos
), parents AS (
    SELECT s.id, 'SEED-2B-GENERIC-READING-' || s.subject_code || '-' || s.points || '-' || s.child_count || '-' || s.n AS code,
           'QUESTION_SET' AS type, s.subject_id, s.grade_id, s.level_id, NULL::uuid AS task_type_id,
           s.points * s.child_count AS points, '<p>Reading set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>Reading context ' || s.n || '</p>', 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', 'SINGLE_CHOICE',
                   'content', '<p>Reading child</p>', 'body', c.body, 'points', s.points,
                   'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.subject_code, s.points, s.child_count, s.n, s.subject_id, s.grade_id, s.level_id
)
INSERT INTO seed_question_rows
SELECT * FROM parents
UNION ALL
SELECT child_id, 'SEED-2B-GENERIC-READING-' || subject_code || '-' || points || '-' || child_count || '-' || n || '-' || pos,
       'SINGLE_CHOICE', subject_id, grade_id, level_id, NULL, points,
       '<p>Reading child</p>', body, id, pos
FROM children;

-- Tag-filtered stand-alone items for each named question-form group.
INSERT INTO seed_question_rows
SELECT md5('thi360-2b-tagged-' || t.purpose || '-' || p.points || '-' || n)::uuid,
       'SEED-2B-TAGGED-' || t.purpose || '-' || p.points || '-' || n,
       'SINGLE_CHOICE', subject.id, grade.id, level.id, NULL, p.points,
       '<p>Language example ' || n || '</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
           jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                             jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))),
       NULL, NULL
FROM seed_2b_tags t
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008' AND tag.code = t.code
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002'
    AND subject.code = CASE WHEN starts_with(t.purpose, 'english_') THEN 'ANH' ELSE 'VAN' END
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
CROSS JOIN (VALUES (1::numeric), (0.75::numeric), (0.25::numeric), (0.2::numeric)) p(points)
CROSS JOIN generate_series(1, 15) n
WHERE starts_with(t.purpose, 'english_')
   OR t.purpose IN ('literature_odd_word', 'literature_word_error', 'literature_word_pair', 'literature_single_passage');

-- Reading and cloze groups select whole passages with the required child count.
WITH sets AS (
    SELECT t.purpose, p.points, sz.child_count, n, subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id,
           md5('thi360-2b-english-set-' || t.purpose || '-' || p.points || '-' || sz.child_count || '-' || n)::uuid AS id
    FROM seed_2b_tags t
    JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008' AND tag.code = t.code
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'ANH'
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
    CROSS JOIN (VALUES (1::numeric), (0.75::numeric), (0.25::numeric), (0.2::numeric)) p(points)
    CROSS JOIN LATERAL unnest(CASE WHEN t.purpose = 'english_sentence_cloze'
        THEN ARRAY[5, 6, 8, 10] ELSE ARRAY[5, 6, 8] END) sz(child_count)
    CROSS JOIN generate_series(1, 2) n
    WHERE starts_with(t.purpose, 'english_')
), children AS (
    SELECT s.*, pos, md5(s.id::text || '-' || pos)::uuid AS child_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))) AS body
    FROM sets s CROSS JOIN LATERAL generate_series(0, s.child_count - 1) pos
), parents AS (
    SELECT s.id, 'SEED-2B-ENGLISH-SET-' || s.purpose || '-' || s.points || '-' || s.child_count || '-' || s.n AS code,
           'QUESTION_SET' AS type, s.subject_id, s.grade_id, s.level_id, NULL::uuid AS task_type_id,
           s.points * s.child_count AS points, '<p>Reading set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>Reading context ' || s.n || '</p>', 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', 'SINGLE_CHOICE',
                   'content', '<p>Reading child</p>', 'body', c.body, 'points', s.points,
                   'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.purpose, s.points, s.child_count, s.n, s.subject_id, s.grade_id, s.level_id
)
INSERT INTO seed_question_rows
SELECT * FROM parents
UNION ALL
SELECT child_id, 'SEED-2B-ENGLISH-SET-' || purpose || '-' || points || '-' || child_count || '-' || n || '-' || pos,
       'SINGLE_CHOICE', subject_id, grade_id, level_id, NULL, points,
       '<p>Reading child</p>', body, id, pos
FROM children;

-- SPT word formation requires typed blanks rather than choice children.
INSERT INTO seed_question_rows
SELECT md5('thi360-2b-word-formation-' || n)::uuid, 'SEED-2B-WORD-FORMATION-' || n,
       'CLOZE', subject.id, grade.id, level.id, NULL, 1, '<p>Word formation</p>',
       jsonb_build_object('type', 'CLOZE', 'passage', '<p>Text [[a]] [[b]] [[c]] [[d]]</p>',
           'blanks', jsonb_build_array(
               jsonb_build_object('id', 'a', 'answers', jsonb_build_array('one'), 'points', 0.25),
               jsonb_build_object('id', 'b', 'answers', jsonb_build_array('two'), 'points', 0.25),
               jsonb_build_object('id', 'c', 'answers', jsonb_build_array('three'), 'points', 0.25),
               jsonb_build_object('id', 'd', 'answers', jsonb_build_array('four'), 'points', 0.25))),
       NULL, NULL
FROM generate_series(1, 3) n
JOIN seed_2b_tags t ON t.purpose = 'english_word_formation'
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008' AND tag.code = t.code
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'ANH'
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH';

-- Named cross-subject data groups also use provisional catalog tags.
INSERT INTO seed_question_rows
SELECT md5('thi360-2b-topic-' || t.purpose || '-' || p.points || '-' || n)::uuid,
       'SEED-2B-TOPIC-' || t.purpose || '-' || p.points || '-' || n,
       'SINGLE_CHOICE', subject.id, grade.id, level.id, NULL, p.points, '<p>Data example 2 4 6</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
           jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                             jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))),
       NULL, NULL
FROM (VALUES ('math_synthesis', 'TOAN'), ('history_source', 'SU'), ('geography_skill', 'DIA')) spec(purpose, subject_code)
JOIN seed_2b_tags t ON t.purpose = spec.purpose
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008' AND tag.code = t.code
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = spec.subject_code
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
CROSS JOIN (VALUES (1::numeric), (0.25::numeric), (0.2::numeric)) p(points)
CROSS JOIN generate_series(1, 12) n;

INSERT INTO seed_question_rows
SELECT md5('thi360-2b-topic-geo-tf-' || n)::uuid,
       'SEED-2B-TOPIC-geography_skill-tf-' || n,
       'TRUE_FALSE', subject.id, grade.id, level.id, NULL, 1, '<p>Data example 2 4 6</p>',
       jsonb_build_object('type', 'TRUE_FALSE', 'scoringMethod', 'COUNT_TABLE',
           'scoreTable', jsonb_build_array(0.1, 0.25, 0.5, 1),
           'statements', jsonb_build_array(
               jsonb_build_object('id', 'a', 'content', '<p>First</p>', 'answer', true),
               jsonb_build_object('id', 'b', 'content', '<p>Second</p>', 'answer', false),
               jsonb_build_object('id', 'c', 'content', '<p>Third</p>', 'answer', true),
               jsonb_build_object('id', 'd', 'content', '<p>Fourth</p>', 'answer', false))),
       NULL, NULL
FROM generate_series(1, 6) n
JOIN seed_2b_tags t ON t.purpose = 'geography_skill'
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008' AND tag.code = t.code
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'DIA'
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH';

INSERT INTO seed_2b_question_tags (question_id, tag_item_id)
SELECT q.id, tag.id
FROM seed_question_rows q
JOIN seed_2b_tags t ON starts_with(q.code, 'SEED-2B-TAGGED-' || t.purpose || '-')
    OR starts_with(q.code, 'SEED-2B-ENGLISH-SET-' || t.purpose || '-')
    OR (starts_with(q.code, 'SEED-2B-WORD-FORMATION-') AND t.purpose = 'english_word_formation')
    OR starts_with(q.code, 'SEED-2B-TOPIC-' || t.purpose || '-')
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008' AND tag.code = t.code
ON CONFLICT DO NOTHING;

-- Tagged 25-point literature essays keep the BCA essay block usable; the new
-- rows also tag on databases seeded before the unmarked essays existed.
INSERT INTO seed_question_rows
SELECT md5('thi360-2b-essay-van-' || n)::uuid, 'SEED-2B-ESSAY-VAN-' || n,
       'ESSAY', subject.id, grade.id, level.id, NULL, 25, '<p>Social argumentation essay</p>',
       jsonb_build_object('type', 'ESSAY', 'gradingGuide', '<p>Give credit for a clear argument.</p>',
                          'rubric', '[]'::jsonb, 'wordLimit', 500),
       NULL, NULL
FROM generate_series(1, 3) n
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'VAN'
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH';

-- The BCA essay block filters the social-argumentation text-type tag on the
-- 25-point literature essays.
INSERT INTO seed_2b_question_tags (question_id, tag_item_id)
SELECT q.id, tag.id
FROM seed_question_rows q
JOIN seed_2b_tags t ON t.purpose = 'literature_social'
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008' AND tag.code = t.code
WHERE starts_with(q.code, 'SEED-ESSAY-') OR starts_with(q.code, 'SEED-2B-ESSAY-VAN-')
ON CONFLICT DO NOTHING;

-- Remaining formats and lifecycle examples, including a draft and a retired
-- question for each format. READY rows use the same validation as real content.
INSERT INTO seed_question_rows
SELECT md5('thi360-seed-extra-' || kind.type || '-' || n)::uuid,
       'SEED-EXTRA-' || kind.type || '-' || n, kind.type,
       subject.id, grade.id, level.id, NULL,
       CASE WHEN kind.type IN ('CLOZE', 'MATCHING') THEN 2 ELSE 1 END,
       '<p>Extra example ' || n || '</p>',
       CASE kind.type
           WHEN 'MULTIPLE_CHOICE' THEN jsonb_build_object('type', kind.type, 'scoringRule', 'ALL_OR_NOTHING',
               'options', jsonb_build_array(
                   jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                   jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', true),
                   jsonb_build_object('id', 'c', 'content', '<p>Three</p>', 'correct', false)))
           WHEN 'CLOZE' THEN jsonb_build_object('type', kind.type, 'passage', '<p>Text [[x]] [[y]]</p>',
               'blanks', jsonb_build_array(jsonb_build_object('id', 'x', 'answers', jsonb_build_array('one'), 'points', 1),
                                           jsonb_build_object('id', 'y', 'answers', jsonb_build_array('two'), 'points', 1)))
           ELSE jsonb_build_object('type', kind.type, 'reuseRight', false,
               'left', jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>Left one</p>'),
                                         jsonb_build_object('id', 'b', 'content', '<p>Left two</p>')),
               'right', jsonb_build_array(jsonb_build_object('id', 'c', 'content', '<p>Right one</p>'),
                                          jsonb_build_object('id', 'd', 'content', '<p>Right two</p>')),
               'pairs', jsonb_build_array(jsonb_build_object('leftId', 'a', 'rightId', 'c', 'points', 1),
                                          jsonb_build_object('leftId', 'b', 'rightId', 'd', 'points', 1))) END,
       NULL, NULL
FROM (VALUES ('MULTIPLE_CHOICE'), ('CLOZE'), ('MATCHING')) kind(type)
CROSS JOIN generate_series(1, 3) n
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'TOAN'
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G10'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'NB';

-- Stand-alone sample sources for the retained V-ACT, THPTQG and SP subjects.
INSERT INTO seed_question_rows
SELECT md5('thi360-2b-general-' || subject.code || '-' || kind.type || '-' || kind.points || '-' || n)::uuid,
       'SEED-2B-GENERAL-' || subject.code || '-' || kind.type || '-' || kind.points || '-' || n,
       kind.type, subject.id, grade.id, level.id, NULL, kind.points,
       '<p>Practice example</p>',
       CASE kind.type
           WHEN 'SINGLE_CHOICE' THEN jsonb_build_object('type', kind.type, 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false)))
           WHEN 'MULTIPLE_CHOICE' THEN jsonb_build_object('type', kind.type, 'scoringRule', 'ALL_OR_NOTHING',
               'options', jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                            jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', true),
                                            jsonb_build_object('id', 'c', 'content', '<p>Three</p>', 'correct', false)))
           WHEN 'SHORT_ANSWER' THEN jsonb_build_object('type', kind.type, 'answers', jsonb_build_array('1'), 'inputHint', '')
           WHEN 'ESSAY' THEN jsonb_build_object('type', kind.type, 'gradingGuide', '<p>Give credit for a clear argument.</p>',
                                               'rubric', '[]'::jsonb, 'wordLimit', 500)
           ELSE jsonb_build_object('type', 'TRUE_FALSE', 'scoringMethod', 'COUNT_TABLE',
               'scoreTable', jsonb_build_array(0.1, 0.25, 0.5, 1),
               'statements', jsonb_build_array(
                   jsonb_build_object('id', 'a', 'content', '<p>First</p>', 'answer', true),
                   jsonb_build_object('id', 'b', 'content', '<p>Second</p>', 'answer', false),
                   jsonb_build_object('id', 'c', 'content', '<p>Third</p>', 'answer', true),
                   jsonb_build_object('id', 'd', 'content', '<p>Fourth</p>', 'answer', false))) END,
       NULL, NULL
FROM catalog.catalog_item subject
CROSS JOIN (VALUES ('SINGLE_CHOICE', 0.25::numeric, 40), ('SINGLE_CHOICE', 0.2::numeric, 32),
                   ('MULTIPLE_CHOICE', 0.25::numeric, 8), ('SHORT_ANSWER', 0.5::numeric, 8),
                   ('SHORT_ANSWER', 0.3::numeric, 8), ('ESSAY', 1::numeric, 2),
                   ('ESSAY', 2::numeric, 2), ('ESSAY', 4::numeric, 2), ('TRUE_FALSE', 1::numeric, 6))
    kind(type, points, quantity)
CROSS JOIN LATERAL generate_series(1, kind.quantity) n
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002'
  AND subject.code IN ('TOAN', 'VAN', 'ANH', 'LY', 'HOA', 'SINH', 'SU', 'DIA',
                       'ECON_LAW', 'TECH_INDUSTRY', 'TECH_AGRICULTURE', 'INFORMATICS');

-- Legacy SP2E science scores each of four statements independently.
INSERT INTO seed_question_rows
SELECT md5('thi360-2b-sp2e-tf-' || subject.code || '-' || n)::uuid,
       'SEED-2B-SP2E-TF-' || subject.code || '-' || n,
       'TRUE_FALSE', subject.id, grade.id, level.id, NULL, 0.8,
       '<p>Science true or false</p>',
       jsonb_build_object('type', 'TRUE_FALSE', 'scoringMethod', 'PER_STATEMENT',
           'statements', jsonb_build_array(
               jsonb_build_object('id', 'a', 'content', '<p>First</p>', 'answer', true, 'points', 0.2),
               jsonb_build_object('id', 'b', 'content', '<p>Second</p>', 'answer', false, 'points', 0.2),
               jsonb_build_object('id', 'c', 'content', '<p>Third</p>', 'answer', true, 'points', 0.2),
               jsonb_build_object('id', 'd', 'content', '<p>Fourth</p>', 'answer', false, 'points', 0.2))),
       NULL, NULL
FROM catalog.catalog_item subject
CROSS JOIN generate_series(1, 6) n
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002'
  AND subject.code IN ('LY', 'HOA', 'SINH', 'SU');

-- Three-child case sets at 0.25 per unit cover the SP2E economic-law blocks.
WITH sets AS (
    SELECT subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id, n,
           md5('thi360-2b-case-set-' || n)::uuid AS id
    FROM generate_series(1, 4) n
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002'
        AND subject.code = 'ECON_LAW'
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
), children AS (
    SELECT s.*, pos, md5(s.id::text || '-' || pos)::uuid AS child_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))) AS body
    FROM sets s CROSS JOIN generate_series(0, 2) pos
), parents AS (
    SELECT s.id, 'SEED-2B-CASE-SET-' || s.n AS code, 'QUESTION_SET' AS type,
           s.subject_id, s.grade_id, s.level_id, NULL::uuid AS task_type_id,
           0.75::numeric AS points, '<p>Case set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>Case context</p>', 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', 'SINGLE_CHOICE',
                   'content', '<p>Case child</p>', 'body', c.body, 'points', 0.25,
                   'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.n, s.subject_id, s.grade_id, s.level_id
)
INSERT INTO seed_question_rows
SELECT * FROM parents
UNION ALL
SELECT child_id, 'SEED-2B-CASE-SET-' || n || '-' || pos, 'SINGLE_CHOICE',
       subject_id, grade_id, level_id, NULL, 0.25, '<p>Case child</p>', body, id, pos
FROM children;

INSERT INTO seed_question_rows
SELECT md5('thi360-2b-general-matching-' || subject.code || '-' || n)::uuid,
       'SEED-2B-MATCHING-GENERAL-' || subject.code || '-' || n,
       'MATCHING', subject.id, grade.id, level.id, NULL, 1, '<p>Matching example</p>',
       jsonb_build_object('type', 'MATCHING', 'reuseRight', false,
           'left', (SELECT jsonb_agg(jsonb_build_object('id', 'l' || i, 'content', '<p>Left one</p>') ORDER BY i)
                    FROM generate_series(1, 4) i),
           'right', (SELECT jsonb_agg(jsonb_build_object('id', 'r' || i, 'content', '<p>Right one</p>') ORDER BY i)
                     FROM generate_series(1, 6) i),
           'pairs', (SELECT jsonb_agg(jsonb_build_object('leftId', 'l' || i, 'rightId', 'r' || i, 'points', 0.25) ORDER BY i)
                     FROM generate_series(1, 4) i)),
       NULL, NULL
FROM catalog.catalog_item subject
CROSS JOIN generate_series(1, 6) n
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002'
  AND subject.code IN ('LY', 'HOA', 'SINH', 'SU', 'DIA', 'ECON_LAW');

-- Explicit answer formats and matching shapes for the SP templates.
INSERT INTO seed_question_rows
SELECT md5('thi360-2b-format-' || subject.code || '-' || format.code || '-' || p.points || '-' || n)::uuid,
       'SEED-2B-FORMAT-' || subject.code || '-' || format.code || '-' || p.points || '-' || n,
       'SHORT_ANSWER', subject.id, grade.id, level.id, NULL, p.points,
       '<p>Format example</p>',
       jsonb_build_object('type', 'SHORT_ANSWER', 'answerFormat', format.code,
                          'answers', jsonb_build_array(format.answer), 'inputHint', ''),
       NULL, NULL
FROM catalog.catalog_item subject
CROSS JOIN (VALUES ('INTEGER', '1'), ('DECIMAL', '1.5'), ('FRACTION', '1/2'), ('CHEM_FORMULA', 'H2O')) format(code, answer)
CROSS JOIN (VALUES (1::numeric), (0.25::numeric), (0.2::numeric)) p(points)
CROSS JOIN generate_series(1, 6) n
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002'
  AND subject.code IN ('TOAN', 'LY', 'HOA', 'SINH', 'DIA', 'TECH_INDUSTRY', 'TECH_AGRICULTURE');

INSERT INTO seed_question_rows
SELECT md5('thi360-2b-science-short-' || subject.code || '-' || grade.code || '-' || level.code || '-' || n)::uuid,
       'SEED-2B-SCIENCE-SHORT-' || subject.code || '-' || grade.code || '-' || level.code || '-' || n,
       'SHORT_ANSWER', subject.id, grade.id, level.id, NULL, 1, '<p>Science short answer</p>',
       jsonb_build_object('type', 'SHORT_ANSWER', 'answerFormat', 'INTEGER',
                          'answers', jsonb_build_array('1'), 'inputHint', ''),
       NULL, NULL
FROM catalog.catalog_item subject
CROSS JOIN catalog.catalog_item grade
CROSS JOIN catalog.catalog_item level
CROSS JOIN generate_series(1, 2) n
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code IN ('LY', 'HOA', 'SINH')
  AND grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code IN ('G10', 'G11', 'G12')
  AND level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code IN ('NB', 'TH', 'VD', 'VDC');

INSERT INTO seed_question_rows
SELECT md5('thi360-2b-matching-' || subject.code || '-' || shape.left_count || '-' || n)::uuid,
       'SEED-2B-MATCHING-' || subject.code || '-' || shape.left_count || '-' || n,
       'MATCHING', subject.id, grade.id, level.id, NULL, shape.left_count * 0.2,
       '<p>Matching example</p>',
       jsonb_build_object('type', 'MATCHING', 'reuseRight', false,
           'left', (SELECT jsonb_agg(jsonb_build_object('id', 'l' || i, 'content', '<p>Left one</p>') ORDER BY i)
                    FROM generate_series(1, shape.left_count) i),
           'right', (SELECT jsonb_agg(jsonb_build_object('id', 'r' || i, 'content', '<p>Right one</p>') ORDER BY i)
                     FROM generate_series(1, shape.right_count) i),
           'pairs', (SELECT jsonb_agg(jsonb_build_object('leftId', 'l' || i, 'rightId', 'r' || i, 'points', 0.2) ORDER BY i)
                     FROM generate_series(1, shape.left_count) i)),
       NULL, NULL
FROM catalog.catalog_item subject
CROSS JOIN (VALUES (4, 6), (3, 5)) shape(left_count, right_count)
CROSS JOIN generate_series(1, 6) n
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002'
  AND subject.code IN ('TOAN', 'LY', 'HOA', 'SINH', 'SU', 'DIA');

INSERT INTO seed_question_rows
SELECT md5('thi360-2b-order-' || kind.type)::uuid, 'SEED-2B-ORDER-' || kind.type,
       kind.type, subject.id, grade.id, level.id, NULL, 1, '<p>Ordered example</p>',
       CASE WHEN kind.type = 'SINGLE_CHOICE' THEN
           jsonb_build_object('type', kind.type, 'shuffleOptions', false, 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false)))
       ELSE jsonb_build_object('type', kind.type, 'shuffleOptions', false, 'scoringRule', 'ALL_OR_NOTHING',
               'options', jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                            jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', true),
                                            jsonb_build_object('id', 'c', 'content', '<p>Three</p>', 'correct', false))) END,
       NULL, NULL
FROM (VALUES ('SINGLE_CHOICE'), ('MULTIPLE_CHOICE')) kind(type)
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'TOAN'
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH';

-- ADV:BE-10:1: inline data, never create object-store metadata without bytes.
INSERT INTO seed_question_rows
SELECT md5('thi360-2b-inline-' || example.subject_code)::uuid, 'SEED-2B-INLINE-' || example.subject_code,
       'SINGLE_CHOICE', subject.id, grade.id, level.id, NULL, 1, '<p>' || example.content || '</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
           jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                             jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))),
       NULL, NULL
FROM (VALUES ('HOA', 'Atomic mass table: H 1, C 12, O 16'),
             ('SINH', 'Pedigree: parents Aa and Aa, child aa'),
             ('DIA', 'Chart: year 2020 is 10, year 2021 is 15')) example(subject_code, content)
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = example.subject_code
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH';

-- Each child has its own MAPID, making position-based conditions observable.
WITH sets AS (
    SELECT n, md5('thi360-2b-mapid-' || n)::uuid AS id,
           subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id
    FROM generate_series(1, 3) n
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'TOAN'
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G10'
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
    WHERE (SELECT count(*) FROM catalog.catalog_item
           WHERE group_id = '00000000-0000-7000-8100-000000000009'
             AND code IN ('0D1-1-1', '0D1-1-2', '0D1-1-3')) = 3
), children AS (
    SELECT s.*, pos, md5(s.id::text || '-' || pos)::uuid AS child_id, node.id AS node_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))) AS body
    FROM sets s CROSS JOIN generate_series(0, 2) pos
    JOIN catalog.catalog_item node ON node.group_id = '00000000-0000-7000-8100-000000000009'
        AND node.code = (ARRAY['0D1-1-1', '0D1-1-2', '0D1-1-3'])[pos + 1]
), parents AS (
    SELECT s.id, 'SEED-2B-MAPID-' || s.n AS code, 'QUESTION_SET' AS type,
           s.subject_id, s.grade_id, s.level_id, NULL::uuid AS task_type_id, 3::numeric AS points,
           '<p>Mapid set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>Mapid context</p>', 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', 'SINGLE_CHOICE',
                   'content', '<p>Mapid child</p>', 'body', c.body, 'points', 1,
                   'knowledgeItemIds', jsonb_build_array(c.node_id)) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.n, s.subject_id, s.grade_id, s.level_id
)
INSERT INTO seed_question_rows
SELECT * FROM parents
UNION ALL
SELECT child_id, 'SEED-2B-MAPID-' || n || '-' || pos, 'SINGLE_CHOICE',
       subject_id, grade_id, level_id, NULL, 1, '<p>Mapid child</p>', body, id, pos
FROM children;

INSERT INTO seed_2b_knowledge (question_id, knowledge_item_id)
SELECT q.id, node.id
FROM seed_question_rows q
JOIN catalog.catalog_item node ON node.group_id = '00000000-0000-7000-8100-000000000009'
    AND node.code = (ARRAY['0D1-1-1', '0D1-1-2', '0D1-1-3'])[q.position + 1]
WHERE q.parent_id IS NOT NULL AND starts_with(q.code, 'SEED-2B-MAPID-');

-- Three-child science passages across grade/difficulty cells complement the
-- existing independent questions for the HSA, QDA and BCA subject matrices.
WITH sets AS (
    SELECT subject.code AS subject_code, grade.code AS grade_code, level.code AS level_code, n,
           subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id,
           md5('thi360-2b-science-' || subject.code || '-' || grade.code || '-' || level.code || '-' || n)::uuid AS id
    FROM catalog.catalog_item subject
    CROSS JOIN catalog.catalog_item grade
    CROSS JOIN catalog.catalog_item level
    CROSS JOIN generate_series(1, 2) n
    WHERE subject.group_id = '00000000-0000-7000-8100-000000000002'
      AND subject.code IN ('LY', 'HOA', 'SINH', 'SU', 'DIA')
      AND grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code IN ('G10', 'G11', 'G12')
      AND level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code IN ('NB', 'TH', 'VD', 'VDC')
), children AS (
    SELECT s.*, pos, md5(s.id::text || '-' || pos)::uuid AS child_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))) AS body
    FROM sets s CROSS JOIN generate_series(0, 2) pos
), parents AS (
    SELECT s.id, 'SEED-2B-SCIENCE-' || s.subject_code || '-' || s.grade_code || '-' || s.level_code || '-' || s.n AS code,
           'QUESTION_SET' AS type, s.subject_id, s.grade_id, s.level_id, NULL::uuid AS task_type_id,
           3::numeric AS points, '<p>Science set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>Science context</p>', 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', 'SINGLE_CHOICE',
                   'content', '<p>Science child</p>', 'body', c.body, 'points', 1,
                   'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.subject_code, s.grade_code, s.level_code, s.n, s.subject_id, s.grade_id, s.level_id
)
INSERT INTO seed_question_rows
SELECT * FROM parents
UNION ALL
SELECT child_id, 'SEED-2B-SCIENCE-' || subject_code || '-' || grade_code || '-' || level_code || '-' || n || '-' || pos,
       'SINGLE_CHOICE', subject_id, grade_id, level_id, NULL, 1, '<p>Science child</p>', body, id, pos
FROM children;

INSERT INTO seed_2b_question_tags (question_id, tag_item_id)
SELECT q.id, tag.id
FROM seed_question_rows q
JOIN seed_2b_tags t ON t.purpose = 'history_source'
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008' AND tag.code = t.code
WHERE starts_with(q.code, 'SEED-2B-SCIENCE-SU-')
ON CONFLICT DO NOTHING;

-- Resolve each D9 purpose by code, not by its canonical UUID, so an unrelated
-- catalog item at that UUID cannot classify a generated question. Keep the
-- slot in the spec for stable generated IDs and resolve again on each seed run.
CREATE TEMP TABLE seed_be14_tag_slots (slot uuid PRIMARY KEY, code text NOT NULL) ON COMMIT DROP;
INSERT INTO seed_be14_tag_slots (slot, code)
WITH slots(ordinal, code) AS (
    VALUES
        (41, 'ANH_PHAT_HIEN_LOI'),
        (42, 'ANH_DIEN_DAT_TUONG_DUONG'),
        (43, 'TD_SAP_XEP_DIEU_KIEN'),
        (44, 'TD_XEP_HANG'),
        (45, 'TD_BANG_SO_LIEU'),
        (46, 'TD_BIEU_DO')
)
SELECT ('00000000-0000-7000-8601-' || lpad(ordinal::text, 12, '0'))::uuid, code FROM slots;

WITH singles AS (
SELECT spec.*, tag.code AS tag_code, n, subject.id AS subject_id,
       grade.id AS grade_id, level.id AS level_id,
       md5('thi360-be14-single-' || spec.competency || '-' || COALESCE(spec.declared_tag, spec.tag_slot, 'NONE') || '-' || n)::uuid AS id
FROM (VALUES
    ('VIETNAMESE', 'VACT-NN-TV', NULL, NULL, 10::numeric, 30),
    ('ANH', 'VACT-NN-TA', 'ANH_HOAN_THANH_CAU', NULL, 10::numeric, 5),
    ('ANH', 'VACT-NN-TA', NULL, '00000000-0000-7000-8601-000000000041', 10::numeric, 5),
    ('ANH', 'VACT-NN-TA', NULL, '00000000-0000-7000-8601-000000000042', 10::numeric, 5),
    ('TOAN', 'VACT-TOAN', NULL, NULL, 10::numeric, 12),
    ('ANH', 'THPT', 'ANH_SAP_XEP_HOI_THOAI', NULL, 0.25::numeric, 5)
) spec(subject, competency, declared_tag, tag_slot, points, quantity)
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = spec.subject
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
LEFT JOIN seed_be14_tag_slots slot ON slot.slot = spec.tag_slot::uuid
LEFT JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008'
    AND tag.code_normalized = lower(COALESCE(spec.declared_tag, slot.code))
CROSS JOIN LATERAL generate_series(1, spec.quantity) n
), classified AS (
    INSERT INTO seed_be14_classification
    SELECT id, competency, tag_code FROM singles
)
INSERT INTO seed_question_rows
SELECT id,
       'SEED-BE14-' || competency || '-' || COALESCE(tag_code, declared_tag, tag_slot, 'NONE') || '-' || n,
       'SINGLE_CHOICE', subject_id, grade_id, level_id, NULL, points,
       '<p>Sample ' || subject || ' question ' || n || ': choose the fitting answer.</p>',
       jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
           jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                             jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))),
       NULL, NULL
FROM singles;

-- Whole passages are separate roots; child points are the filterable score.
WITH sets AS (
    SELECT spec.*, tag.code AS tag_code, n, subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id,
           md5('thi360-be14-set-' || spec.competency || '-' || COALESCE(spec.declared_tag, spec.tag_slot, 'NONE') || '-' || spec.child_count || '-' || n)::uuid AS id,
           'SEED-BE14-' || spec.competency || '-' || COALESCE(tag.code, spec.declared_tag, spec.tag_slot, 'NONE') || '-' || spec.child_count || '-' || n AS code
    FROM (VALUES
        ('ANH', 'THPT', 'ANH_DOC_DIEN', NULL, 0.25::numeric, 6, 2),
        ('ANH', 'THPT', 'ANH_DOC_DIEN_CAU', NULL, 0.25::numeric, 5, 1),
        ('ANH', 'THPT', 'ANH_DOC_HIEU', NULL, 0.25::numeric, 8, 1),
        ('ANH', 'THPT', 'ANH_DOC_HIEU', NULL, 0.25::numeric, 10, 1),
        ('ANH', 'VACT-NN-TA', 'ANH_DOC_HIEU', NULL, 10::numeric, 7, 1),
        ('ANH', 'VACT-NN-TA', 'ANH_DOC_HIEU', NULL, 10::numeric, 8, 1),
        ('TOAN', 'VACT-TOAN', NULL, NULL, 10::numeric, 3, 6),
        ('SCIENTIFIC_THINKING', 'VACT-TD-LOGIC', NULL, '00000000-0000-7000-8601-000000000043', 10::numeric, 3, 1),
        ('SCIENTIFIC_THINKING', 'VACT-TD-LOGIC', NULL, '00000000-0000-7000-8601-000000000044', 10::numeric, 3, 1),
        ('SCIENTIFIC_THINKING', 'VACT-TD-LOGIC', NULL, '00000000-0000-7000-8601-000000000045', 10::numeric, 3, 1),
        ('SCIENTIFIC_THINKING', 'VACT-TD-LOGIC', NULL, '00000000-0000-7000-8601-000000000046', 10::numeric, 3, 1),
        ('SCIENTIFIC_THINKING', 'VACT-TD-SL', NULL, NULL, 10::numeric, 3, 6)
    ) spec(subject, competency, declared_tag, tag_slot, points, child_count, quantity)
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = spec.subject
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
    LEFT JOIN seed_be14_tag_slots slot ON slot.slot = spec.tag_slot::uuid
    LEFT JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008'
        AND tag.code_normalized = lower(COALESCE(spec.declared_tag, slot.code))
    CROSS JOIN LATERAL generate_series(1, spec.quantity) n
), children AS (
    SELECT s.*, pos, md5(s.id::text || '-' || pos)::uuid AS child_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>One</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Two</p>', 'correct', false))) AS child_body
    FROM sets s CROSS JOIN LATERAL generate_series(0, s.child_count - 1) pos
), classified AS (
    INSERT INTO seed_be14_classification
    SELECT id, competency, tag_code FROM sets
    UNION ALL
    SELECT child_id, competency, tag_code FROM children
), parents AS (
    SELECT s.id, s.code, 'QUESTION_SET' AS type, s.subject_id, s.grade_id, s.level_id,
           NULL::uuid AS task_type_id, s.points * s.child_count AS points,
           '<p>Sample set</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET',
               'passage', '<p>Sample context for ' || s.subject || ' set ' || s.n || '.</p>',
               'children', jsonb_agg(jsonb_build_object('id', c.child_id, 'type', 'SINGLE_CHOICE',
                   'content', '<p>Sample child</p>', 'body', c.child_body, 'points', s.points,
                   'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.code, s.subject, s.n, s.subject_id, s.grade_id, s.level_id, s.points, s.child_count
)
INSERT INTO seed_question_rows
SELECT * FROM parents
UNION ALL
SELECT child_id, code || '-' || pos, 'SINGLE_CHOICE', subject_id, grade_id, level_id, NULL,
       points, '<p>Sample child</p>', child_body, id, pos FROM children;

INSERT INTO seed_question_rows
SELECT md5('thi360-be14-tf-' || subject.code || '-' || n)::uuid,
       'SEED-BE14-THPT-TF-' || subject.code || '-' || n, 'TRUE_FALSE',
       subject.id, grade.id, level.id, NULL, 1, '<p>Sample true or false ' || n || '.</p>',
       jsonb_build_object('type', 'TRUE_FALSE', 'scoringMethod', 'COUNT_TABLE',
           'scoreTable', jsonb_build_array(0.1, 0.25, 0.5, 1),
           'statements', jsonb_build_array(
               jsonb_build_object('id', 'a', 'content', '<p>First</p>', 'answer', true),
               jsonb_build_object('id', 'b', 'content', '<p>Second</p>', 'answer', false),
               jsonb_build_object('id', 'c', 'content', '<p>Third</p>', 'answer', true),
               jsonb_build_object('id', 'd', 'content', '<p>Fourth</p>', 'answer', false))),
       NULL, NULL
FROM catalog.catalog_item subject
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
CROSS JOIN generate_series(1, 4) n
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002'
  AND subject.code IN ('TOAN', 'LY', 'HOA', 'SINH', 'DIA', 'SU', 'ECON_LAW', 'TECH_INDUSTRY', 'TECH_AGRICULTURE');

INSERT INTO seed_2b_question_tags (question_id, tag_item_id)
SELECT m.question_id, tag.id
FROM seed_be14_classification m
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008'
    AND tag.code = m.tag_code
ON CONFLICT DO NOTHING;

-- Parents must exist before their child rows satisfy the self-reference.
WITH prepared AS (
    SELECT s.*,
           CASE WHEN code LIKE 'SEED-EXTRA-%-2' THEN 'DRAFT'
                WHEN code LIKE 'SEED-EXTRA-%-3' THEN 'RETIRED' ELSE 'READY' END AS seed_status,
           CASE type
               WHEN 'SINGLE_CHOICE' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' One Two'
               WHEN 'SHORT_ANSWER' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' ' || (body->'answers'->>0)
               WHEN 'MULTIPLE_CHOICE' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' One Two Three'
               WHEN 'CLOZE' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' '
                   || regexp_replace(body->>'passage', '<[^>]+>', '', 'g')
               WHEN 'MATCHING' THEN CASE WHEN starts_with(code, 'SEED-2B-MATCHING-')
                   THEN regexp_replace(content, '<[^>]+>', '', 'g')
                       || repeat(' Left one', jsonb_array_length(body->'left'))
                       || repeat(' Right one', jsonb_array_length(body->'right'))
                   ELSE regexp_replace(content, '<[^>]+>', '', 'g') || ' Left one Left two Right one Right two' END
               WHEN 'ESSAY' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' Give credit for a clear argument.'
               WHEN 'TRUE_FALSE' THEN regexp_replace(content, '<[^>]+>', '', 'g') || ' First Second Third Fourth'
               WHEN 'QUESTION_SET' THEN CASE
                   WHEN code LIKE 'SEED-BE14-%'
                       THEN 'Sample set ' || regexp_replace(body->>'passage', '<[^>]+>', '', 'g')
                           || repeat(' Sample child One Two', jsonb_array_length(body->'children'))
                   WHEN code LIKE 'SEED-2B-LITERATURE-%' OR code LIKE 'SEED-2B-ENGLISH-SET-%'
                       OR code LIKE 'SEED-2B-GENERIC-READING-%'
                       THEN 'Reading set ' || regexp_replace(body->>'passage', '<[^>]+>', '', 'g')
                           || repeat(' Reading child One Two', jsonb_array_length(body->'children'))
                   WHEN code LIKE 'SEED-2B-MAPID-%'
                       THEN 'Mapid set Mapid context Mapid child One Two Mapid child One Two Mapid child One Two'
                   WHEN code LIKE 'SEED-2B-SCIENCE-%'
                       THEN 'Science set Science context Science child One Two Science child One Two Science child One Two'
                   WHEN code LIKE 'SEED-2B-CASE-SET-%'
                       THEN 'Case set Case context Case child One Two Case child One Two Case child One Two'
                   WHEN code LIKE 'SEED-READING-%'
                       THEN 'Reading set Reading context Reading child One Two Reading child One Two'
                   WHEN code LIKE 'SEED-HSA-MATH-SET-%'
                       THEN 'HSA Math set HSA Math context HSA Math child One Two HSA Math child One Two'
                   ELSE 'Math set Math context Math child One Two Math child 1' END
           END AS plain
    FROM seed_question_rows s WHERE parent_id IS NULL
), inserted AS (
    INSERT INTO questionbank.question
        (id, code, type, status, parent_id, position, subject_item_id, grade_item_id,
         level_item_id, task_type_item_id, points, current_version_no, working_version_no,
         content_text, search_text)
    SELECT id, code, type, seed_status, NULL, NULL, subject_id, grade_id, level_id, task_type_id,
           points, CASE WHEN seed_status = 'DRAFT' THEN NULL ELSE 1 END,
           CASE WHEN seed_status = 'DRAFT' THEN 1 ELSE NULL END, plain, lower(code || ' ' || plain)
    FROM prepared
    ON CONFLICT DO NOTHING RETURNING id, subject_item_id, status
)
INSERT INTO seed_inserted_questions (id, subject_id, status)
SELECT id, subject_item_id, status FROM inserted;

INSERT INTO questionbank.question_source_revision (subject_item_id, revision)
SELECT subject_id, count(*) FROM seed_inserted_questions WHERE status = 'READY' GROUP BY subject_id
ON CONFLICT (subject_item_id) DO UPDATE
SET revision = questionbank.question_source_revision.revision + EXCLUDED.revision;

WITH inserted AS (
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
ON CONFLICT DO NOTHING RETURNING id
)
INSERT INTO seed_inserted_children (id) SELECT id FROM inserted;

INSERT INTO questionbank.question_knowledge (question_id, knowledge_item_id, is_primary)
SELECT k.question_id, k.knowledge_item_id, true FROM seed_2b_knowledge k
WHERE EXISTS (SELECT 1 FROM seed_inserted_questions i WHERE i.id = k.question_id)
   OR EXISTS (SELECT 1 FROM seed_inserted_children i WHERE i.id = k.question_id)
ON CONFLICT DO NOTHING;

-- Classify only rows inserted in this run; rerunning the seed must not overwrite
-- edits or serving versions of existing questions.
UPDATE questionbank.question q
SET competency_item_id = c.id
FROM seed_be14_classification m
JOIN catalog.catalog_item c ON c.group_id = '00000000-0000-7000-8100-00000000000a'
    AND c.code = m.competency_code AND c.status = 1
WHERE q.id = m.question_id
  AND (EXISTS (SELECT 1 FROM seed_inserted_questions i WHERE i.id = q.id)
       OR EXISTS (SELECT 1 FROM seed_inserted_children i WHERE i.id = q.id));

INSERT INTO questionbank.question_version (question_id, version_no, snapshot, ready_at)
SELECT s.id, 1, jsonb_build_object(
    'subjectItemId', s.subject_id, 'gradeItemId', s.grade_id, 'levelItemId', s.level_id,
    'taskTypeItemId', s.task_type_id, 'competencyItemId', q.competency_item_id, 'points', s.points,
    'knowledge', COALESCE((SELECT jsonb_agg(jsonb_build_object('itemId', k.knowledge_item_id, 'primary', true))
                           FROM seed_2b_knowledge k WHERE k.question_id = s.id), '[]'::jsonb),
    'tagItemIds', COALESCE(
        (SELECT jsonb_agg(t.tag_item_id ORDER BY t.tag_item_id)
         FROM seed_2b_question_tags t WHERE t.question_id = s.id), '[]'::jsonb), 'content', s.content,
    'solution', NULL, 'commonMistakes', '[]'::jsonb, 'attachmentIds', '[]'::jsonb, 'body', s.body),
    CASE WHEN q.status = 'DRAFT' THEN NULL ELSE now() END
FROM seed_question_rows s
JOIN questionbank.question q ON q.id = s.id
ON CONFLICT DO NOTHING;

INSERT INTO questionbank.question_tag (question_id, tag_item_id)
SELECT t.question_id, t.tag_item_id FROM seed_2b_question_tags t
WHERE EXISTS (SELECT 1 FROM seed_inserted_questions i WHERE i.id = t.question_id)
   OR EXISTS (SELECT 1 FROM seed_inserted_children i WHERE i.id = t.question_id)
ON CONFLICT DO NOTHING;

DROP TABLE seed_be14_classification;
