-- UNI-2706 (EX-1, review R1-2, R1-6): a cover is deleted in two steps so a failed or crashed object delete is retried.
-- Step 1 (short transaction): the cleanup sweep stamps delete_requested_at on the rows it will remove and takes a lease
-- (delete_claimed_until) so overlapping sweeps do not delete the same object twice. A row with delete_requested_at can
-- no longer be attached to an exam. Step 2 (no row lock held): the object is deleted, then the row.
-- A row whose object delete failed keeps delete_requested_at; the next sweep takes it again once the lease is released
-- or has run out.
ALTER TABLE exam.exam_cover_asset
    ADD COLUMN delete_requested_at timestamptz,
    ADD COLUMN delete_claimed_until timestamptz;

CREATE INDEX idx_exam_cover_asset_delete ON exam.exam_cover_asset (delete_requested_at) WHERE delete_requested_at IS NOT NULL;

-- A working copy can only point at an existing cover. NOT VALID: rows written before this constraint (the column
-- was never written before EX-1) are not rechecked, every new or changed cover_asset_id is.
ALTER TABLE exam.exam
    ADD CONSTRAINT fk_exam_cover_asset FOREIGN KEY (cover_asset_id) REFERENCES exam.exam_cover_asset (id) NOT VALID;
