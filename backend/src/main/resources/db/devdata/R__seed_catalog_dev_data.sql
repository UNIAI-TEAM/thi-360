-- B13 catalog sample data (dev & test profiles only; never run in prod/default).
-- Values of the six fixed groups (EXAM, SUBJECT, GRADE, QUESTION_LEVEL, QUESTION_FORMAT, TASK_TYPE) are system values (is_system = true, UNI-2759); the other sample values have is_system = false so admins can edit/delete them.
-- Descriptions of QUESTION_FORMAT, TASK_TYPE, SUBJECT_COMBINATION, and KNOWLEDGE start with "[MẪU]".

-- Retire old sample codes without deleting their versions or references.
UPDATE catalog.catalog_item
SET status = 2
WHERE group_id = '00000000-0000-7000-8100-000000000007'
  AND code_normalized IN ('qda_qd01', 'qda_qd02', 'qda_qd03', 'qda_qd04', 'qda_qd05', 'kh-1', 'kh-2', 'kh-3')
  AND status <> 2;

-- 1. EXAM (HSA, QDA)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8200-000000000101', '00000000-0000-7000-8100-000000000001', 'HSA', 'hsa', 1, 'Đánh giá năng lực ĐHQGHN (HSA)', 'VNU Hanoi Competency Assessment (HSA)', NULL, 0, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000101/'),
    ('00000000-0000-7000-8200-000000000102', '00000000-0000-7000-8100-000000000001', 'QDA', 'qda', 2, 'Đánh giá năng lực ĐHSPHN (QDA)', 'QDA Competency Assessment', NULL, 1, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000102/')
ON CONFLICT DO NOTHING;

-- 2. SUBJECT: 12 môn của kỳ thi tốt nghiệp THPT (TOAN, VAN, ANH, LY, HOA, SINH, SU, DIA, TIN, GDKTPL, CNCN, CNNN)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8200-000000000201', '00000000-0000-7000-8100-000000000002', 'TOAN', 'toan', 1, 'Toán học', 'Mathematics', NULL, 0, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000201/'),
    ('00000000-0000-7000-8200-000000000202', '00000000-0000-7000-8100-000000000002', 'VAN', 'van', 2, 'Ngữ văn', 'Literature', NULL, 1, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000202/'),
    ('00000000-0000-7000-8200-000000000203', '00000000-0000-7000-8100-000000000002', 'ANH', 'anh', 3, 'Tiếng Anh', 'English', NULL, 2, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000203/'),
    ('00000000-0000-7000-8200-000000000204', '00000000-0000-7000-8100-000000000002', 'LY', 'ly', 4, 'Vật lí', 'Physics', NULL, 3, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000204/'),
    ('00000000-0000-7000-8200-000000000205', '00000000-0000-7000-8100-000000000002', 'HOA', 'hoa', 5, 'Hóa học', 'Chemistry', NULL, 4, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000205/'),
    ('00000000-0000-7000-8200-000000000206', '00000000-0000-7000-8100-000000000002', 'SINH', 'sinh', 6, 'Sinh học', 'Biology', NULL, 5, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000206/'),
    ('00000000-0000-7000-8200-000000000207', '00000000-0000-7000-8100-000000000002', 'SU', 'su', 7, 'Lịch sử', 'History', NULL, 6, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000207/'),
    ('00000000-0000-7000-8200-000000000208', '00000000-0000-7000-8100-000000000002', 'DIA', 'dia', 8, 'Địa lí', 'Geography', NULL, 7, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000208/'),
    ('00000000-0000-7000-8200-000000000209', '00000000-0000-7000-8100-000000000002', 'TIN', 'tin', 9, 'Tin học', 'Informatics', NULL, 8, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000209/'),
    ('00000000-0000-7000-8200-000000000210', '00000000-0000-7000-8100-000000000002', 'GDKTPL', 'gdktpl', 10, 'Giáo dục kinh tế và pháp luật', 'Economic and Legal Education', NULL, 9, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000210/'),
    ('00000000-0000-7000-8200-000000000211', '00000000-0000-7000-8100-000000000002', 'CNCN', 'cncn', 11, 'Công nghệ (định hướng công nghiệp)', 'Technology (industrial)', NULL, 10, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000211/'),
    ('00000000-0000-7000-8200-000000000212', '00000000-0000-7000-8100-000000000002', 'CNNN', 'cnnn', 12, 'Công nghệ (định hướng nông nghiệp)', 'Technology (agricultural)', NULL, 11, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000212/')
ON CONFLICT DO NOTHING;

-- 3. GRADE (G10, G11, G12)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8200-000000000301', '00000000-0000-7000-8100-000000000003', 'G10', 'g10', 1, 'Lớp 10', 'Grade 10', NULL, 0, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000301/'),
    ('00000000-0000-7000-8200-000000000302', '00000000-0000-7000-8100-000000000003', 'G11', 'g11', 2, 'Lớp 11', 'Grade 11', NULL, 1, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000302/'),
    ('00000000-0000-7000-8200-000000000303', '00000000-0000-7000-8100-000000000003', 'G12', 'g12', 3, 'Lớp 12', 'Grade 12', NULL, 2, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000303/')
ON CONFLICT DO NOTHING;

-- 4. QUESTION_LEVEL (NB, TH, VD, VDC)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8200-000000000401', '00000000-0000-7000-8100-000000000004', 'NB', 'nb', 1, 'Nhận biết', 'Remembering', NULL, 0, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000401/'),
    ('00000000-0000-7000-8200-000000000402', '00000000-0000-7000-8100-000000000004', 'TH', 'th', 2, 'Thông hiểu', 'Understanding', NULL, 1, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000402/'),
    ('00000000-0000-7000-8200-000000000403', '00000000-0000-7000-8100-000000000004', 'VD', 'vd', 3, 'Vận dụng', 'Applying', NULL, 2, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000403/'),
    ('00000000-0000-7000-8200-000000000404', '00000000-0000-7000-8100-000000000004', 'VDC', 'vdc', 4, 'Vận dụng cao', 'High-level Applying', NULL, 3, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000404/')
ON CONFLICT DO NOTHING;

-- 5. QUESTION_FORMAT (SINGLE_CHOICE, SHORT_ANSWER)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8200-000000000501', '00000000-0000-7000-8100-000000000005', 'SINGLE_CHOICE', 'single_choice', 1, 'Trắc nghiệm một đáp án', 'Single choice', '[MẪU] Dạng câu hỏi trắc nghiệm một đáp án đúng', 0, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000501/'),
    ('00000000-0000-7000-8200-000000000502', '00000000-0000-7000-8100-000000000005', 'SHORT_ANSWER', 'short_answer', 2, 'Trả lời ngắn', 'Short answer', '[MẪU] Dạng câu hỏi điền đáp án ngắn', 1, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000502/')
ON CONFLICT DO NOTHING;

-- 6. TASK_TYPE (SINGLE, CLUSTER) — last 3 groups start description with "[MẪU]"
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8200-000000000601', '00000000-0000-7000-8100-000000000006', 'SINGLE', 'single', 1, 'Câu đơn', 'Single question', '[MẪU] Câu hỏi độc lập', 0, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000601/'),
    ('00000000-0000-7000-8200-000000000602', '00000000-0000-7000-8100-000000000006', 'CLUSTER', 'cluster', 2, 'Câu chùm', 'Question cluster', '[MẪU] Nhóm câu hỏi dùng chung ngữ liệu', 1, 1, true, NULL, 1, '/00000000-0000-7000-8200-000000000602/')
ON CONFLICT DO NOTHING;


-- 8. KNOWLEDGE (TREE: 3 chapters 0D1/0D2/0D3; 0D1 has 3 lessons + 14 leaves; 0D2 & 0D3 each have 2 lessons x 3 leaves = 6 leaves)
-- Chapters (depth 1)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8200-000000001001', '00000000-0000-7000-8100-000000000009', '0D1', '0d1', 1, 'Mệnh đề. Tập hợp', 'Logic and Sets', '[MẪU] Chương 1 Toán 10', 0, 1, false, NULL, 1, '/00000000-0000-7000-8200-000000001001/'),
    ('00000000-0000-7000-8200-000000001002', '00000000-0000-7000-8100-000000000009', '0D2', '0d2', 2, 'Bất phương trình và hệ bất phương trình bậc nhất hai ẩn', 'Linear Inequalities in Two Variables', '[MẪU] Chương 2 Toán 10', 1, 1, false, NULL, 1, '/00000000-0000-7000-8200-000000001002/'),
    ('00000000-0000-7000-8200-000000001003', '00000000-0000-7000-8100-000000000009', '0D3', '0d3', 3, 'Hàm số bậc hai và đồ thị', 'Quadratic Functions and Graphs', '[MẪU] Chương 3 Toán 10', 2, 1, false, NULL, 1, '/00000000-0000-7000-8200-000000001003/')
ON CONFLICT (group_id, code_normalized) DO NOTHING;

-- Lessons (depth 2)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8200-000000001101', '00000000-0000-7000-8100-000000000009', '0D1-1', '0d1-1', 4, 'Mệnh đề', 'Propositions', '[MẪU] Bài 1 chương 0D1', 0, 1, false, '00000000-0000-7000-8200-000000001001', 2, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001101/'),
    ('00000000-0000-7000-8200-000000001102', '00000000-0000-7000-8100-000000000009', '0D1-2', '0d1-2', 5, 'Tập hợp', 'Sets', '[MẪU] Bài 2 chương 0D1', 1, 1, false, '00000000-0000-7000-8200-000000001001', 2, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001102/'),
    ('00000000-0000-7000-8200-000000001103', '00000000-0000-7000-8100-000000000009', '0D1-3', '0d1-3', 6, 'Các phép toán trên tập hợp', 'Set operations', '[MẪU] Bài 3 chương 0D1', 2, 1, false, '00000000-0000-7000-8200-000000001001', 2, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001103/'),
    ('00000000-0000-7000-8200-000000001104', '00000000-0000-7000-8100-000000000009', '0D2-1', '0d2-1', 7, 'Bất phương trình bậc nhất hai ẩn', 'Linear inequalities in two variables', '[MẪU] Bài 1 chương 0D2', 0, 1, false, '00000000-0000-7000-8200-000000001002', 2, '/00000000-0000-7000-8200-000000001002/00000000-0000-7000-8200-000000001104/'),
    ('00000000-0000-7000-8200-000000001105', '00000000-0000-7000-8100-000000000009', '0D2-2', '0d2-2', 8, 'Hệ bất phương trình bậc nhất hai ẩn', 'Systems of linear inequalities', '[MẪU] Bài 2 chương 0D2', 1, 1, false, '00000000-0000-7000-8200-000000001002', 2, '/00000000-0000-7000-8200-000000001002/00000000-0000-7000-8200-000000001105/'),
    ('00000000-0000-7000-8200-000000001106', '00000000-0000-7000-8100-000000000009', '0D3-1', '0d3-1', 9, 'Hàm số và đồ thị', 'Functions and graphs', '[MẪU] Bài 1 chương 0D3', 0, 1, false, '00000000-0000-7000-8200-000000001003', 2, '/00000000-0000-7000-8200-000000001003/00000000-0000-7000-8200-000000001106/'),
    ('00000000-0000-7000-8200-000000001107', '00000000-0000-7000-8100-000000000009', '0D3-2', '0d3-2', 10, 'Hàm số bậc hai', 'Quadratic functions', '[MẪU] Bài 2 chương 0D3', 1, 1, false, '00000000-0000-7000-8200-000000001003', 2, '/00000000-0000-7000-8200-000000001003/00000000-0000-7000-8200-000000001107/')
ON CONFLICT (group_id, code_normalized) DO NOTHING;

-- Knowledge leaves (depth 3): 14 leaves under 0D1 (5 + 4 + 5), 6 under 0D2 (3 + 3), 6 under 0D3 (3 + 3)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    -- 0D1-1 (5 leaves)
    ('00000000-0000-7000-8200-000000001201', '00000000-0000-7000-8100-000000000009', '0D1-1-1', '0d1-1-1', 11, 'Khái niệm mệnh đề và mệnh đề chứa biến', 'Propositions and open sentences', '[MẪU] Kiến thức 0D1-1-1', 0, 1, false, '00000000-0000-7000-8200-000000001101', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001101/00000000-0000-7000-8200-000000001201/'),
    ('00000000-0000-7000-8200-000000001202', '00000000-0000-7000-8100-000000000009', '0D1-1-2', '0d1-1-2', 12, 'Phủ định của một mệnh đề', 'Negation of a proposition', '[MẪU] Kiến thức 0D1-1-2', 1, 1, false, '00000000-0000-7000-8200-000000001101', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001101/00000000-0000-7000-8200-000000001202/'),
    ('00000000-0000-7000-8200-000000001203', '00000000-0000-7000-8100-000000000009', '0D1-1-3', '0d1-1-3', 13, 'Mệnh đề kéo theo và mệnh đề đảo', 'Conditional and converse propositions', '[MẪU] Kiến thức 0D1-1-3', 2, 1, false, '00000000-0000-7000-8200-000000001101', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001101/00000000-0000-7000-8200-000000001203/'),
    ('00000000-0000-7000-8200-000000001204', '00000000-0000-7000-8100-000000000009', '0D1-1-4', '0d1-1-4', 14, 'Mệnh đề tương đương', 'Biconditional propositions', '[MẪU] Kiến thức 0D1-1-4', 3, 1, false, '00000000-0000-7000-8200-000000001101', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001101/00000000-0000-7000-8200-000000001204/'),
    ('00000000-0000-7000-8200-000000001205', '00000000-0000-7000-8100-000000000009', '0D1-1-5', '0d1-1-5', 15, 'Mệnh đề với kí hiệu với mọi, tồn tại', 'Universal and existential quantifiers', '[MẪU] Kiến thức 0D1-1-5', 4, 1, false, '00000000-0000-7000-8200-000000001101', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001101/00000000-0000-7000-8200-000000001205/'),
    -- 0D1-2 (4 leaves)
    ('00000000-0000-7000-8200-000000001206', '00000000-0000-7000-8100-000000000009', '0D1-2-1', '0d1-2-1', 16, 'Cách xác định tập hợp', 'Set representation', '[MẪU] Kiến thức 0D1-2-1', 0, 1, false, '00000000-0000-7000-8200-000000001102', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001102/00000000-0000-7000-8200-000000001206/'),
    ('00000000-0000-7000-8200-000000001207', '00000000-0000-7000-8100-000000000009', '0D1-2-2', '0d1-2-2', 17, 'Tập con và tập hợp bằng nhau', 'Subsets and equal sets', '[MẪU] Kiến thức 0D1-2-2', 1, 1, false, '00000000-0000-7000-8200-000000001102', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001102/00000000-0000-7000-8200-000000001207/'),
    ('00000000-0000-7000-8200-000000001208', '00000000-0000-7000-8100-000000000009', '0D1-2-3', '0d1-2-3', 18, 'Các tập hợp số', 'Number sets', '[MẪU] Kiến thức 0D1-2-3', 2, 1, false, '00000000-0000-7000-8200-000000001102', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001102/00000000-0000-7000-8200-000000001208/'),
    ('00000000-0000-7000-8200-000000001209', '00000000-0000-7000-8100-000000000009', '0D1-2-4', '0d1-2-4', 19, 'Khoảng, đoạn, nửa khoảng', 'Intervals on the real line', '[MẪU] Kiến thức 0D1-2-4', 3, 1, false, '00000000-0000-7000-8200-000000001102', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001102/00000000-0000-7000-8200-000000001209/'),
    -- 0D1-3 (5 leaves)
    ('00000000-0000-7000-8200-000000001210', '00000000-0000-7000-8100-000000000009', '0D1-3-1', '0d1-3-1', 20, 'Giao của hai tập hợp', 'Intersection of sets', '[MẪU] Kiến thức 0D1-3-1', 0, 1, false, '00000000-0000-7000-8200-000000001103', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001103/00000000-0000-7000-8200-000000001210/'),
    ('00000000-0000-7000-8200-000000001211', '00000000-0000-7000-8100-000000000009', '0D1-3-2', '0d1-3-2', 21, 'Hợp của hai tập hợp', 'Union of sets', '[MẪU] Kiến thức 0D1-3-2', 1, 1, false, '00000000-0000-7000-8200-000000001103', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001103/00000000-0000-7000-8200-000000001211/'),
    ('00000000-0000-7000-8200-000000001212', '00000000-0000-7000-8100-000000000009', '0D1-3-3', '0d1-3-3', 22, 'Hiệu và phần bù của hai tập hợp', 'Set difference and complement', '[MẪU] Kiến thức 0D1-3-3', 2, 1, false, '00000000-0000-7000-8200-000000001103', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001103/00000000-0000-7000-8200-000000001212/'),
    ('00000000-0000-7000-8200-000000001213', '00000000-0000-7000-8100-000000000009', '0D1-3-4', '0d1-3-4', 23, 'Phép toán trên khoảng, đoạn', 'Operations on intervals', '[MẪU] Kiến thức 0D1-3-4', 3, 1, false, '00000000-0000-7000-8200-000000001103', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001103/00000000-0000-7000-8200-000000001213/'),
    ('00000000-0000-7000-8200-000000001214', '00000000-0000-7000-8100-000000000009', '0D1-3-5', '0d1-3-5', 24, 'Bài toán đếm phần tử bằng biểu đồ Ven', 'Counting elements with Venn diagrams', '[MẪU] Kiến thức 0D1-3-5', 4, 1, false, '00000000-0000-7000-8200-000000001103', 3, '/00000000-0000-7000-8200-000000001001/00000000-0000-7000-8200-000000001103/00000000-0000-7000-8200-000000001214/'),
    -- 0D2-1 (3 leaves)
    ('00000000-0000-7000-8200-000000001215', '00000000-0000-7000-8100-000000000009', '0D2-1-1', '0d2-1-1', 25, 'Nhận dạng bất phương trình bậc nhất hai ẩn', 'Identifying linear inequalities', '[MẪU] Kiến thức 0D2-1-1', 0, 1, false, '00000000-0000-7000-8200-000000001104', 3, '/00000000-0000-7000-8200-000000001002/00000000-0000-7000-8200-000000001104/00000000-0000-7000-8200-000000001215/'),
    ('00000000-0000-7000-8200-000000001216', '00000000-0000-7000-8100-000000000009', '0D2-1-2', '0d2-1-2', 26, 'Biểu diễn miền nghiệm bất phương trình', 'Graphing solution regions', '[MẪU] Kiến thức 0D2-1-2', 1, 1, false, '00000000-0000-7000-8200-000000001104', 3, '/00000000-0000-7000-8200-000000001002/00000000-0000-7000-8200-000000001104/00000000-0000-7000-8200-000000001216/'),
    ('00000000-0000-7000-8200-000000001217', '00000000-0000-7000-8100-000000000009', '0D2-1-3', '0d2-1-3', 27, 'Ứng dụng thực tiễn bất phương trình', 'Practical applications of linear inequalities', '[MẪU] Kiến thức 0D2-1-3', 2, 1, false, '00000000-0000-7000-8200-000000001104', 3, '/00000000-0000-7000-8200-000000001002/00000000-0000-7000-8200-000000001104/00000000-0000-7000-8200-000000001217/'),
    -- 0D2-2 (3 leaves)
    ('00000000-0000-7000-8200-000000001218', '00000000-0000-7000-8100-000000000009', '0D2-2-1', '0d2-2-1', 28, 'Miền nghiệm của hệ bất phương trình', 'Feasible region of a system', '[MẪU] Kiến thức 0D2-2-1', 0, 1, false, '00000000-0000-7000-8200-000000001105', 3, '/00000000-0000-7000-8200-000000001002/00000000-0000-7000-8200-000000001105/00000000-0000-7000-8200-000000001218/'),
    ('00000000-0000-7000-8200-000000001219', '00000000-0000-7000-8100-000000000009', '0D2-2-2', '0d2-2-2', 29, 'Tìm giá trị lớn nhất, nhỏ nhất trên miền đa giác', 'Linear programming optimization', '[MẪU] Kiến thức 0D2-2-2', 1, 1, false, '00000000-0000-7000-8200-000000001105', 3, '/00000000-0000-7000-8200-000000001002/00000000-0000-7000-8200-000000001105/00000000-0000-7000-8200-000000001219/'),
    ('00000000-0000-7000-8200-000000001220', '00000000-0000-7000-8100-000000000009', '0D2-2-3', '0d2-2-3', 30, 'Bài toán tối ưu kinh tế', 'Economic optimization problems', '[MẪU] Kiến thức 0D2-2-3', 2, 1, false, '00000000-0000-7000-8200-000000001105', 3, '/00000000-0000-7000-8200-000000001002/00000000-0000-7000-8200-000000001105/00000000-0000-7000-8200-000000001220/'),
    -- 0D3-1 (3 leaves)
    ('00000000-0000-7000-8200-000000001221', '00000000-0000-7000-8100-000000000009', '0D3-1-1', '0d3-1-1', 31, 'Tập xác định của hàm số', 'Domain of a function', '[MẪU] Kiến thức 0D3-1-1', 0, 1, false, '00000000-0000-7000-8200-000000001106', 3, '/00000000-0000-7000-8200-000000001003/00000000-0000-7000-8200-000000001106/00000000-0000-7000-8200-000000001221/'),
    ('00000000-0000-7000-8200-000000001222', '00000000-0000-7000-8100-000000000009', '0D3-1-2', '0d3-1-2', 32, 'Sự biến thiên của hàm số', 'Monotonicity of a function', '[MẪU] Kiến thức 0D3-1-2', 1, 1, false, '00000000-0000-7000-8200-000000001106', 3, '/00000000-0000-7000-8200-000000001003/00000000-0000-7000-8200-000000001106/00000000-0000-7000-8200-000000001222/'),
    ('00000000-0000-7000-8200-000000001223', '00000000-0000-7000-8100-000000000009', '0D3-1-3', '0d3-1-3', 33, 'Đọc đồ thị hàm số', 'Reading function graphs', '[MẪU] Kiến thức 0D3-1-3', 2, 1, false, '00000000-0000-7000-8200-000000001106', 3, '/00000000-0000-7000-8200-000000001003/00000000-0000-7000-8200-000000001106/00000000-0000-7000-8200-000000001223/'),
    -- 0D3-2 (3 leaves)
    ('00000000-0000-7000-8200-000000001224', '00000000-0000-7000-8100-000000000009', '0D3-2-1', '0d3-2-1', 34, 'Đỉnh và trục đối xứng của parabol', 'Vertex and axis of symmetry', '[MẪU] Kiến thức 0D3-2-1', 0, 1, false, '00000000-0000-7000-8200-000000001107', 3, '/00000000-0000-7000-8200-000000001003/00000000-0000-7000-8200-000000001107/00000000-0000-7000-8200-000000001224/'),
    ('00000000-0000-7000-8200-000000001225', '00000000-0000-7000-8100-000000000009', '0D3-2-2', '0d3-2-2', 35, 'Xác định phương trình parabol', 'Determining quadratic equations', '[MẪU] Kiến thức 0D3-2-2', 1, 1, false, '00000000-0000-7000-8200-000000001107', 3, '/00000000-0000-7000-8200-000000001003/00000000-0000-7000-8200-000000001107/00000000-0000-7000-8200-000000001225/'),
    ('00000000-0000-7000-8200-000000001226', '00000000-0000-7000-8100-000000000009', '0D3-2-3', '0d3-2-3', 36, 'Ứng dụng hàm số bậc hai', 'Applications of quadratic functions', '[MẪU] Kiến thức 0D3-2-3', 2, 1, false, '00000000-0000-7000-8200-000000001107', 3, '/00000000-0000-7000-8200-000000001003/00000000-0000-7000-8200-000000001107/00000000-0000-7000-8200-000000001226/')
ON CONFLICT (group_id, code_normalized) DO NOTHING;

-- Link all 3 chapters to SUBJECT.TOAN and GRADE.G10
INSERT INTO catalog.catalog_item_ref (item_id, ref_item_id)
VALUES
    ('00000000-0000-7000-8200-000000001001', '00000000-0000-7000-8200-000000000201'),
    ('00000000-0000-7000-8200-000000001001', '00000000-0000-7000-8200-000000000301'),
    ('00000000-0000-7000-8200-000000001002', '00000000-0000-7000-8200-000000000201'),
    ('00000000-0000-7000-8200-000000001002', '00000000-0000-7000-8200-000000000301'),
    ('00000000-0000-7000-8200-000000001003', '00000000-0000-7000-8200-000000000201'),
    ('00000000-0000-7000-8200-000000001003', '00000000-0000-7000-8200-000000000301')
ON CONFLICT (item_id, ref_item_id) DO NOTHING;

-- Sync last_value on all groups so newly created items never collide with seeded values
UPDATE catalog.catalog_group g
SET last_value = GREATEST(
    g.last_value,
    COALESCE((SELECT MAX(i.value) FROM catalog.catalog_item i WHERE i.group_id = g.id), 0)
);


-- ===================== Đợt 2 (BE-1, UNI-2607): dữ liệu mẫu PBI-03/04 =====================

-- 9. EXAM: 5 kỳ thi còn lại (BCA, VACT, SPT, SP2E, THPTQG) theo spec mục 6
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8400-000000000103', '00000000-0000-7000-8100-000000000001', 'BCA', 'bca', 3, 'Đánh giá tuyển sinh Công an nhân dân (BCA)', 'People''s Public Security admission assessment (BCA)', NULL, 2, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000103/'),
    ('00000000-0000-7000-8400-000000000104', '00000000-0000-7000-8100-000000000001', 'VACT', 'vact', 4, 'Đánh giá năng lực ĐHQG TP.HCM (V-ACT)', 'VNU-HCM Competency Assessment (V-ACT)', NULL, 3, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000104/'),
    ('00000000-0000-7000-8400-000000000105', '00000000-0000-7000-8100-000000000001', 'SPT', 'spt', 5, 'Đánh giá năng lực ĐH Sư phạm Hà Nội (SPT)', 'HNUE competency assessment (SPT)', NULL, 4, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000105/'),
    ('00000000-0000-7000-8400-000000000106', '00000000-0000-7000-8100-000000000001', 'SP2E', 'sp2e', 6, 'Đánh giá năng lực ĐH Sư phạm Hà Nội 2 (SP2E)', 'HNUE2 competency assessment (SP2E)', NULL, 5, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000106/'),
    ('00000000-0000-7000-8400-000000000107', '00000000-0000-7000-8100-000000000001', 'THPTQG', 'thptqg', 7, 'Tốt nghiệp THPT (THPTQG)', 'National high school graduation exam (THPTQG)', NULL, 6, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000107/')
ON CONFLICT DO NOTHING;

-- 9b. EXAM: H-SCA, V-SAT (UNI-2688, spec H36). Tên viết tắt theo ghi chú BA, chưa có tên đầy đủ chính thức.
-- `value` cấp trên mốc cao nhất của nhóm (kể cả mục quản trị đã tạo sau bảy kỳ đầu) và không cấp lại khi chạy lại;
-- mục đã có cùng mã giữ nguyên id, value của nó. Mục đã đổi mã (PATCH cho phép) vẫn chiếm id seed nên bỏ qua
-- theo id giống bảy kỳ thi đầu, tránh đụng khóa chính; giữ nguyên mã và value mới của nó.
WITH seed(ordinal, code, name_vi, name_en, id) AS (
    VALUES
      (1, 'HSCA', 'Đánh giá năng lực H-SCA', 'H-SCA assessment', '00000000-0000-7000-8400-000000000108'::uuid),
      (2, 'VSAT', 'Đánh giá năng lực V-SAT', 'V-SAT assessment', '00000000-0000-7000-8400-000000000109'::uuid)
), missing AS (
    SELECT s.*, row_number() OVER (ORDER BY s.ordinal) AS allocation
    FROM seed s
    WHERE NOT EXISTS (
        SELECT 1 FROM catalog.catalog_item i
        WHERE i.group_id = '00000000-0000-7000-8100-000000000001'
          AND i.code_normalized = lower(s.code)
    )
    AND NOT EXISTS (
        SELECT 1 FROM catalog.catalog_item i WHERE i.id = s.id
    )
)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
SELECT m.id, g.id, m.code, lower(m.code),
       GREATEST(g.last_value, COALESCE((SELECT MAX(i.value) FROM catalog.catalog_item i WHERE i.group_id = g.id), 0)) + m.allocation,
       m.name_vi, m.name_en, NULL, 6 + m.ordinal, 1, true, NULL, 1, '/' || m.id::text || '/'
FROM missing m JOIN catalog.catalog_group g ON g.id = '00000000-0000-7000-8100-000000000001'
ON CONFLICT (group_id, code_normalized) DO NOTHING;

-- Chuẩn lại tên hai kỳ đã seed ở đợt 1 theo spec mục 6. Mỗi cột chỉ đổi khi còn đúng bản seed cũ:
-- tên admin đã sửa không bị ghi đè khi seed chạy lại (AD-35), DB cũ vẫn nhận tên mới.
-- (HSA en: bản cũ trùng bản mới nên không cần đổi.)
UPDATE catalog.catalog_item SET name_vi = 'Đánh giá năng lực ĐHQG Hà Nội (HSA)'
WHERE id = '00000000-0000-7000-8200-000000000101' AND name_vi = 'Đánh giá năng lực ĐHQGHN (HSA)';
UPDATE catalog.catalog_item SET name_vi = 'Đánh giá năng lực Bộ Quốc phòng (QDA)'
WHERE id = '00000000-0000-7000-8200-000000000102' AND name_vi = 'Đánh giá năng lực ĐHSPHN (QDA)';
UPDATE catalog.catalog_item SET name_en = 'Ministry of Defence Competency Assessment (QDA)'
WHERE id = '00000000-0000-7000-8200-000000000102' AND name_en = 'QDA Competency Assessment';

-- 10. SUBJECT: 6 môn bổ sung (mã mới theo spec mục 6; không đổi mã các môn đợt 1)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8400-000000000213', '00000000-0000-7000-8100-000000000002', 'VIETNAMESE', 'vietnamese', 13, 'Tiếng Việt', 'Vietnamese', NULL, 12, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000213/'),
    ('00000000-0000-7000-8400-000000000214', '00000000-0000-7000-8100-000000000002', 'ECON_LAW', 'econ_law', 14, 'Giáo dục kinh tế và pháp luật', 'Economic and Legal Education', NULL, 13, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000214/'),
    ('00000000-0000-7000-8400-000000000215', '00000000-0000-7000-8100-000000000002', 'INFORMATICS', 'informatics', 15, 'Tin học', 'Informatics', NULL, 14, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000215/'),
    ('00000000-0000-7000-8400-000000000216', '00000000-0000-7000-8100-000000000002', 'TECH_INDUSTRY', 'tech_industry', 16, 'Công nghệ (định hướng công nghiệp)', 'Technology (industrial)', NULL, 15, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000216/'),
    ('00000000-0000-7000-8400-000000000217', '00000000-0000-7000-8100-000000000002', 'TECH_AGRICULTURE', 'tech_agriculture', 17, 'Công nghệ (định hướng nông nghiệp)', 'Technology (agricultural)', NULL, 16, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000217/'),
    ('00000000-0000-7000-8400-000000000218', '00000000-0000-7000-8100-000000000002', 'SCIENTIFIC_THINKING', 'scientific_thinking', 18, 'Tư duy khoa học', 'Scientific thinking', NULL, 17, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000218/')
ON CONFLICT DO NOTHING;

-- 11. GRADE: Liên lớp (V-ACT, câu dùng kiến thức nhiều lớp)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8400-000000000304', '00000000-0000-7000-8100-000000000003', 'GRADE_MIXED', 'grade_mixed', 4, 'Liên lớp', 'Mixed grade', NULL, 3, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000304/')
ON CONFLICT DO NOTHING;

-- 12. QUESTION_FORMAT: 6 dạng còn lại của B6 (SINGLE_CHOICE, SHORT_ANSWER đã có ở đợt 1)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8400-000000000503', '00000000-0000-7000-8100-000000000005', 'MULTIPLE_CHOICE', 'multiple_choice', 3, 'Trắc nghiệm nhiều đáp án', 'Multiple choice', '[MẪU] Dạng câu hỏi trắc nghiệm nhiều đáp án đúng', 2, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000503/'),
    ('00000000-0000-7000-8400-000000000504', '00000000-0000-7000-8100-000000000005', 'CLOZE', 'cloze', 4, 'Điền khuyết', 'Cloze', '[MẪU] Dạng câu hỏi điền khuyết', 3, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000504/'),
    ('00000000-0000-7000-8400-000000000505', '00000000-0000-7000-8100-000000000005', 'QUESTION_SET', 'question_set', 5, 'Bộ câu hỏi', 'Question set', '[MẪU] Dạng câu hỏi bộ câu hỏi', 4, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000505/'),
    ('00000000-0000-7000-8400-000000000506', '00000000-0000-7000-8100-000000000005', 'TRUE_FALSE', 'true_false', 6, 'Đúng/Sai', 'True/False', '[MẪU] Dạng câu hỏi đúng sai', 5, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000506/'),
    ('00000000-0000-7000-8400-000000000507', '00000000-0000-7000-8100-000000000005', 'MATCHING', 'matching', 7, 'Ghép đôi', 'Matching', '[MẪU] Dạng câu hỏi ghép đôi', 6, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000507/'),
    ('00000000-0000-7000-8400-000000000508', '00000000-0000-7000-8100-000000000005', 'ESSAY', 'essay', 8, 'Tự luận', 'Essay', '[MẪU] Dạng câu hỏi tự luận', 7, 1, true, NULL, 1, '/00000000-0000-7000-8400-000000000508/')
ON CONFLICT DO NOTHING;

-- 13. SUBJECT_COMBINATION: QD01..QD05 (QDA), CA1..4 (BCA)
WITH seed(id, group_id, code, code_normalized, seed_value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path) AS (
VALUES
    ('00000000-0000-7000-8400-000000000709', '00000000-0000-7000-8100-000000000007', 'QD01', 'qd01', 9, 'QĐ01 · Vật lí – Hóa học', 'QD01 · Physics – Chemistry', '[MẪU] Tổ hợp QĐ01 (QDA)', 0, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000709/'),
    ('00000000-0000-7000-8400-000000000710', '00000000-0000-7000-8100-000000000007', 'QD02', 'qd02', 10, 'QĐ02 · Hóa học – Sinh học', 'QD02 · Chemistry – Biology', '[MẪU] Tổ hợp QĐ02 (QDA)', 1, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000710/'),
    ('00000000-0000-7000-8400-000000000711', '00000000-0000-7000-8100-000000000007', 'QD03', 'qd03', 11, 'QĐ03 · Vật lí – Sinh học', 'QD03 · Physics – Biology', '[MẪU] Tổ hợp QĐ03 (QDA)', 2, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000711/'),
    ('00000000-0000-7000-8400-000000000712', '00000000-0000-7000-8100-000000000007', 'QD04', 'qd04', 12, 'QĐ04 · Lịch sử – Địa lí', 'QD04 · History – Geography', '[MẪU] Tổ hợp QĐ04 (QDA)', 3, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000712/'),
    ('00000000-0000-7000-8400-000000000717', '00000000-0000-7000-8100-000000000007', 'QD05', 'qd05', 17, 'QĐ05 · Tiếng Anh', 'QD05 · English', '[MẪU] Tổ hợp QĐ05 (QDA)', 4, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000717/'),
    ('00000000-0000-7000-8400-000000000713', '00000000-0000-7000-8100-000000000007', 'CA1', 'ca1', 13, 'CA1 · Vật lí', 'CA1 · Physics', '[MẪU] Tổ hợp CA1 (BCA)', 5, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000713/'),
    ('00000000-0000-7000-8400-000000000714', '00000000-0000-7000-8100-000000000007', 'CA2', 'ca2', 14, 'CA2 · Hóa học', 'CA2 · Chemistry', '[MẪU] Tổ hợp CA2 (BCA)', 6, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000714/'),
    ('00000000-0000-7000-8400-000000000715', '00000000-0000-7000-8100-000000000007', 'CA3', 'ca3', 15, 'CA3 · Sinh học', 'CA3 · Biology', '[MẪU] Tổ hợp CA3 (BCA)', 7, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000715/'),
    ('00000000-0000-7000-8400-000000000716', '00000000-0000-7000-8100-000000000007', 'CA4', 'ca4', 16, 'CA4 · Địa lí', 'CA4 · Geography', '[MẪU] Tổ hợp CA4 (BCA)', 8, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000716/')
), missing AS (
    SELECT s.*, row_number() OVER (ORDER BY seed_value) AS allocation
    FROM seed s
    WHERE NOT EXISTS (
        SELECT 1 FROM catalog.catalog_item i
        WHERE i.group_id = s.group_id::uuid AND i.code_normalized = s.code_normalized
    )
)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
SELECT m.id::uuid, m.group_id::uuid, m.code, m.code_normalized,
       GREATEST(g.last_value, COALESCE((SELECT MAX(i.value) FROM catalog.catalog_item i WHERE i.group_id = g.id), 0)) + m.allocation,
       m.name_vi, m.name_en, m.description, m.display_order, m.status, m.is_system, m.parent_id::uuid, m.depth, m.path
FROM missing m JOIN catalog.catalog_group g ON g.id = m.group_id::uuid
ON CONFLICT (group_id, code_normalized) DO NOTHING;

-- Resolve seeded combinations by code: existing items and versions can predate these UUIDs.
WITH seed(code, version_id, exam_code, branch, source_note) AS (
    VALUES
      ('qd01', '00000000-0000-7000-8500-000000000709', 'qda', 'SCIENCE', '[MẪU] Tổ hợp QĐ01'),
      ('qd02', '00000000-0000-7000-8500-000000000710', 'qda', 'SCIENCE', '[MẪU] Tổ hợp QĐ02'),
      ('qd03', '00000000-0000-7000-8500-000000000711', 'qda', 'SCIENCE', '[MẪU] Tổ hợp QĐ03'),
      ('qd04', '00000000-0000-7000-8500-000000000712', 'qda', 'SCIENCE', '[MẪU] Tổ hợp QĐ04'),
      ('qd05', '00000000-0000-7000-8500-000000000717', 'qda', 'ENGLISH', '[MẪU] Tổ hợp QĐ05'),
      ('ca1', '00000000-0000-7000-8500-000000000713', 'bca', 'SCIENCE', '[MẪU] Tổ hợp CA1'),
      ('ca2', '00000000-0000-7000-8500-000000000714', 'bca', 'SCIENCE', '[MẪU] Tổ hợp CA2'),
      ('ca3', '00000000-0000-7000-8500-000000000715', 'bca', 'SCIENCE', '[MẪU] Tổ hợp CA3'),
      ('ca4', '00000000-0000-7000-8500-000000000716', 'bca', 'SCIENCE', '[MẪU] Tổ hợp CA4')
)
INSERT INTO catalog.combination_version (id, item_id, version_no, exam_item_id, branch, source_note, is_current)
SELECT s.version_id::uuid, i.id, 1, e.id, s.branch, s.source_note, true
FROM seed s
JOIN catalog.catalog_item i ON i.group_id = '00000000-0000-7000-8100-000000000007' AND i.code_normalized = s.code
JOIN catalog.catalog_item e ON e.group_id = '00000000-0000-7000-8100-000000000001' AND e.code_normalized = s.exam_code
WHERE NOT EXISTS (SELECT 1 FROM catalog.combination_version v WHERE v.item_id = i.id)
ON CONFLICT DO NOTHING;

UPDATE catalog.combination_version v
SET branch = CASE WHEN i.code_normalized = 'qd05' THEN 'ENGLISH' ELSE 'SCIENCE' END
FROM catalog.catalog_item i
WHERE v.item_id = i.id AND v.version_no = 1 AND v.is_current AND v.branch IS NULL
  AND i.group_id = '00000000-0000-7000-8100-000000000007'
  AND i.code_normalized IN ('qd01', 'qd02', 'qd03', 'qd04', 'qd05', 'ca1', 'ca2', 'ca3', 'ca4');

WITH seed(code, subject_code, position) AS (
    VALUES ('qd01', 'ly', 0), ('qd01', 'hoa', 1),
           ('qd02', 'hoa', 0), ('qd02', 'sinh', 1),
           ('qd03', 'ly', 0), ('qd03', 'sinh', 1),
           ('qd04', 'su', 0), ('qd04', 'dia', 1),
           ('qd05', 'anh', 0), ('ca1', 'ly', 0), ('ca2', 'hoa', 0),
           ('ca3', 'sinh', 0), ('ca4', 'dia', 0)
)
INSERT INTO catalog.combination_subject (combination_version_id, subject_item_id, position)
SELECT v.id, subject.id, s.position
FROM seed s
JOIN catalog.catalog_item i ON i.group_id = '00000000-0000-7000-8100-000000000007' AND i.code_normalized = s.code
JOIN catalog.combination_version v ON v.item_id = i.id AND v.version_no = 1 AND v.is_current
JOIN catalog.catalog_item subject ON subject.group_id = '00000000-0000-7000-8100-000000000002' AND subject.code_normalized = s.subject_code
WHERE NOT EXISTS (SELECT 1 FROM catalog.combination_subject existing WHERE existing.combination_version_id = v.id)
ON CONFLICT DO NOTHING;

-- 14. COMPETENCY: 5 năng lực V-ACT (cây một cấp, nhóm hệ thống tạo bởi migration)
INSERT INTO catalog.catalog_item (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, parent_id, depth, path)
VALUES
    ('00000000-0000-7000-8400-000000000901', '00000000-0000-7000-8100-00000000000a', 'VACT-NN-TV', 'vact-nn-tv', 1, 'Sử dụng ngôn ngữ · Tiếng Việt', 'Language use · Vietnamese', NULL, 0, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000901/'),
    ('00000000-0000-7000-8400-000000000902', '00000000-0000-7000-8100-00000000000a', 'VACT-NN-TA', 'vact-nn-ta', 2, 'Sử dụng ngôn ngữ · Tiếng Anh', 'Language use · English', NULL, 1, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000902/'),
    ('00000000-0000-7000-8400-000000000903', '00000000-0000-7000-8100-00000000000a', 'VACT-TOAN', 'vact-toan', 3, 'Toán học', 'Mathematics', NULL, 2, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000903/'),
    ('00000000-0000-7000-8400-000000000904', '00000000-0000-7000-8100-00000000000a', 'VACT-TD-LOGIC', 'vact-td-logic', 4, 'Tư duy khoa học · Logic, phân tích số liệu', 'Scientific thinking · Logic and data analysis', NULL, 3, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000904/'),
    ('00000000-0000-7000-8400-000000000905', '00000000-0000-7000-8100-00000000000a', 'VACT-TD-SL', 'vact-td-sl', 5, 'Tư duy khoa học · Suy luận khoa học', 'Scientific thinking · Scientific reasoning', NULL, 4, 1, false, NULL, 1, '/00000000-0000-7000-8400-000000000905/')
ON CONFLICT (group_id, code_normalized) DO NOTHING;

-- Mỗi năng lực gắn một môn và kỳ thi V-ACT qua catalog_item_ref (D3)
INSERT INTO catalog.catalog_item_ref (item_id, ref_item_id)
VALUES
    ('00000000-0000-7000-8400-000000000901', '00000000-0000-7000-8400-000000000213'),
    ('00000000-0000-7000-8400-000000000901', '00000000-0000-7000-8400-000000000104'),
    ('00000000-0000-7000-8400-000000000902', '00000000-0000-7000-8200-000000000203'),
    ('00000000-0000-7000-8400-000000000902', '00000000-0000-7000-8400-000000000104'),
    ('00000000-0000-7000-8400-000000000903', '00000000-0000-7000-8200-000000000201'),
    ('00000000-0000-7000-8400-000000000903', '00000000-0000-7000-8400-000000000104'),
    ('00000000-0000-7000-8400-000000000904', '00000000-0000-7000-8400-000000000218'),
    ('00000000-0000-7000-8400-000000000904', '00000000-0000-7000-8400-000000000104'),
    ('00000000-0000-7000-8400-000000000905', '00000000-0000-7000-8400-000000000218'),
    ('00000000-0000-7000-8400-000000000905', '00000000-0000-7000-8400-000000000104')
ON CONFLICT (item_id, ref_item_id) DO NOTHING;

-- QUESTION_TAG codes use SUBJECT_CONCEPT (uppercase ASCII); names follow UniNote.
-- Separate labels for distinct position-band names allow templates to filter each band.
WITH tag_seed(value, code, name_vi) AS (
    VALUES
      (1, 'VAN_NGHI_LUAN_XA_HOI', 'Nghị luận xã hội'),
      (2, 'VAN_THONG_TIN', 'Văn bản thông tin'),
      (3, 'VAN_KHOA_HOC_CHINH_LUAN', 'Khoa học – chính luận'),
      (4, 'VAN_THO', 'Đoạn trích thơ'),
      (5, 'VAN_VAN_XUOI', 'Văn xuôi'),
      (6, 'VAN_THONG_TIN_NGHI_LUAN', 'Văn bản thông tin – nghị luận'),
      (7, 'VAN_NGHI_LUAN', 'Văn bản nghị luận'),
      (8, 'VAN_THIEU_NHI', 'Văn bản về văn học thiếu nhi'),
      (9, 'VAN_TRUYEN', 'Đoạn trích truyện'),
      (10, 'VAN_KHOA_HOC', 'Văn bản khoa học'),
      (11, 'VAN_TU_KHAC_NHOM', 'Chọn từ hoặc cụm từ không cùng nhóm nghĩa'),
      (12, 'VAN_TU_SAI', 'Xác định từ hoặc cụm từ sai về ngữ pháp, ngữ nghĩa, logic hoặc phong cách'),
      (13, 'VAN_CAP_TU_DIEN', 'Chọn cặp từ hoặc cụm từ thích hợp điền vào chỗ trống'),
      (14, 'VAN_NGU_LIEU_MOT_CAU', 'Đọc một ngữ liệu, trả lời một câu hỏi'),
      (15, 'ANH_HOAN_THANH_CAU', 'Hoàn thành câu'),
      (16, 'ANH_DONG_NGHIA', 'Chọn từ đồng nghĩa'),
      (17, 'ANH_TRAI_NGHIA', 'Chọn từ trái nghĩa'),
      (18, 'ANH_VIET_LAI_CAU', 'Viết lại câu'),
      (19, 'ANH_KET_HOP_CAU', 'Kết hợp hai câu'),
      (20, 'ANH_HOI_THOAI', 'Hoàn thành hội thoại'),
      (21, 'ANH_SAP_XEP_HOI_THOAI', 'Sắp xếp hội thoại'),
      (22, 'ANH_DOC_DIEN', 'Đọc điền từ hoặc cụm từ'),
      (23, 'ANH_DOC_HIEU_1', 'Đọc hiểu 1'),
      (24, 'ANH_DOC_HIEU_2', 'Đọc hiểu 2'),
      (25, 'ANH_TU_DUY_LOGIC', 'Tư duy logic và giải quyết vấn đề'),
      (26, 'ANH_THONG_BAO', 'Đọc thông báo, biển báo'),
      (27, 'ANH_SUY_LUAN', 'Suy luận và giải quyết tình huống'),
      (28, 'ANH_DOC_DIEN_CAU', 'Đọc điền câu'),
      (29, 'ANH_DIEN_TU', 'Đọc điền từ vào đoạn văn'),
      (30, 'ANH_DIEN_CUM_CAU', 'Đọc điền cụm từ, mệnh đề hoặc câu vào đoạn văn'),
      (31, 'ANH_GIAO_TIEP', 'Giao tiếp'),
      (32, 'ANH_SAP_XEP_DOAN', 'Sắp xếp hội thoại hoặc đoạn văn'),
      (33, 'ANH_PHAT_AM', 'Phát âm'),
      (34, 'ANH_TRONG_AM', 'Trọng âm'),
      (35, 'ANH_TU_VUNG_NGU_PHAP', 'Từ vựng – ngữ pháp'),
      (36, 'ANH_DOC_HIEU', 'Đọc hiểu'),
      (37, 'ANH_WORD_FORMATION', 'Word Formation'),
      (38, 'TOAN_DU_LIEU_TONG_HOP', 'Dữ liệu tổng hợp'),
      (39, 'SU_TU_LIEU_LICH_SU', 'Tư liệu lịch sử'),
      (40, 'DIA_CAU_KY_NANG', 'Câu kỹ năng')
), tags AS (
    SELECT value, code, name_vi,
           ('00000000-0000-7000-8601-' || lpad(value::text, 12, '0'))::uuid AS id
    FROM tag_seed
), missing AS (
    SELECT t.*, row_number() OVER (ORDER BY t.value) AS allocation
    FROM tags t
    WHERE NOT EXISTS (
        SELECT 1 FROM catalog.catalog_item i
        WHERE i.group_id = '00000000-0000-7000-8100-000000000008' AND i.code_normalized = lower(t.code)
    )
)
INSERT INTO catalog.catalog_item
    (id, group_id, code, code_normalized, value, name_vi, description, display_order, status, is_system, depth, path)
SELECT m.id, g.id, m.code, lower(m.code),
       GREATEST(g.last_value, COALESCE((SELECT MAX(i.value) FROM catalog.catalog_item i WHERE i.group_id = g.id), 0)) + m.allocation,
       m.name_vi, '[MẪU] Nhãn phân loại câu hỏi', m.value - 1, 1, false, 1, '/' || m.id::text || '/'
FROM missing m JOIN catalog.catalog_group g ON g.id = '00000000-0000-7000-8100-000000000008'
ON CONFLICT (group_id, code_normalized) DO NOTHING;

-- D9 provisional codes: reconcile with R__seed_questionbank_dev_data.sql for BA review.
WITH seed(ordinal, code, name_vi, name_en) AS (
    VALUES
      (41, 'ANH_PHAT_HIEN_LOI', 'Phát hiện lỗi sai', 'Error identification'),
      (42, 'ANH_DIEN_DAT_TUONG_DUONG', 'Diễn đạt tương đương', 'Equivalent expression'),
      (43, 'TD_SAP_XEP_DIEU_KIEN', 'Sắp xếp điều kiện', 'Conditional arrangement'),
      (44, 'TD_XEP_HANG', 'Xếp hạng', 'Ranking'),
      (45, 'TD_BANG_SO_LIEU', 'Bảng số liệu', 'Data table'),
      (46, 'TD_BIEU_DO', 'Biểu đồ', 'Chart'),
      -- Q-45 (UNI-2687): hai nhãn Địa lí cho V-SAT Phần II, tạm thời đến khi BA xác nhận mã.
      (47, 'DIA_BANG_SO_LIEU', 'Địa lí · Bảng số liệu', 'Geography · Data table'),
      (48, 'DIA_BIEU_DO', 'Địa lí · Biểu đồ', 'Geography · Chart')
), missing AS (
    SELECT s.*, row_number() OVER (ORDER BY ordinal) AS allocation,
           ('00000000-0000-7000-8601-' || lpad(s.ordinal::text, 12, '0'))::uuid AS id
    FROM seed s
    WHERE NOT EXISTS (
        SELECT 1 FROM catalog.catalog_item i
        WHERE i.group_id = '00000000-0000-7000-8100-000000000008'
          AND i.code_normalized = lower(s.code)
    )
)
INSERT INTO catalog.catalog_item
        (id, group_id, code, code_normalized, value, name_vi, name_en, description, display_order, status, is_system, depth, path)
    SELECT m.id, g.id, m.code, lower(m.code),
           GREATEST(g.last_value, COALESCE((SELECT MAX(i.value) FROM catalog.catalog_item i WHERE i.group_id = g.id), 0)) + m.allocation,
           m.name_vi, m.name_en, '[MẪU] Nhãn phân loại câu hỏi', m.ordinal - 1, 1, false, 1,
           '/' || m.id::text || '/'
FROM missing m JOIN catalog.catalog_group g ON g.id = '00000000-0000-7000-8100-000000000008'
ON CONFLICT (group_id, code_normalized) DO NOTHING;

-- Sync lại last_value cho mọi nhóm sau khi seed đợt 2
UPDATE catalog.catalog_group g
SET last_value = GREATEST(
    g.last_value,
    COALESCE((SELECT MAX(i.value) FROM catalog.catalog_item i WHERE i.group_id = g.id), 0)
);
