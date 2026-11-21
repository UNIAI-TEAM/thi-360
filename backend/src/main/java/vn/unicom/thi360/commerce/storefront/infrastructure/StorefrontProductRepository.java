package vn.unicom.thi360.commerce.storefront.infrastructure;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.commerce.order.domain.ProductType;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontRow;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSort;

/**
 * Reads and writes {@code commerce.storefront_product}. Plain SQL: the filters combine arrays, {@code LIKE} and JSON, and
 * the projection must be one atomic statement that never moves {@code version_no} backwards.
 */
@Repository
public class StorefrontProductRepository {

    private static final String UPSERT = """
            INSERT INTO commerce.storefront_product (id, exam_id, code, code_normalized, version_no, sale_state, name,
                search_text, exam_item_id, kind_code, scope, scope_item_id, subject_item_ids, part_codes, part_names,
                question_count, question_count_max, duration_minutes, duration_minutes_max, max_points, max_points_max,
                build_mode, list_price_vnd, attempts_granted, cover_asset_id, card, detail, projection_hash,
                published_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
            ON CONFLICT (exam_id) DO UPDATE SET
                code = EXCLUDED.code, code_normalized = EXCLUDED.code_normalized, version_no = EXCLUDED.version_no,
                sale_state = EXCLUDED.sale_state, name = EXCLUDED.name, search_text = EXCLUDED.search_text,
                exam_item_id = EXCLUDED.exam_item_id, kind_code = EXCLUDED.kind_code, scope = EXCLUDED.scope,
                scope_item_id = EXCLUDED.scope_item_id, subject_item_ids = EXCLUDED.subject_item_ids,
                part_codes = EXCLUDED.part_codes, part_names = EXCLUDED.part_names,
                question_count = EXCLUDED.question_count, question_count_max = EXCLUDED.question_count_max,
                duration_minutes = EXCLUDED.duration_minutes, duration_minutes_max = EXCLUDED.duration_minutes_max,
                max_points = EXCLUDED.max_points, max_points_max = EXCLUDED.max_points_max,
                build_mode = EXCLUDED.build_mode, list_price_vnd = EXCLUDED.list_price_vnd,
                attempts_granted = EXCLUDED.attempts_granted, cover_asset_id = EXCLUDED.cover_asset_id,
                card = EXCLUDED.card, detail = EXCLUDED.detail, projection_hash = EXCLUDED.projection_hash,
                published_at = EXCLUDED.published_at, updated_at = EXCLUDED.updated_at
            WHERE (commerce.storefront_product.version_no < EXCLUDED.version_no
                   OR (commerce.storefront_product.version_no = EXCLUDED.version_no
                       AND NOT (commerce.storefront_product.sale_state = 'DISCONTINUED'
                                AND EXCLUDED.sale_state = 'ON_SALE')))
              AND commerce.storefront_product.projection_hash <> EXCLUDED.projection_hash
            """;

