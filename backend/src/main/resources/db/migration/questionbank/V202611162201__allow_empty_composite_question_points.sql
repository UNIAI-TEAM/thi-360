-- Empty QUESTION_SET and CLOZE drafts have the sum of zero components. Readiness still
-- requires the components; ordinary questions and children retain positive points.
ALTER TABLE questionbank.question DROP CONSTRAINT question_points_check;
ALTER TABLE questionbank.question ADD CONSTRAINT question_points_check
    CHECK (points > 0 OR type IN ('QUESTION_SET', 'CLOZE'));
