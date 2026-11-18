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

-- BEGIN BE-9 ECON_LAW SP2E: six true/false questions and two three-child case sets.
INSERT INTO seed_question_rows
SELECT md5('thi360-be9-econ-tf-' || n)::uuid, 'SEED-BE9-ECON-TF-' || n,
       'TRUE_FALSE', subject.id, grade.id, level.id, NULL, 0.8,
       '<p>Practice statement exercise</p>',
       jsonb_build_object('type', 'TRUE_FALSE', 'scoringMethod', 'PER_STATEMENT',
           'statements', jsonb_build_array(
               jsonb_build_object('id', 'a', 'content', '<p>Practice statement A</p>', 'answer', true, 'points', 0.2),
               jsonb_build_object('id', 'b', 'content', '<p>Practice statement B</p>', 'answer', false, 'points', 0.2),
               jsonb_build_object('id', 'c', 'content', '<p>Practice statement C</p>', 'answer', true, 'points', 0.2),
               jsonb_build_object('id', 'd', 'content', '<p>Practice statement D</p>', 'answer', false, 'points', 0.2))),
       NULL, NULL
FROM generate_series(1, 6) n
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'ECON_LAW'
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH';

WITH sets AS (
    SELECT subject.id AS subject_id, grade.id AS grade_id, level.id AS level_id, n,
           md5('thi360-be9-econ-case-' || n)::uuid AS id
    FROM generate_series(1, 2) n
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = 'ECON_LAW'
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
), children AS (
    SELECT s.*, pos, md5('thi360-be9-econ-case-child-' || n || '-' || pos)::uuid AS child_id,
           jsonb_build_object('type', 'SINGLE_CHOICE', 'options',
               jsonb_build_array(jsonb_build_object('id', 'a', 'content', '<p>Practice answer A</p>', 'correct', true),
                                 jsonb_build_object('id', 'b', 'content', '<p>Practice answer B</p>', 'correct', false))) AS body
    FROM sets s CROSS JOIN generate_series(0, 2) pos
), parents AS (
    SELECT s.id, 'SEED-BE9-ECON-CASE-' || s.n AS code, 'QUESTION_SET' AS type,
           s.subject_id, s.grade_id, s.level_id, NULL::uuid AS task_type_id,
           0.6::numeric AS points, '<p>Practice case</p>' AS content,
           jsonb_build_object('type', 'QUESTION_SET', 'passage', '<p>Practice case context</p>', 'children',
               jsonb_agg(jsonb_build_object('id', c.child_id, 'type', 'SINGLE_CHOICE',
                   'content', '<p>Practice case question</p>', 'body', c.body, 'points', 0.2,
                   'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)) AS body,
           NULL::uuid AS parent_id, NULL::integer AS position
    FROM sets s JOIN children c ON c.id = s.id
    GROUP BY s.id, s.n, s.subject_id, s.grade_id, s.level_id
)
INSERT INTO seed_question_rows
SELECT * FROM parents
UNION ALL
SELECT child_id, 'SEED-BE9-ECON-CASE-' || n || '-' || pos, 'SINGLE_CHOICE',
       subject_id, grade_id, level_id, NULL, 0.2, '<p>Practice case question</p>', body, id, pos
FROM children;
-- END BE-9 ECON_LAW SP2E.

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

-- D9 provisional codes: reconcile with R__seed_catalog_dev_data.sql for BA review.

WITH singles AS (
SELECT spec.*, tag.code AS tag_code, n, subject.id AS subject_id,
       grade.id AS grade_id, level.id AS level_id,
       md5('thi360-be14-single-' || spec.competency || '-' || COALESCE(spec.declared_tag, 'NONE') || '-' || n)::uuid AS id
FROM (VALUES
    ('VIETNAMESE', 'VACT-NN-TV', NULL, 10::numeric, 30),
    ('ANH', 'VACT-NN-TA', 'ANH_HOAN_THANH_CAU', 10::numeric, 5),
    ('ANH', 'VACT-NN-TA', 'ANH_PHAT_HIEN_LOI', 10::numeric, 5),
    ('ANH', 'VACT-NN-TA', 'ANH_DIEN_DAT_TUONG_DUONG', 10::numeric, 5),
    ('TOAN', 'VACT-TOAN', NULL, 10::numeric, 12),
    ('ANH', 'THPT', 'ANH_SAP_XEP_HOI_THOAI', 0.25::numeric, 5)
) spec(subject, competency, declared_tag, points, quantity)
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = spec.subject
JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
LEFT JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008'
    AND tag.code_normalized = lower(spec.declared_tag)
CROSS JOIN LATERAL generate_series(1, spec.quantity) n
), classified AS (
    INSERT INTO seed_be14_classification
    SELECT id, competency, tag_code FROM singles
)
INSERT INTO seed_question_rows
SELECT id,
       'SEED-BE14-' || competency || '-' || COALESCE(declared_tag, 'NONE') || '-' || n,
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
           md5('thi360-be14-set-' || spec.competency || '-' || COALESCE(spec.declared_tag, 'NONE') || '-' || spec.child_count || '-' || n)::uuid AS id,
           'SEED-BE14-' || spec.competency || '-' || COALESCE(spec.declared_tag, 'NONE') || '-' || spec.child_count || '-' || n AS code
    FROM (VALUES
        ('ANH', 'THPT', 'ANH_DOC_DIEN', 0.25::numeric, 6, 2),
        ('ANH', 'THPT', 'ANH_DOC_DIEN_CAU', 0.25::numeric, 5, 1),
        ('ANH', 'THPT', 'ANH_DOC_HIEU', 0.25::numeric, 8, 1),
        ('ANH', 'THPT', 'ANH_DOC_HIEU', 0.25::numeric, 10, 1),
        ('ANH', 'VACT-NN-TA', 'ANH_DOC_HIEU', 10::numeric, 7, 1),
        ('ANH', 'VACT-NN-TA', 'ANH_DOC_HIEU', 10::numeric, 8, 1),
        ('TOAN', 'VACT-TOAN', NULL, 10::numeric, 3, 6),
        ('SCIENTIFIC_THINKING', 'VACT-TD-LOGIC', 'TD_SAP_XEP_DIEU_KIEN', 10::numeric, 3, 1),
        ('SCIENTIFIC_THINKING', 'VACT-TD-LOGIC', 'TD_XEP_HANG', 10::numeric, 3, 1),
        ('SCIENTIFIC_THINKING', 'VACT-TD-LOGIC', 'TD_BANG_SO_LIEU', 10::numeric, 3, 1),
        ('SCIENTIFIC_THINKING', 'VACT-TD-LOGIC', 'TD_BIEU_DO', 10::numeric, 3, 1),
        ('SCIENTIFIC_THINKING', 'VACT-TD-SL', NULL, 10::numeric, 3, 6)
    ) spec(subject, competency, declared_tag, points, child_count, quantity)
    JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code = spec.subject
    JOIN catalog.catalog_item grade ON grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code = 'G12'
    JOIN catalog.catalog_item level ON level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code = 'TH'
    LEFT JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008'
        AND tag.code_normalized = lower(spec.declared_tag)
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

-- BEGIN BE-19 (UNI-2691): H-SCA and V-SAT sample sources, listening audio included.
-- Content is plain ASCII so content_text and search_text equal the Java normalizer.
-- Every id and code derives from a fixed seed string, so a rerun inserts nothing twice;
-- ON CONFLICT DO NOTHING keeps admin-created rows and renamed seeded rows untouched.
-- Function bodies use single quotes (doubled inside) and no semicolons: the repeatable is also
-- executed through Spring ScriptUtils in tests, which does not understand dollar quoting.
CREATE FUNCTION pg_temp.seed_text(html text) RETURNS text LANGUAGE sql IMMUTABLE AS
'SELECT btrim(regexp_replace(regexp_replace(coalesce(html, ''''), ''<[^>]+>'', '''', ''g''), ''\s+'', '' '', ''g''))';

-- Text of a non-set body in the order of QuestionSearchNormalizer: options or statements, then
-- matching left and right, short answers, and the cloze passage.
CREATE FUNCTION pg_temp.seed_leaf_text(body jsonb) RETURNS text LANGUAGE sql IMMUTABLE AS
'SELECT concat_ws('' '',
    (SELECT string_agg(pg_temp.seed_text(e.v->>''content''), '' '' ORDER BY e.o)
     FROM jsonb_array_elements(body->''options'') WITH ORDINALITY e(v, o)),
    (SELECT string_agg(pg_temp.seed_text(e.v->>''content''), '' '' ORDER BY e.o)
     FROM jsonb_array_elements(body->''statements'') WITH ORDINALITY e(v, o)),
    (SELECT string_agg(pg_temp.seed_text(e.v->>''content''), '' '' ORDER BY e.o)
     FROM jsonb_array_elements(body->''left'') WITH ORDINALITY e(v, o)),
    (SELECT string_agg(pg_temp.seed_text(e.v->>''content''), '' '' ORDER BY e.o)
     FROM jsonb_array_elements(body->''right'') WITH ORDINALITY e(v, o)),
    (SELECT string_agg(e.v #>> ''{}'', '' '' ORDER BY e.o)
     FROM jsonb_array_elements(body->''answers'') WITH ORDINALITY e(v, o)),
    CASE WHEN body->>''type'' = ''CLOZE'' THEN pg_temp.seed_text(body->>''passage'') END)';

-- content_text of a question: its content, then the body text; a set adds its passage and, for
-- each child, the child content followed by the child body text.
CREATE FUNCTION pg_temp.seed_plain(content text, body jsonb) RETURNS text LANGUAGE sql IMMUTABLE AS
'SELECT btrim(regexp_replace(concat_ws('' '', pg_temp.seed_text(content),
    CASE WHEN body->>''type'' = ''QUESTION_SET'' THEN concat_ws('' '', pg_temp.seed_text(body->>''passage''),
        (SELECT string_agg(concat_ws('' '', pg_temp.seed_text(e.v->>''content''), pg_temp.seed_leaf_text(e.v->''body'')),
                           '' '' ORDER BY e.o)
         FROM jsonb_array_elements(body->''children'') WITH ORDINALITY e(v, o)))
    ELSE pg_temp.seed_leaf_text(body) END), ''\s+'', '' '', ''g''))';