    private static final String UPSERT_BUNDLE = """
            INSERT INTO commerce.storefront_product (id, exam_id, bundle_id, product_type, code, code_normalized,
                version_no, sale_state, name, search_text, exam_item_id, kind_code, scope, scope_item_id,
                subject_item_ids, part_codes, part_names, question_count, question_count_max, duration_minutes,
                duration_minutes_max, max_points, max_points_max, build_mode, list_price_vnd, attempts_granted,
                cover_asset_id, card, detail, projection_hash, published_at, updated_at)
            VALUES (?, NULL, ?, 'BUNDLE', ?, ?, ?, ?, ?, ?, ?, ?, 'BUNDLE', NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                NULL, ?::jsonb, ?::jsonb, ?, ?, ?)
            ON CONFLICT (bundle_id) DO UPDATE SET
                code = EXCLUDED.code, code_normalized = EXCLUDED.code_normalized, version_no = EXCLUDED.version_no,
                sale_state = EXCLUDED.sale_state, name = EXCLUDED.name, search_text = EXCLUDED.search_text,
                exam_item_id = EXCLUDED.exam_item_id, kind_code = EXCLUDED.kind_code, scope = EXCLUDED.scope,
                scope_item_id = EXCLUDED.scope_item_id, subject_item_ids = EXCLUDED.subject_item_ids,
                part_codes = EXCLUDED.part_codes, part_names = EXCLUDED.part_names,
                question_count = EXCLUDED.question_count, question_count_max = EXCLUDED.question_count_max,
                duration_minutes = EXCLUDED.duration_minutes, duration_minutes_max = EXCLUDED.duration_minutes_max,
                max_points = EXCLUDED.max_points, max_points_max = EXCLUDED.max_points_max,
                build_mode = EXCLUDED.build_mode, list_price_vnd = EXCLUDED.list_price_vnd,
                attempts_granted = EXCLUDED.attempts_granted, cover_asset_id = EXCLUDED.cover_asset_id,
                card = EXCLUDED.card, detail = EXCLUDED.detail, projection_hash = EXCLUDED.projection_hash,
                published_at = EXCLUDED.published_at, updated_at = EXCLUDED.updated_at
            WHERE (commerce.storefront_product.version_no < EXCLUDED.version_no
                   OR (commerce.storefront_product.version_no = EXCLUDED.version_no
                       AND NOT (commerce.storefront_product.sale_state = 'DISCONTINUED'
                                AND EXCLUDED.sale_state <> 'DISCONTINUED')))
              AND commerce.storefront_product.projection_hash <> EXCLUDED.projection_hash
            """;

    private final JdbcTemplate jdbc;

    public StorefrontProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Runs the work behind a JDBC savepoint of the current transaction: a failure undoes what the work wrote and is
     * rethrown, the rest of the transaction stays intact. The JPA dialect offers no savepoints to the transaction
     * template, so the connection of the transaction is used directly. Needs an active transaction.
     */
    public <T> T inSavepoint(Supplier<T> work) {
        return jdbc.execute((ConnectionCallback<T>) connection -> {
            Savepoint savepoint = connection.setSavepoint();
            try {
                T result = work.get();
                connection.releaseSavepoint(savepoint);
                return result;
            } catch (RuntimeException e) {
                connection.rollback(savepoint);
                throw e;
            }
        });
    }

    /**
     * Inserts the row, or updates the row of the same exam when it holds an older version, or the same version and
     * different content. A newer stored version is never overwritten, and a stale same-version ON_SALE write never
     * relists a DISCONTINUED row (a discontinue does not raise the version).
     *
     * @return true when a row was inserted or updated
     */
    public boolean upsert(UUID id, StorefrontRow row, Instant now) {
        return jdbc.update(UPSERT, ps -> {
            int i = 1;
            ps.setObject(i++, id);
            ps.setObject(i++, row.examId());
            ps.setString(i++, row.code());
            ps.setString(i++, row.codeNormalized());
            ps.setInt(i++, row.versionNo());
            ps.setString(i++, row.saleState().name());
            ps.setString(i++, row.name());
            ps.setString(i++, row.searchText());
            ps.setObject(i++, row.examItemId());
            ps.setString(i++, row.kindCode());
            ps.setString(i++, row.scope());
            ps.setObject(i++, row.scopeItemId());
            ps.setArray(i++, array(ps, "uuid", row.subjectItemIds().toArray()));
            ps.setArray(i++, array(ps, "text", row.partCodes().toArray()));
            ps.setArray(i++, array(ps, "text", row.partNames().toArray()));
            i = setInt(ps, i, row.questionCount());
            i = setInt(ps, i, row.questionCountMax());
            i = setInt(ps, i, row.durationMinutes());
            i = setInt(ps, i, row.durationMinutesMax());
            ps.setBigDecimal(i++, row.maxPoints());
            ps.setBigDecimal(i++, row.maxPointsMax());
            ps.setString(i++, row.buildMode());
            ps.setLong(i++, row.listPriceVnd());
            ps.setInt(i++, row.attemptsGranted());
            ps.setObject(i++, row.coverAssetId());
            ps.setString(i++, row.card());
            ps.setString(i++, row.detail());
            ps.setString(i++, row.projectionHash());
            ps.setTimestamp(i++, Timestamp.from(row.publishedAt()));
            ps.setTimestamp(i, Timestamp.from(now));
        }) > 0;
    }

