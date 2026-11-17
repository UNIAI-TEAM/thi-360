UPDATE exam.exam_template
SET expert_note = 'Khoa học gồm 5 môn, học viên chọn 3 môn; số câu lần lượt 17, 17 và 16 đang chờ chuyên gia xác nhận.'
WHERE code = 'HSA_FULL'
  AND expert_note = 'Khoa học là một SUBJECT_CHOICE 5 môn, chọn 3; countsByPick 17/17/16 chờ chuyên gia xác nhận.';
