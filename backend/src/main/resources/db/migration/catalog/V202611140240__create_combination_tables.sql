CREATE TABLE catalog.combination_version (
    id uuid PRIMARY KEY,
    item_id uuid NOT NULL REFERENCES catalog.catalog_item (id) ON DELETE CASCADE,
    version_no integer NOT NULL CHECK (version_no >= 1),
    exam_item_id uuid NOT NULL REFERENCES catalog.catalog_item (id),
    source_note text,
    is_current boolean NOT NULL DEFAULT true,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_combination_version_item_version_no UNIQUE (item_id, version_no)
);

CREATE UNIQUE INDEX uq_combination_version_one_current
    ON catalog.combination_version (item_id)
    WHERE is_current = true;

CREATE INDEX idx_combination_version_item_id
    ON catalog.combination_version (item_id, version_no DESC);

CREATE INDEX idx_combination_version_exam_item_id
    ON catalog.combination_version (exam_item_id);

CREATE TABLE catalog.combination_subject (
    combination_version_id uuid NOT NULL REFERENCES catalog.combination_version (id) ON DELETE CASCADE,
    subject_item_id uuid NOT NULL REFERENCES catalog.catalog_item (id),
    position integer NOT NULL CHECK (position >= 0),
    PRIMARY KEY (combination_version_id, subject_item_id),
    CONSTRAINT uq_combination_subject_position UNIQUE (combination_version_id, position)
);

CREATE INDEX idx_combination_subject_subject_item_id
    ON catalog.combination_subject (subject_item_id);