    /**
     * Inserts or updates a bundle storefront row keyed on {@code bundle_id} (TM 16.2). Never moves
     * {@code version_no} backwards and leaves unchanged rows untouched via {@code projection_hash}.
     *
     * @return true when a row was inserted or updated
     */
    public boolean upsertBundle(UUID id, StorefrontRow row, Instant now) {
        return jdbc.update(UPSERT_BUNDLE, ps -> {
            int i = 1;
            ps.setObject(i++, id);
            ps.setObject(i++, row.bundleId());
            ps.setString(i++, row.code());
            ps.setString(i++, row.codeNormalized());
            ps.setInt(i++, row.versionNo());
            ps.setString(i++, row.saleState().name());
            ps.setString(i++, row.name());
            ps.setString(i++, row.searchText());
            ps.setObject(i++, row.examItemId());
            ps.setString(i++, row.kindCode());
            ps.setArray(i++, array(ps, "uuid", row.subjectItemIds().toArray()));
            ps.setArray(i++, array(ps, "text", row.partCodes().toArray()));
            ps.setArray(i++, array(ps, "text", row.partNames().toArray()));
            i = setInt(ps, i, row.questionCount());
            i = setInt(ps, i, row.questionCountMax());
            i = setInt(ps, i, row.durationMinutes());
            i = setInt(ps, i, row.durationMinutesMax());
            ps.setBigDecimal(i++, row.maxPoints());
            ps.setBigDecimal(i++, row.maxPointsMax());
            ps.setString(i++, row.buildMode());
            ps.setLong(i++, row.listPriceVnd());
            ps.setInt(i++, row.attemptsGranted());
            ps.setString(i++, row.card());
            ps.setString(i++, row.detail());
            ps.setString(i++, row.projectionHash());
            ps.setTimestamp(i++, Timestamp.from(row.publishedAt()));
            ps.setTimestamp(i, Timestamp.from(now));
        }) > 0;
    }

    /** Marks a bundle storefront row as {@code DISCONTINUED}, preserving code and name for 410 responses (TM 16.2). */
    public boolean markBundleDiscontinued(UUID bundleId, Instant now) {
        return jdbc.update(
                "UPDATE commerce.storefront_product SET sale_state = 'DISCONTINUED', updated_at = ? "
                        + "WHERE bundle_id = ? AND sale_state <> 'DISCONTINUED'",
                Timestamp.from(now), bundleId) > 0;
    }

