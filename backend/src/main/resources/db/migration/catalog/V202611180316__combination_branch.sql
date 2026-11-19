ALTER TABLE catalog.combination_version
    ADD COLUMN branch varchar(20);

ALTER TABLE catalog.combination_version
    ADD CONSTRAINT chk_combination_version_branch
    CHECK (branch IS NULL OR branch IN ('SCIENCE', 'ENGLISH'));