CREATE FUNCTION pg_temp.seed_options(label text, option_count integer, correct_letters text[])
RETURNS jsonb LANGUAGE sql IMMUTABLE AS
'SELECT jsonb_agg(jsonb_build_object(''id'', chr(96 + i), ''content'', ''<p>'' || label || '' option '' || chr(64 + i) || ''</p>'',
                                     ''correct'', chr(96 + i) = ANY (correct_letters)) ORDER BY i)
 FROM generate_series(1, option_count) i';

-- Four options, exactly one correct; the correct letter rotates with n.
CREATE FUNCTION pg_temp.seed_single(label text, n integer) RETURNS jsonb LANGUAGE sql IMMUTABLE AS
'SELECT jsonb_build_object(''type'', ''SINGLE_CHOICE'', ''options'',
                           pg_temp.seed_options(label, 4, ARRAY[chr(97 + n % 4)]))';

-- Four options, two correct; H-SCA multiple choice is PARTIAL (spec H36).
CREATE FUNCTION pg_temp.seed_multiple(label text, n integer) RETURNS jsonb LANGUAGE sql IMMUTABLE AS
'SELECT jsonb_build_object(''type'', ''MULTIPLE_CHOICE'', ''scoringRule'', ''PARTIAL'', ''options'',
                           pg_temp.seed_options(label, 4, ARRAY[''a'', chr(98 + n % 3)]))';