    private static int setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
        return index + 1;
    }

    private static Array array(PreparedStatement ps, String type, Object[] values) throws SQLException {
        return ps.getConnection().createArrayOf(type, values);
    }

    /** The stored product with this normalised code, any sale state. */
    public Optional<StoredProduct> findByCode(String codeNormalized) {
        return jdbc.query(STORED_SELECT + " WHERE code_normalized = ?", this::stored, codeNormalized).stream().findFirst();
    }

    /** The stored exam product for {@code examId}, any sale state. */
    public Optional<StoredProduct> findByExamId(UUID examId) {
        return jdbc.query(STORED_SELECT + " WHERE exam_id = ?", this::stored, examId).stream().findFirst();
    }

    /** The stored bundle product for {@code bundleId}, any sale state. */
    public Optional<StoredProduct> findByBundleId(UUID bundleId) {
        return jdbc.query(STORED_SELECT + " WHERE bundle_id = ?", this::stored, bundleId).stream().findFirst();
    }

    /** True when another storefront row (not belonging to {@code excludedBundleId}) already uses {@code codeNormalized}. */
    public boolean existsCodeNormalizedConflict(String codeNormalized, UUID excludedBundleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM commerce.storefront_product WHERE code_normalized = ? "
                        + "AND (?::uuid IS NULL OR bundle_id IS NULL OR bundle_id <> ?::uuid))",
                Boolean.class, codeNormalized, excludedBundleId, excludedBundleId));
    }

    /** Batch lookup of storefront {@link SaleState} for the given bundle IDs. */
    public Map<UUID, SaleState> findSaleStatesByBundleIds(Collection<UUID> bundleIds) {
        if (bundleIds == null || bundleIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SaleState> states = new LinkedHashMap<>(bundleIds.size());
        jdbc.query(
                "SELECT bundle_id, sale_state FROM commerce.storefront_product WHERE bundle_id = ANY(?)",
                ps -> ps.setArray(1, array(ps, "uuid", bundleIds.toArray())),
                rs -> {
                    states.put(rs.getObject(1, UUID.class), SaleState.valueOf(rs.getString(2)));
                });
        return states;
    }

    /** True when {@code examId} currently has an on-sale storefront row. */
    public boolean existsOnSaleByExamId(UUID examId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM commerce.storefront_product WHERE exam_id = ? AND sale_state = 'ON_SALE')",
                Boolean.class, examId));
    }

    /** All on-sale products (exams and bundles) used for batch promotion cap-warning evaluation (CM-2, CM-6). */
    public List<CapWarningProduct> listOnSaleProductsForCapWarnings() {
        return jdbc.query(
                "SELECT exam_id, bundle_id, product_type, name, exam_item_id, list_price_vnd "
                        + "FROM commerce.storefront_product "
                        + "WHERE sale_state = 'ON_SALE' "
                        + "ORDER BY name ASC, id ASC",
                (rs, n) -> new CapWarningProduct(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        ProductType.valueOf(rs.getString(3)),
                        rs.getString(4),
                        rs.getObject(5, UUID.class),
                        rs.getLong(6)));
    }

    /** Batch lookup of code and name for exam IDs present in {@code storefront_product}. */
    public Map<UUID, ExamRef> findExamRefsByExamIds(Collection<UUID> examIds) {
        if (examIds == null || examIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ExamRef> refs = new LinkedHashMap<>(examIds.size());
        jdbc.query(
                "SELECT exam_id, code, name FROM commerce.storefront_product WHERE exam_id = ANY(?)",
                ps -> ps.setArray(1, array(ps, "uuid", examIds.toArray())),
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    refs.put(id, new ExamRef(id, rs.getString(2), rs.getString(3)));
                });
        return refs;
    }

    /** Batch lookup of code and name for bundle IDs present in {@code storefront_product}. */
    public Map<UUID, BundleRef> findBundleRefsByBundleIds(Collection<UUID> bundleIds) {
        if (bundleIds == null || bundleIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BundleRef> refs = new LinkedHashMap<>(bundleIds.size());
        jdbc.query(
                "SELECT bundle_id, code, name FROM commerce.storefront_product WHERE bundle_id = ANY(?)",
                ps -> ps.setArray(1, array(ps, "uuid", bundleIds.toArray())),
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    refs.put(id, new BundleRef(id, rs.getString(2), rs.getString(3)));
                });
        return refs;
    }

    /** True when the asset is the current cover of a storefront row (any sale state): the only images served publicly. */
    public boolean isCurrentCover(UUID assetId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM commerce.storefront_product WHERE cover_asset_id = ?)", Boolean.class, assetId));
    }

    private static final String STORED_SELECT = """
            SELECT id, exam_id, exam_item_id, code, name, sale_state, version_no, list_price_vnd, attempts_granted,
                card::text, detail::text, product_type, bundle_id
            FROM commerce.storefront_product""";

    private StoredProduct stored(java.sql.ResultSet rs, int rowNum) throws SQLException {
        return new StoredProduct(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5), SaleState.valueOf(rs.getString(6)), rs.getInt(7), rs.getLong(8),
                rs.getInt(9), rs.getString(10), rs.getString(11), ProductType.valueOf(rs.getString(12)),
                rs.getObject(13, UUID.class));
    }

    /** The on-sale rows matching the filters, in the requested order. */
    public Page list(ListQuery query) {
        List<Object> args = new ArrayList<>();
        String where = where(query, args);
        long total = jdbc.queryForObject("SELECT count(*) FROM commerce.storefront_product " + where, Long.class,
                args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.size());
        pageArgs.add((long) query.page() * query.size());
        List<ListedProduct> items = jdbc.query(
                "SELECT exam_id, bundle_id, product_type, exam_item_id, list_price_vnd, card::text "
                        + "FROM commerce.storefront_product " + where
                        + " ORDER BY " + order(query.sort()) + " LIMIT ? OFFSET ?",
                (rs, n) -> new ListedProduct(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        ProductType.valueOf(rs.getString(3)), rs.getObject(4, UUID.class), rs.getLong(5),
                        rs.getString(6)),
                pageArgs.toArray());
        return new Page(items, total);
    }

    /** Lightweight projection of all matching on-sale rows for post-promotion price sorting (CM-2, E11). */
    public List<SortableProduct> listSortable(ListQuery query) {
        List<Object> args = new ArrayList<>();
        String where = where(query, args);
        return jdbc.query(
                "SELECT id, exam_id, bundle_id, product_type, exam_item_id, list_price_vnd, published_at "
                        + "FROM commerce.storefront_product " + where,
                (rs, n) -> new SortableProduct(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class),
                        ProductType.valueOf(rs.getString(4)),
                        rs.getObject(5, UUID.class),
                        rs.getLong(6),
                        rs.getTimestamp(7).toInstant()),
                args.toArray());
    }

    /** Fetches serialized {@code card} JSON only for the requested product IDs. */
    public Map<UUID, String> findCardsByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> cards = new LinkedHashMap<>(ids.size());
        jdbc.query(
                "SELECT id, card::text FROM commerce.storefront_product WHERE id = ANY(?)",
                ps -> ps.setArray(1, array(ps, "uuid", ids.toArray())),
                rs -> {
                    cards.put(rs.getObject(1, UUID.class), rs.getString(2));
                });
        return cards;
    }

    private static String where(ListQuery query, List<Object> args) {
        StringBuilder sql = new StringBuilder("WHERE sale_state = 'ON_SALE'");
        if (query.likePattern() != null) {
            sql.append(" AND search_text LIKE ? ESCAPE '\\'");
            args.add(query.likePattern());
        }
        if (query.kindCode() != null) {
            sql.append(" AND kind_code = ?");
            args.add(query.kindCode());
        }
        if (query.scope() != null) {
            sql.append(" AND scope = ?");
            args.add(query.scope());
        }
        if (query.subjectItemId() != null) {
            sql.append(" AND subject_item_ids @> ARRAY[?]::uuid[]");
            args.add(query.subjectItemId());
        }
        if (query.partCode() != null) {
            sql.append(" AND scope = 'PART' AND part_codes @> ARRAY[?]::text[]");
            args.add(query.partCode());
        }
        return sql.toString();
    }

    private static String order(StorefrontSort sort) {
        return switch (sort) {
            case NEWEST -> "published_at DESC, id";
            case PRICE_ASC -> "list_price_vnd ASC, published_at DESC, id";
            case PRICE_DESC -> "list_price_vnd DESC, published_at DESC, id";
        };
    }

    /** Number of on-sale products per kind code. */
    public List<Count> countByKind() {
        return jdbc.query("SELECT kind_code, count(*) FROM commerce.storefront_product WHERE sale_state = 'ON_SALE' "
                + "GROUP BY kind_code", (rs, n) -> new Count(rs.getString(1), rs.getLong(2)));
    }

    /** Number of on-sale products per scope (format). */
    public List<Count> countByScope() {
        return jdbc.query("SELECT scope, count(*) FROM commerce.storefront_product WHERE sale_state = 'ON_SALE' "
                + "GROUP BY scope", (rs, n) -> new Count(rs.getString(1), rs.getLong(2)));
    }

    /** Number of on-sale products per subject item id (a product counts once per subject). */
    public List<SubjectCount> countBySubject() {
        return jdbc.query("SELECT s, count(*) FROM commerce.storefront_product p, unnest(p.subject_item_ids) AS s "
                + "WHERE p.sale_state = 'ON_SALE' GROUP BY s",
                (rs, n) -> new SubjectCount(rs.getObject(1, UUID.class), rs.getLong(2)));
    }

    /**
     * Number of on-sale PART exams per (kind, part code); the name is the smallest name seen for that code. Only a PART
     * exam stands for one part (AD-78): a FULL exam is found through the format filter, never through a part code.
     */
    public List<PartCount> countByPart() {
        return jdbc.query("SELECT p.kind_code, t.code, min(t.name), count(*) FROM commerce.storefront_product p, "
                + "unnest(p.part_codes, p.part_names) AS t(code, name) WHERE p.sale_state = 'ON_SALE' "
                + "AND p.scope = 'PART' "
                + "GROUP BY p.kind_code, t.code ORDER BY p.kind_code, t.code",
                (rs, n) -> new PartCount(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
    }

    /**
     * @param likePattern normalised text pattern of {@code q}; null for no text filter
     * @param scope FULL, PART, SUBJECT or BUNDLE; null for any
     */
    public record ListQuery(String likePattern, String kindCode, String scope, UUID subjectItemId, String partCode,
                            StorefrontSort sort, int page, int size) { }

    public record ListedProduct(UUID examId, UUID bundleId, ProductType productType, UUID examKindItemId,
                                long listPriceVnd, String card) {
        public ListedProduct(UUID examId, UUID examKindItemId, long listPriceVnd, String card) {
            this(examId, null, ProductType.EXAM, examKindItemId, listPriceVnd, card);
        }
    }

    public record SortableProduct(UUID id, UUID examId, UUID bundleId, ProductType productType, UUID examKindItemId,
                                  long listPriceVnd, Instant publishedAt) {
        public SortableProduct(UUID id, UUID examId, UUID examKindItemId, long listPriceVnd, Instant publishedAt) {
            this(id, examId, null, ProductType.EXAM, examKindItemId, listPriceVnd, publishedAt);
        }
    }

    public record Page(List<ListedProduct> items, long total) { }

    public record StoredProduct(UUID id, UUID examId, UUID examKindItemId, String code, String name,
                                SaleState saleState, int versionNo, long listPriceVnd, int attemptsGranted,
                                String card, String detail, ProductType productType, UUID bundleId) {
        public StoredProduct(UUID id, UUID examId, UUID examKindItemId, String code, String name,
                             SaleState saleState, int versionNo, long listPriceVnd, int attemptsGranted,
                             String card, String detail) {
            this(id, examId, examKindItemId, code, name, saleState, versionNo, listPriceVnd, attemptsGranted,
                    card, detail, ProductType.EXAM, null);
        }
    }

    public record CapWarningProduct(UUID examId, UUID bundleId, ProductType productType, String name,
                                    UUID examKindItemId, long listPriceVnd) {
        public UUID targetId() {
            return productType == ProductType.BUNDLE ? bundleId : examId;
        }
    }

    public record ExamRef(UUID examId, String code, String name) { }

    public record BundleRef(UUID bundleId, String code, String name) { }

    public record Count(String key, long count) { }

    public record SubjectCount(UUID subjectItemId, long count) { }

    public record PartCount(String kindCode, String code, String name, long count) { }
}
