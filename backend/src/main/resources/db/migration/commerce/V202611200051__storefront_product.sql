-- UNI-2712 (CM-1): storefront projection (TM 10.1, E2). One row per exam that is on sale or was discontinued, written by
-- the handlers of ExamPublished / ExamDiscontinued and rebuilt by POST /api/v1/admin/storefront/rebuild. The public API reads
-- only this table. exam_id, exam_item_id, scope_item_id, subject_item_ids and cover_asset_id carry no foreign key: they point
-- into the tables of other modules (RULES 4). bundle_id has none yet: CM-6 adds it with the bundle table (plan B1).
--
-- question_count, duration_minutes and max_points are the lower bound of the totals of the serving paths, the *_max columns
-- the upper bound: paths that differ show a range such as "48-50 cau" (TM 10.1).
-- card and detail are built at write time without the promotion price, which is computed on read (TM 10.1).
-- projection_hash covers every column the projection writes: a replayed event or a rebuild that changes nothing leaves the
-- row (and updated_at) untouched.
CREATE TABLE commerce.storefront_product (
    id uuid PRIMARY KEY,
    exam_id uuid,
    code text NOT NULL,
    code_normalized text NOT NULL,
    version_no integer NOT NULL,
    sale_state text NOT NULL,
    name text NOT NULL,
    search_text text NOT NULL,
    exam_item_id uuid,
    kind_code text NOT NULL,
    scope text NOT NULL,
    scope_item_id uuid,
    subject_item_ids uuid[] NOT NULL DEFAULT '{}',
    part_codes text[] NOT NULL DEFAULT '{}',
    part_names text[] NOT NULL DEFAULT '{}',
    question_count integer,
    question_count_max integer,
    duration_minutes integer,
    duration_minutes_max integer,
    max_points numeric(12, 2),
    max_points_max numeric(12, 2),
    product_type text NOT NULL DEFAULT 'EXAM',
    bundle_id uuid,
    build_mode text NOT NULL DEFAULT 'STANDARD',
    list_price_vnd bigint NOT NULL,
    attempts_granted integer NOT NULL,
    cover_asset_id uuid,
    card jsonb NOT NULL,
    detail jsonb NOT NULL,
    projection_hash text NOT NULL,
    published_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_storefront_product_exam UNIQUE (exam_id),
    CONSTRAINT uq_storefront_product_code UNIQUE (code_normalized),
    CONSTRAINT ck_storefront_product_version CHECK (version_no >= 1),
    CONSTRAINT ck_storefront_product_sale_state CHECK (sale_state IN ('ON_SALE', 'DISCONTINUED', 'UNAVAILABLE')),
    -- UNAVAILABLE exists only for bundles (TM 16.2).
    CONSTRAINT ck_storefront_product_unavailable_bundle CHECK (sale_state <> 'UNAVAILABLE' OR product_type = 'BUNDLE'),
    CONSTRAINT ck_storefront_product_scope CHECK (scope IN ('FULL', 'PART', 'SUBJECT')),
    CONSTRAINT ck_storefront_product_type CHECK (product_type IN ('EXAM', 'BUNDLE')),
    CONSTRAINT ck_storefront_product_owner CHECK (
        (product_type = 'EXAM' AND exam_id IS NOT NULL AND bundle_id IS NULL)
        OR (product_type = 'BUNDLE' AND exam_id IS NULL AND bundle_id IS NOT NULL)),
    CONSTRAINT ck_storefront_product_build_mode CHECK (build_mode IN ('STANDARD', 'REVIEW')),
    CONSTRAINT ck_storefront_product_price CHECK (list_price_vnd >= 0),
    CONSTRAINT ck_storefront_product_attempts CHECK (attempts_granted >= 1)
);

-- Default listing order ("Moi cong bo") and the price orders, always over the on-sale rows.
CREATE INDEX idx_storefront_product_sale_published
    ON commerce.storefront_product (sale_state, published_at DESC, id);
CREATE INDEX idx_storefront_product_sale_price
    ON commerce.storefront_product (sale_state, list_price_vnd, id);
CREATE INDEX idx_storefront_product_kind
    ON commerce.storefront_product (kind_code);
CREATE INDEX idx_storefront_product_cover
    ON commerce.storefront_product (cover_asset_id) WHERE cover_asset_id IS NOT NULL;
CREATE INDEX idx_storefront_product_subjects
    ON commerce.storefront_product USING gin (subject_item_ids);
CREATE INDEX idx_storefront_product_parts
    ON commerce.storefront_product USING gin (part_codes);
-- pg_trgm is enabled by the questionbank migration V202611161400 (plan D16).
CREATE INDEX idx_storefront_product_search
    ON commerce.storefront_product USING gin (search_text gin_trgm_ops);
