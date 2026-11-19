-- UNI-2688 (H-SCA, V-SAT, spec H41-H43, H49): hai cờ khóa của mẫu và mẫu PART được có mã môn.
-- fixed_block_counts: dạng, số câu, số chùm, cỡ chùm của từng nhóm khóa theo mẫu (chỉ bài phạm vi SUBJECT).
-- omitted_locked: nhóm đã lược của mẫu (phần viết) không bật lại được.
-- Không có cột excluded_parts: phần viết của V-SAT Ngữ văn lưu như H-SCA Ngữ văn (H49).
ALTER TABLE exam.exam_template
    ADD COLUMN fixed_block_counts boolean NOT NULL DEFAULT false,
    ADD COLUMN omitted_locked boolean NOT NULL DEFAULT false;

-- H41: mẫu PART của Tiếng Anh V-SAT mang subject_code; mẫu FULL vẫn không được có part_code, subject_code.
ALTER TABLE exam.exam_template DROP CONSTRAINT chk_exam_template_scope_fields;
ALTER TABLE exam.exam_template ADD CONSTRAINT chk_exam_template_scope_fields CHECK (
    (scope = 'FULL' AND part_code IS NULL AND subject_code IS NULL)
    OR scope = 'PART'
    OR (scope = 'SUBJECT' AND (subject_code IS NOT NULL OR build_mode = 'REVIEW'))
);