-- V-SAT true/false: four statements, count table [1,2,3,6] on a six-point question (spec H36).
CREATE FUNCTION pg_temp.seed_true_false(label text) RETURNS jsonb LANGUAGE sql IMMUTABLE AS
'SELECT jsonb_build_object(''type'', ''TRUE_FALSE'', ''scoringMethod'', ''COUNT_TABLE'',
    ''scoreTable'', jsonb_build_array(1, 2, 3, 6),
    ''statements'', (SELECT jsonb_agg(jsonb_build_object(''id'', chr(96 + i),
        ''content'', ''<p>'' || label || '' statement '' || chr(64 + i) || ''</p>'', ''answer'', i % 2 = 1) ORDER BY i)
        FROM generate_series(1, 4) i))';

-- V-SAT matching: four items on each side, 1.5 points per pair (six points in all).
CREATE FUNCTION pg_temp.seed_matching(label text) RETURNS jsonb LANGUAGE sql IMMUTABLE AS
'SELECT jsonb_build_object(''type'', ''MATCHING'', ''reuseRight'', false,
    ''left'', (SELECT jsonb_agg(jsonb_build_object(''id'', ''l'' || i, ''content'', ''<p>'' || label || '' left '' || i || ''</p>'') ORDER BY i)
             FROM generate_series(1, 4) i),
    ''right'', (SELECT jsonb_agg(jsonb_build_object(''id'', ''r'' || i, ''content'', ''<p>'' || label || '' right '' || i || ''</p>'') ORDER BY i)
              FROM generate_series(1, 4) i),
    ''pairs'', (SELECT jsonb_agg(jsonb_build_object(''leftId'', ''l'' || i, ''rightId'', ''r'' || i, ''points'', 1.5) ORDER BY i)
              FROM generate_series(1, 4) i))';

CREATE TEMP TABLE seed_dot2b_cells ON COMMIT DROP AS
SELECT subject.code AS subject_code, subject.id AS subject_id, grade.code AS grade_code, grade.id AS grade_id,
       level.code AS level_code, level.id AS level_id
FROM catalog.catalog_item subject
CROSS JOIN catalog.catalog_item grade
CROSS JOIN catalog.catalog_item level
WHERE subject.group_id = '00000000-0000-7000-8100-000000000002'
  AND subject.code IN ('TOAN', 'LY', 'HOA', 'SINH', 'SU', 'DIA', 'VAN', 'ANH')
  AND grade.group_id = '00000000-0000-7000-8100-000000000003' AND grade.code IN ('G10', 'G11', 'G12')
  AND level.group_id = '00000000-0000-7000-8100-000000000004' AND level.code IN ('NB', 'TH', 'VD');

CREATE TEMP TABLE seed_dot2b_single (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    type text NOT NULL,
    subject_code text NOT NULL,
    grade_code text NOT NULL,
    level_code text NOT NULL,
    points numeric(8,4) NOT NULL,
    label text NOT NULL,
    body jsonb NOT NULL
) ON COMMIT DROP;

CREATE TEMP TABLE seed_dot2b_set (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    subject_code text NOT NULL,
    grade_code text NOT NULL,
    level_code text NOT NULL,
    label text NOT NULL,
    passage text NOT NULL,
    child_count integer NOT NULL,
    child_points numeric(8,4) NOT NULL,
    mixed boolean NOT NULL,
    tag_codes text[]
) ON COMMIT DROP;

-- Listening files: one audio/mpeg attachment per question root (children never carry files).
-- Size and sha256 equal the files in db/devdata/assets/; DevQuestionAssetSeeder puts the bytes
-- into object storage under the same object_key.
CREATE TEMP TABLE seed_dot2b_asset (
    asset_id uuid PRIMARY KEY,
    question_id uuid NOT NULL,
    object_key text NOT NULL,
    file_name text NOT NULL,
    size bigint NOT NULL,
    sha256 bytea NOT NULL
) ON COMMIT DROP;

