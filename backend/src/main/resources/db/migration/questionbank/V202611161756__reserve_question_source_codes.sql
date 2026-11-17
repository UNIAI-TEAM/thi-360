-- A current and a working version can both own a source code. The same code
-- must not be claimed by different questions, even before a draft is published.
CREATE TABLE questionbank.question_source_code_claim (
    source_code_normalized text PRIMARY KEY,
    question_id uuid NOT NULL REFERENCES questionbank.question(id)
);

CREATE INDEX idx_question_source_code_claim_question
    ON questionbank.question_source_code_claim(question_id);

INSERT INTO questionbank.question_source_code_claim (source_code_normalized, question_id)
SELECT source_code_normalized, id FROM questionbank.question
WHERE deleted_at IS NULL AND parent_id IS NULL AND source_code_normalized IS NOT NULL;
