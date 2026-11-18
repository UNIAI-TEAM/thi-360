ALTER TABLE exam.exam_template
    ADD COLUMN publishable boolean NOT NULL DEFAULT true,
    ADD COLUMN fixed_counts boolean NOT NULL DEFAULT false;