-- H-SCA Toan, Ly, Hoa, Sinh (spec 4.1): 0.25 per question, four options.
INSERT INTO seed_dot2b_single (id, code, type, subject_code, grade_code, level_code, points, label, body)
SELECT md5('thi360-hsca-' || s.code || '-sc-' || n)::uuid, 'SEED-HSCA-' || s.code || '-SC-' || lpad(n::text, 3, '0'),
       'SINGLE_CHOICE', s.code, (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       0.25, 'H-SCA ' || s.code || ' single ' || n, pg_temp.seed_single('H-SCA ' || s.code || ' single ' || n, n)
FROM unnest(ARRAY['TOAN', 'LY', 'HOA', 'SINH']) AS s(code) CROSS JOIN generate_series(1, 24) n
UNION ALL
SELECT md5('thi360-hsca-' || s.code || '-mc-' || n)::uuid, 'SEED-HSCA-' || s.code || '-MC-' || lpad(n::text, 3, '0'),
       'MULTIPLE_CHOICE', s.code, (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       0.25, 'H-SCA ' || s.code || ' multiple ' || n, pg_temp.seed_multiple('H-SCA ' || s.code || ' multiple ' || n, n)
FROM unnest(ARRAY['TOAN', 'LY', 'HOA', 'SINH']) AS s(code) CROSS JOIN generate_series(1, 8) n
UNION ALL
SELECT md5('thi360-hsca-' || s.code || '-sa-' || n)::uuid, 'SEED-HSCA-' || s.code || '-SA-' || lpad(n::text, 3, '0'),
       'SHORT_ANSWER', s.code, (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       0.25, 'H-SCA ' || s.code || ' short answer ' || n,
       jsonb_build_object('type', 'SHORT_ANSWER', 'answerFormat', 'INTEGER', 'answers', jsonb_build_array(n::text), 'inputHint', '')
FROM unnest(ARRAY['TOAN', 'LY', 'HOA', 'SINH']) AS s(code) CROSS JOIN generate_series(1, 12) n;

-- H-SCA English listening, single questions (spec 4.3): audio on the question itself, one point.
INSERT INTO seed_dot2b_single (id, code, type, subject_code, grade_code, level_code, points, label, body)
SELECT md5('thi360-hsca-en-listen-single-' || n)::uuid, 'SEED-HSCA-ANH-LISTEN-SINGLE-' || lpad(n::text, 2, '0'),
       'SINGLE_CHOICE', 'ANH', 'G12', 'TH', 1, 'H-SCA English listening single ' || n,
       pg_temp.seed_single('H-SCA English listening single ' || n, n)
FROM generate_series(1, 10) n;

-- V-SAT (spec 5.1, 5.2): six points per true/false, matching and short answer; Cloze six per position.
INSERT INTO seed_dot2b_single (id, code, type, subject_code, grade_code, level_code, points, label, body)
SELECT md5('thi360-vsat-' || s.code || '-tf-' || n)::uuid, 'SEED-VSAT-' || s.code || '-TF-' || lpad(n::text, 3, '0'),
       'TRUE_FALSE', s.code, (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       6, 'V-SAT ' || s.code || ' true false ' || n, pg_temp.seed_true_false('V-SAT ' || s.code || ' true false ' || n)
FROM unnest(ARRAY['TOAN', 'LY', 'HOA', 'SINH', 'SU', 'DIA', 'VAN', 'ANH']) AS s(code) CROSS JOIN generate_series(1, 12) n
UNION ALL
SELECT md5('thi360-vsat-' || s.code || '-match-' || n)::uuid, 'SEED-VSAT-' || s.code || '-MATCH-' || lpad(n::text, 3, '0'),
       'MATCHING', s.code, (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       6, 'V-SAT ' || s.code || ' matching ' || n, pg_temp.seed_matching('V-SAT ' || s.code || ' matching ' || n)
FROM unnest(ARRAY['TOAN', 'LY', 'HOA', 'SINH', 'SU', 'DIA', 'VAN', 'ANH']) AS s(code) CROSS JOIN generate_series(1, 10) n
UNION ALL
SELECT md5('thi360-vsat-' || s.code || '-sa-' || n)::uuid, 'SEED-VSAT-' || s.code || '-SA-' || lpad(n::text, 3, '0'),
       'SHORT_ANSWER', s.code, (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       6, 'V-SAT ' || s.code || ' short answer ' || n,
       jsonb_build_object('type', 'SHORT_ANSWER', 'answerFormat', 'INTEGER', 'answers', jsonb_build_array(n::text), 'inputHint', '')
FROM unnest(ARRAY['TOAN', 'LY', 'HOA', 'SINH', 'SU', 'DIA']) AS s(code) CROSS JOIN generate_series(1, 10) n
UNION ALL
SELECT md5('thi360-vsat-ANH-cloze-' || n)::uuid, 'SEED-VSAT-ANH-CLOZE-' || lpad(n::text, 3, '0'),
       'CLOZE', 'ANH', (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       30, 'V-SAT English cloze ' || n,
       jsonb_build_object('type', 'CLOZE',
           'passage', '<p>V-SAT English cloze passage ' || n || ' [[a]] [[b]] [[c]] [[d]] [[e]]</p>',
           'blanks', (SELECT jsonb_agg(jsonb_build_object('id', chr(96 + i), 'answers', jsonb_build_array('word' || i),
                                                          'points', 6) ORDER BY i)
                      FROM generate_series(1, 5) i))
FROM generate_series(1, 3) n;

-- H-SCA sets: Toan/Ly/Hoa/Sinh part 2 (five children, Q-40), literature (5, 7, 8 children,
-- one point each, Q-39), English listening (4 and 5 children, audio on the root) and reading
-- (ten children, no audio). Children are single choice with four options.
INSERT INTO seed_dot2b_set (id, code, subject_code, grade_code, level_code, label, passage, child_count, child_points,
                            mixed, tag_codes)
SELECT md5('thi360-hsca-' || s.code || '-set-' || n)::uuid, 'SEED-HSCA-' || s.code || '-SET-' || lpad(n::text, 3, '0'),
       s.code, (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       'H-SCA ' || s.code || ' set ' || n, '<p>H-SCA ' || s.code || ' context ' || n || '</p>', 5, 0.25, false,
       NULL::text[]
FROM unnest(ARRAY['TOAN', 'LY', 'HOA', 'SINH']) AS s(code) CROSS JOIN generate_series(1, 4) n
UNION ALL
SELECT md5('thi360-hsca-literature-' || f.stem || '-' || n)::uuid, 'SEED-HSCA-VAN-' || f.stem || '-' || lpad(n::text, 3, '0'),
       'VAN', 'G12', 'TH', 'H-SCA literature ' || f.stem || ' set ' || n,
       '<p>H-SCA literature ' || f.stem || ' text ' || n || '</p>', f.child_count, 1, false,
       ARRAY[f.tag]
FROM (VALUES ('INFO', 'VAN_THONG_TIN', 5), ('ARGUMENT', 'VAN_NGHI_LUAN', 7), ('POETRY', 'VAN_THO', 8))
     f(stem, tag, child_count)
CROSS JOIN generate_series(1, 3) n
UNION ALL
SELECT md5('thi360-hsca-en-listen-' || f.kind || '-' || n)::uuid,
       'SEED-HSCA-ANH-LISTEN-' || upper(f.kind) || '-' || lpad(n::text, 2, '0'), 'ANH', 'G12', 'TH',
       'H-SCA English listening ' || f.kind || ' ' || n, '<p>H-SCA English listening ' || f.kind || ' script ' || n || '</p>',
       f.child_count, 1, false, NULL::text[]
FROM (VALUES ('set4', 4), ('set5', 5)) f(kind, child_count) CROSS JOIN generate_series(1, 4) n
UNION ALL
SELECT md5('thi360-hsca-en-reading-set-' || n)::uuid, 'SEED-HSCA-ANH-READING-SET-' || lpad(n::text, 2, '0'), 'ANH', 'G12', 'TH',
       'H-SCA English reading set ' || n, '<p>H-SCA English reading passage ' || n || '</p>', 10, 1, false,
       NULL::text[]
FROM generate_series(1, 6) n;

-- V-SAT sets: part 2 of six subjects (two sets of three children are needed; four are seeded),
-- literature part 2 (poetry and prose), English single-choice set of six, English mixed set of
-- eight (positions 1-2 true/false). Slot matching is by root tag (ADV:BE-19:3): the four Địa lí
-- roots carry one label each, two DIA_BANG_SO_LIEU and two DIA_BIEU_DO; children carry none.
INSERT INTO seed_dot2b_set (id, code, subject_code, grade_code, level_code, label, passage, child_count, child_points,
                            mixed, tag_codes)
SELECT md5('thi360-vsat-' || s.code || '-set-' || n)::uuid, 'SEED-VSAT-' || s.code || '-SET-' || lpad(n::text, 3, '0'),
       s.code, (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       'V-SAT ' || s.code || ' set ' || n, '<p>V-SAT ' || s.code || ' context ' || n || '</p>', 3, 6, false,
       CASE WHEN s.code = 'DIA' THEN ARRAY[(ARRAY['DIA_BANG_SO_LIEU', 'DIA_BIEU_DO'])[1 + n % 2]] END
FROM unnest(ARRAY['TOAN', 'LY', 'HOA', 'SINH', 'SU', 'DIA']) AS s(code) CROSS JOIN generate_series(1, 4) n
UNION ALL
SELECT md5('thi360-vsat-literature-' || f.stem || '-' || n)::uuid, 'SEED-VSAT-VAN-' || f.stem || '-SET-' || lpad(n::text, 3, '0'),
       'VAN', (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       'V-SAT literature ' || f.stem || ' set ' || n, '<p>V-SAT literature ' || f.stem || ' text ' || n || '</p>', 3, 6, false,
       ARRAY[f.tag]
FROM (VALUES ('POETRY', 'VAN_THO'), ('PROSE', 'VAN_TRUYEN')) f(stem, tag) CROSS JOIN generate_series(1, 3) n
UNION ALL
SELECT md5('thi360-vsat-ANH-choice-set-' || n)::uuid, 'SEED-VSAT-ANH-CHOICE-SET-' || lpad(n::text, 2, '0'), 'ANH',
       (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       'V-SAT English choice set ' || n, '<p>V-SAT English choice passage ' || n || '</p>', 6, 6, false,
       NULL::text[]
FROM generate_series(1, 3) n
UNION ALL
SELECT md5('thi360-vsat-ANH-mixed-set-' || n)::uuid, 'SEED-VSAT-ANH-MIXED-SET-' || lpad(n::text, 2, '0'), 'ANH',
       (ARRAY['G10', 'G11', 'G12'])[1 + n % 3], (ARRAY['NB', 'TH', 'VD'])[1 + (n / 3) % 3],
       'V-SAT English shared reading set ' || n, '<p>V-SAT English shared reading passage ' || n || '</p>', 8, 6, true,
       NULL::text[]
FROM generate_series(1, 3) n;

INSERT INTO seed_dot2b_asset (asset_id, question_id, object_key, file_name, size, sha256)
SELECT md5('thi360-hsca-audio-' || a.kind || '-' || a.n)::uuid, md5('thi360-hsca-en-listen-' || a.kind || '-' || a.n)::uuid,
       'questions/dev-seed/hsca-en-listening-' || a.kind || '-' || lpad(a.n::text, 2, '0') || '.mp3',
       'hsca-en-listening-' || a.kind || '-' || lpad(a.n::text, 2, '0') || '.mp3', a.size, decode(a.sha256, 'hex')
FROM (VALUES
        ('single', 1, 6264, '26a61e1708c3893cef781c8b1cf6e9c76a14c44822a244c44e85fe0674a59d93'),
        ('single', 2, 6264, '34a936fdbd8e95a08314dc41278dad3e14fd736ae1437e3c85595e642086ff16'),
        ('single', 3, 6264, 'd7a950a21e6433ce1955b11ccda22ac329a3fbf4667a492ad95ef6b70448ee44'),
        ('single', 4, 6264, '2669ad1c3495e6e502130b1fe18bcf8f498d272c9250943f944ea2be7ff7ce68'),
        ('single', 5, 6264, '67ef287eada857d3abb46f02c9566a8b7177a4364ec92441e1f78d6681eb5e62'),
        ('single', 6, 6264, '2893a6106ffd8fe2a1b14880a23bb2250d7181ba534343042abb292b40119a2e'),
        ('single', 7, 6264, '7befba64f9a942981426eaf0c70e2d414c49d27b6fdd2bacdcaba9f5fac16a6e'),
        ('single', 8, 6264, '7f014ef0655c7b4d6e0c32d17906e344fb7c7c04c636d27c1f812ec3d9d3157c'),
        ('single', 9, 6264, '85bf7a3b96e65a932dc9f69e0cf049af5f82cb0e653b79a0ce2cf20a889c3389'),
        ('single', 10, 6264, '71103f8860aa01d9825be93a7a2a0ce4fb7c45fdf732d14aa677fa28d5c6f179'),
        ('set4', 1, 6264, 'ba14a916e87470fc371bc629990ef2d698f0f4358b19ca3d9d46f53871fdcb98'),
        ('set4', 2, 6264, '27968836cb40e6ffdd10807efa0a195435601b0936d2f0bef4a88b99016108f2'),
        ('set4', 3, 6264, '15e7fe1b33151ef5dbb3b2d4e942eee19b8034b61f70760e2b6137f03bf8a167'),
        ('set4', 4, 6264, '9611d40f4fa0cce732832672657bff8ceaf9748ad8b25289b6c85c323d826b8e'),
        ('set5', 1, 6264, '6c5a711f5adea7ff71fa65869776c4a96abf7e2b3809554a5783a59f742c341f'),
        ('set5', 2, 6264, '9b66487e3e776b4785bdea6e0e86ba0ee2bd514b888c33a89562527d018ff0cf'),
        ('set5', 3, 6264, 'e001a40179064c7fac51626e15e2b643e3164e92ca101412aa76fd1ff26d5fa2'),
        ('set5', 4, 6264, 'ac0db8a9a176cdaeb30296ec334d86a45f21e69d84f306e0ad744966c5a208e1')
     ) a(kind, n, size, sha256);

INSERT INTO questionbank.question_asset (id, kind, object_key, file_name, mime, size, sha256)
SELECT asset_id, 'ATTACHMENT', object_key, file_name, 'audio/mpeg', size, sha256 FROM seed_dot2b_asset
ON CONFLICT DO NOTHING;

INSERT INTO seed_question_rows
SELECT s.id, s.code, s.type, c.subject_id, c.grade_id, c.level_id, NULL, s.points, '<p>' || s.label || '</p>', s.body, NULL, NULL
FROM seed_dot2b_single s
JOIN seed_dot2b_cells c ON c.subject_code = s.subject_code AND c.grade_code = s.grade_code AND c.level_code = s.level_code;

WITH sets AS (
    SELECT s.*, c.subject_id, c.grade_id, c.level_id
    FROM seed_dot2b_set s
    JOIN seed_dot2b_cells c ON c.subject_code = s.subject_code AND c.grade_code = s.grade_code AND c.level_code = s.level_code
), children AS (
    SELECT s.*, pos, md5(s.id::text || '-' || pos)::uuid AS child_id,
           CASE WHEN s.mixed AND pos < 2 THEN 'TRUE_FALSE' ELSE 'SINGLE_CHOICE' END AS child_type,
           s.label || ' question ' || (pos + 1) AS child_label,
           CASE WHEN s.mixed AND pos < 2 THEN pg_temp.seed_true_false(s.label || ' question ' || (pos + 1))
                ELSE pg_temp.seed_single(s.label || ' question ' || (pos + 1), pos) END AS child_body
    FROM sets s CROSS JOIN LATERAL generate_series(0, s.child_count - 1) pos
)
INSERT INTO seed_question_rows
SELECT s.id, s.code, 'QUESTION_SET', s.subject_id, s.grade_id, s.level_id, NULL::uuid,
       s.child_count * s.child_points, '<p>' || s.label || '</p>',
       jsonb_build_object('type', 'QUESTION_SET', 'passage', s.passage, 'children',
           (SELECT jsonb_agg(jsonb_build_object('id', c.child_id, 'type', c.child_type,
                                                'content', '<p>' || c.child_label || '</p>', 'body', c.child_body,
                                                'points', s.child_points, 'knowledgeItemIds', '[]'::jsonb) ORDER BY c.pos)
            FROM children c WHERE c.id = s.id)),
       NULL::uuid, NULL::integer
FROM sets s
UNION ALL
SELECT c.child_id, c.code || '-' || c.pos, c.child_type, c.subject_id, c.grade_id, c.level_id, NULL, c.child_points,
       '<p>' || c.child_label || '</p>', c.child_body, c.id, c.pos
FROM children c;

INSERT INTO seed_2b_question_tags (question_id, tag_item_id)
SELECT s.id, tag.id
FROM seed_dot2b_set s
CROSS JOIN LATERAL unnest(s.tag_codes) t(code)
JOIN catalog.catalog_item tag ON tag.group_id = '00000000-0000-7000-8100-000000000008' AND tag.code = t.code
ON CONFLICT DO NOTHING;

-- A dot2b set is one seed unit: when a fixed root/child id or code is already occupied the
-- whole set is skipped, so no READY root can point at children another parent owns.
CREATE TEMP TABLE seed_blocked_sets (id uuid PRIMARY KEY) ON COMMIT DROP;
INSERT INTO seed_blocked_sets
SELECT s.id
FROM seed_dot2b_set s
WHERE EXISTS (SELECT 1 FROM questionbank.question q WHERE q.id = s.id OR q.code = s.code)
   OR EXISTS (SELECT 1 FROM generate_series(0, s.child_count - 1) pos
              JOIN questionbank.question q
                ON q.id = md5(s.id::text || '-' || pos)::uuid
               OR q.code = s.code || '-' || pos);

-- END BE-19.

-- Parents must exist before their child rows satisfy the self-reference.
WITH prepared AS (
    SELECT s.*,
           CASE WHEN code LIKE 'SEED-EXTRA-%-2' THEN 'DRAFT'
                WHEN code LIKE 'SEED-EXTRA-%-3' THEN 'RETIRED' ELSE 'READY' END AS seed_status,
           CASE WHEN code LIKE 'SEED-HSCA-%' OR code LIKE 'SEED-VSAT-%' THEN pg_temp.seed_plain(content, body) ELSE
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
               WHEN 'TRUE_FALSE' THEN CASE WHEN code LIKE 'SEED-BE9-ECON-TF-%'
                   THEN regexp_replace(content, '<[^>]+>', '', 'g')
                       || ' Practice statement A Practice statement B Practice statement C Practice statement D'
                   ELSE regexp_replace(content, '<[^>]+>', '', 'g') || ' First Second Third Fourth' END
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
                   WHEN code LIKE 'SEED-BE9-ECON-CASE-%'
                       THEN regexp_replace(content, '<[^>]+>', '', 'g')
                           || ' ' || regexp_replace(body->>'passage', '<[^>]+>', '', 'g')
                           || repeat(' Practice case question Practice answer A Practice answer B',
                                     jsonb_array_length(body->'children'))
                   WHEN code LIKE 'SEED-READING-%'
                       THEN 'Reading set Reading context Reading child One Two Reading child One Two'
                   WHEN code LIKE 'SEED-HSA-MATH-SET-%'
                       THEN 'HSA Math set HSA Math context HSA Math child One Two HSA Math child One Two'
                   ELSE 'Math set Math context Math child One Two Math child 1' END
           END END AS plain
    FROM seed_question_rows s WHERE parent_id IS NULL
      AND NOT EXISTS (SELECT 1 FROM seed_blocked_sets b WHERE b.id = s.id)
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
       CASE WHEN code LIKE 'SEED-HSCA-%' OR code LIKE 'SEED-VSAT-%' THEN pg_temp.seed_plain(content, body)
            ELSE regexp_replace(content, '<[^>]+>', '', 'g') ||
                CASE WHEN code LIKE 'SEED-BE9-ECON-CASE-%' THEN ' Practice answer A Practice answer B'
                     WHEN type = 'SINGLE_CHOICE' THEN ' One Two' ELSE ' 1' END END,
       lower(code || ' ' || CASE WHEN code LIKE 'SEED-HSCA-%' OR code LIKE 'SEED-VSAT-%' THEN pg_temp.seed_plain(content, body)
                                 ELSE regexp_replace(content, '<[^>]+>', '', 'g') ||
                CASE WHEN code LIKE 'SEED-BE9-ECON-CASE-%' THEN ' Practice answer A Practice answer B'
                     WHEN type = 'SINGLE_CHOICE' THEN ' One Two' ELSE ' 1' END END)
FROM seed_question_rows
WHERE parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM seed_blocked_sets b WHERE b.id = seed_question_rows.parent_id)
  AND EXISTS (SELECT 1 FROM questionbank.question p WHERE p.id = seed_question_rows.parent_id)
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
    'solution', NULL, 'commonMistakes', '[]'::jsonb,
    'attachmentIds', COALESCE((SELECT jsonb_agg(a.id ORDER BY a.id)
                               FROM seed_dot2b_asset sa JOIN questionbank.question_asset a ON a.id = sa.asset_id
                               WHERE sa.question_id = s.id), '[]'::jsonb), 'body', s.body),
    CASE WHEN q.status = 'DRAFT' THEN NULL ELSE now() END
FROM seed_question_rows s
JOIN questionbank.question q ON q.id = s.id
-- Only rows this run inserted: a fixed id owned by someone else never receives a seed version.
WHERE EXISTS (SELECT 1 FROM seed_inserted_questions i WHERE i.id = s.id)
   OR EXISTS (SELECT 1 FROM seed_inserted_children i WHERE i.id = s.id)
ON CONFLICT DO NOTHING;

INSERT INTO questionbank.question_tag (question_id, tag_item_id)
SELECT t.question_id, t.tag_item_id FROM seed_2b_question_tags t
WHERE EXISTS (SELECT 1 FROM seed_inserted_questions i WHERE i.id = t.question_id)
   OR EXISTS (SELECT 1 FROM seed_inserted_children i WHERE i.id = t.question_id)
ON CONFLICT DO NOTHING;

-- Địa lí part II roots keep exactly one slot label (ADV:BE-19:3). A database seeded by 6ef80282
-- still carries both legacy labels on the roots and one each on the first two children. A root
-- is corrected only when it is provably in that untouched state: published at version 1 and
-- never edited, exactly both legacy labels on the root in question_tag and in its version-1
-- snapshot, children carrying only legacy labels, no extra children. Anything else (already
-- correct, edited, published again, unrelated) is left as it is.
CREATE TEMP TABLE seed_dia_legacy ON COMMIT DROP AS
SELECT id FROM catalog.catalog_item
WHERE group_id = '00000000-0000-7000-8100-000000000008' AND code IN ('DIA_BANG_SO_LIEU', 'DIA_BIEU_DO');

CREATE TEMP TABLE seed_dia_fix ON COMMIT DROP AS
SELECT s.id AS root_id, q.subject_item_id AS subject_id, t.tag_item_id AS target_tag_id
FROM seed_dot2b_set s
JOIN questionbank.question q ON q.id = s.id AND q.code = s.code AND q.parent_id IS NULL
JOIN seed_2b_question_tags t ON t.question_id = s.id
WHERE s.code LIKE 'SEED-VSAT-DIA-SET-%'
  AND (SELECT count(*) FROM seed_dia_legacy) = 2
  AND q.status = 'READY' AND q.deleted_at IS NULL
  AND q.current_version_no = 1 AND q.working_version_no IS NULL
  AND NOT EXISTS (SELECT 1 FROM questionbank.question_version v WHERE v.question_id = s.id AND v.version_no <> 1)
  AND (SELECT count(*) FROM questionbank.question_tag qt WHERE qt.question_id = s.id) = 2
  AND (SELECT count(*) FROM questionbank.question_tag qt
       JOIN seed_dia_legacy l ON l.id = qt.tag_item_id WHERE qt.question_id = s.id) = 2
  AND EXISTS (SELECT 1 FROM questionbank.question_version v
              WHERE v.question_id = s.id AND v.version_no = 1
                AND jsonb_typeof(v.snapshot -> 'tagItemIds') = 'array'
                AND jsonb_array_length(v.snapshot -> 'tagItemIds') = 2
                AND (SELECT count(*) FROM jsonb_array_elements_text(v.snapshot -> 'tagItemIds') e
                     JOIN seed_dia_legacy l ON l.id::text = e) = 2)
  AND NOT EXISTS (SELECT 1 FROM questionbank.question c
                  WHERE c.parent_id = s.id
                    AND NOT EXISTS (SELECT 1 FROM generate_series(0, s.child_count - 1) pos
                                    WHERE md5(s.id::text || '-' || pos)::uuid = c.id))
  AND NOT EXISTS (
      SELECT 1 FROM generate_series(0, s.child_count - 1) pos
      JOIN questionbank.question c ON c.id = md5(s.id::text || '-' || pos)::uuid
      WHERE c.parent_id IS DISTINCT FROM s.id
         OR c.current_version_no IS DISTINCT FROM 1 OR c.working_version_no IS NOT NULL
         OR EXISTS (SELECT 1 FROM questionbank.question_version v WHERE v.question_id = c.id AND v.version_no <> 1)
         OR EXISTS (SELECT 1 FROM questionbank.question_tag qt
                    WHERE qt.question_id = c.id AND qt.tag_item_id NOT IN (SELECT id FROM seed_dia_legacy))
         OR EXISTS (SELECT 1 FROM questionbank.question_version v
                    WHERE v.question_id = c.id AND v.version_no = 1
                      AND (jsonb_typeof(v.snapshot -> 'tagItemIds') IS DISTINCT FROM 'array'
                           OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(v.snapshot -> 'tagItemIds') e
                                      WHERE e NOT IN (SELECT id::text FROM seed_dia_legacy)))));

DELETE FROM questionbank.question_tag qt
WHERE qt.tag_item_id IN (SELECT id FROM seed_dia_legacy)
  AND (qt.question_id IN (SELECT root_id FROM seed_dia_fix)
       OR qt.question_id IN (SELECT c.id FROM questionbank.question c
                             JOIN seed_dia_fix f ON c.parent_id = f.root_id));

INSERT INTO questionbank.question_tag (question_id, tag_item_id)
SELECT root_id, target_tag_id FROM seed_dia_fix
ON CONFLICT DO NOTHING;

-- The v1 snapshot of a corrected root keeps its own tags minus the legacy labels plus the
-- slot label of this set; child snapshots lose the legacy labels. Nothing is read from question_tag.
UPDATE questionbank.question_version v
SET snapshot = jsonb_set(v.snapshot, '{tagItemIds}', COALESCE(
    (SELECT jsonb_agg(x.u ORDER BY x.u)
     FROM (SELECT e::uuid AS u FROM jsonb_array_elements_text(v.snapshot -> 'tagItemIds') e
           WHERE e::uuid NOT IN (SELECT id FROM seed_dia_legacy)
           UNION SELECT f.target_tag_id FROM seed_dia_fix f WHERE f.root_id = v.question_id) x),
    '[]'::jsonb))
WHERE v.version_no = 1 AND v.question_id IN (SELECT root_id FROM seed_dia_fix);

UPDATE questionbank.question_version v
SET snapshot = jsonb_set(v.snapshot, '{tagItemIds}', COALESCE(
    (SELECT jsonb_agg(e::uuid ORDER BY e::uuid) FROM jsonb_array_elements_text(v.snapshot -> 'tagItemIds') e
     WHERE e::uuid NOT IN (SELECT id FROM seed_dia_legacy)), '[]'::jsonb))
WHERE v.version_no = 1
  AND v.question_id IN (SELECT c.id FROM questionbank.question c JOIN seed_dia_fix f ON c.parent_id = f.root_id);

-- The READY source of these subjects changed (roots left a root-tag filter), so cached proofs
-- must be invalidated in the same transaction; a rerun finds no root to correct and bumps nothing.
INSERT INTO questionbank.question_source_revision (subject_item_id, revision)
SELECT subject_id, count(*) FROM seed_dia_fix GROUP BY subject_id
ON CONFLICT (subject_item_id) DO UPDATE
SET revision = questionbank.question_source_revision.revision + EXCLUDED.revision;

DROP TABLE seed_dia_fix, seed_dia_legacy;

DROP TABLE seed_be14_classification;

-- Roots keep their audio reference even if an admin later edits them (version 1 stays referenced).
INSERT INTO questionbank.question_asset_ref (asset_id, question_id, version_no)
SELECT a.id, sa.question_id, 1
FROM seed_dot2b_asset sa
JOIN questionbank.question_asset a ON a.id = sa.asset_id
WHERE EXISTS (SELECT 1 FROM seed_inserted_questions i WHERE i.id = sa.question_id)
ON CONFLICT DO NOTHING;

-- Dropped here so the script can run twice in one transaction, as SampleQuestionCoverageIT does.
DROP TABLE seed_dot2b_cells, seed_dot2b_single, seed_dot2b_set, seed_dot2b_asset, seed_blocked_sets;

DROP FUNCTION pg_temp.seed_plain(text, jsonb), pg_temp.seed_leaf_text(jsonb), pg_temp.seed_text(text),
    pg_temp.seed_options(text, integer, text[]), pg_temp.seed_single(text, integer),
    pg_temp.seed_multiple(text, integer), pg_temp.seed_true_false(text), pg_temp.seed_matching(text);
