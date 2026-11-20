-- UNI-2700 (FU, AD-90): the BCA structure document (H6, FTM spec R-8) fixes grade and difficulty as "Tham khảo".
-- The BCA templates stored gradeEnforcement REQUIRED, which the solver and the checker treat as a binding quota
-- (SolverInputBuilder.matrix, ExamCheckRules); difficultyEnforcement was already REFERENCE. Templates only: exams
-- that already loaded a template keep their own draft.
UPDATE exam.exam_template
SET structure = replace(structure::text, '"gradeEnforcement": "REQUIRED"', '"gradeEnforcement": "REFERENCE"')::jsonb
WHERE kind_code = 'BCA'
  AND structure::text LIKE '%"gradeEnforcement": "REQUIRED"%';
