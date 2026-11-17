-- BE-4 W-API (UNI-2610): cờ "cấu trúc đã sửa kể từ lần nạp mẫu gần nhất" trên bản làm việc.
-- spec 5.2: nạp lại mẫu / đổi kỳ thi, phạm vi, phần, môn, năm sau khi cấu trúc đã sửa phải có
-- confirmReplace = true (409 EXAM_STRUCTURE_REPLACE_CONFIRM). Cột này là nguồn duy nhất cho
-- luật đó: false sau khi nạp mẫu hoặc sao chép bài, true sau mỗi PUT cấu trúc.
ALTER TABLE exam.exam_structure
    ADD COLUMN edited boolean NOT NULL DEFAULT false;
