-- UNI-2650 / K11: exam structures no longer filter by question task type.
DROP INDEX IF EXISTS exam.idx_exam_block_ref_task_type;
ALTER TABLE exam.exam_block_ref
    DROP COLUMN IF EXISTS task_type_item_id;
