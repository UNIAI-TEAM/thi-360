-- UNI-2706 (EX-1): sale listing (working copy) and cover images of an exam.
-- exam.exam already holds `description` and `cover_asset_id`; the listing table does not repeat them (TM 6.1).
-- Exams created before this migration have no listing row: the API reads them as an empty working copy and
-- the publish check blocks them with LISTING_PRICE_MISSING. Nothing is backfilled.
CREATE TABLE exam.exam_listing (
    exam_id uuid PRIMARY KEY REFERENCES exam.exam (id),
    summary text CHECK (summary IS NULL OR char_length(summary) <= 300),
    objectives text CHECK (objectives IS NULL OR char_length(objectives) <= 2000),
    audience text CHECK (audience IS NULL OR char_length(audience) <= 2000),
    list_price_vnd bigint CHECK (list_price_vnd IS NULL OR list_price_vnd >= 0),
    attempts_granted integer CHECK (attempts_granted IS NULL OR attempts_granted BETWEEN 1 AND 100),
    benefit_ai_explanation boolean NOT NULL DEFAULT false,
    benefit_strength_weakness boolean NOT NULL DEFAULT false,
    benefit_practice boolean NOT NULL DEFAULT false,
    benefit_note text CHECK (benefit_note IS NULL OR char_length(benefit_note) <= 1000),
    result_view text NOT NULL DEFAULT 'SCORE_AND_ANSWERS'
        CHECK (result_view IN ('SCORE', 'SCORE_AND_ANSWERS', 'FULL_SOLUTION')),
    updated_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Cover images live in the private object bucket under the `exam-covers/` key prefix (plan EX-1, open question 2).
-- A row with orphaned_at set is no longer the cover of a working copy or of any version; the daily job deletes it
-- (row and object) 7 days after the first sweep that marked it.
CREATE TABLE exam.exam_cover_asset (
    id uuid PRIMARY KEY,
    exam_id uuid NOT NULL REFERENCES exam.exam (id),
    object_key text NOT NULL,
    content_type text NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    width integer NOT NULL CHECK (width > 0),
    height integer NOT NULL CHECK (height > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    orphaned_at timestamptz,
    CONSTRAINT uq_exam_cover_asset_object_key UNIQUE (object_key)
);

CREATE INDEX idx_exam_cover_asset_exam ON exam.exam_cover_asset (exam_id);
CREATE INDEX idx_exam_cover_asset_orphaned ON exam.exam_cover_asset (orphaned_at) WHERE orphaned_at IS NOT NULL;
