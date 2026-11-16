package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.application.QuestionAssetCleanupJob;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Worker dọn tệp mồ côi (plan D/BE-2b): orphan > 24h mất cả dòng lẫn object (sau commit);
 * orphan trẻ hoặc asset chỉ còn ở phiên bản cũ giữ nguyên; một lần lưu đua với quét không
 * để lại ref lơ lửng — insert ref chặn delete nhờ khóa FK của Postgres.
 *
 * <p>Không {@code @Transactional}: object storage chỉ xóa sau khi commit thật, và race test
 * cần hai giao dịch song song. Dữ liệu dọn bằng {@link #removeFixture()}.
 */
class QuestionAssetCleanupIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/questions";
    private final UUID actor = UUID.randomUUID();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper mapper;
    @Autowired private QuestionAssetCleanupJob cleanupJob;
    @MockitoSpyBean private ObjectStorage objectStorage;
    @Autowired private PlatformTransactionManager txManager;

    /** Asset id tạo trong test này — dọn cả ý định xóa object sót lại. */
    private final List<UUID> createdAssets = new CopyOnWriteArrayList<>();

    @Override
    protected org.springframework.test.web.servlet.request.RequestPostProcessor asUser(String... permissions) {
        return asUser(actor, "asset-cleanup-it", permissions);
    }

    @AfterEach
    void removeFixture() {
        String ids = "SELECT id FROM questionbank.question WHERE created_by = ?";
        jdbc.update("DELETE FROM questionbank.question_asset_ref WHERE question_id IN (" + ids + ")", actor);
        if (!createdAssets.isEmpty()) {
            String marks = String.join(", ",
                    java.util.Collections.nCopies(createdAssets.size(), "?"));
            jdbc.update("DELETE FROM questionbank.question_asset_pending_delete WHERE asset_id IN ("
                    + marks + ")", createdAssets.toArray());
        }
        jdbc.update("DELETE FROM questionbank.question_asset WHERE uploaded_by = ?", actor);
        jdbc.update("DELETE FROM questionbank.question_knowledge WHERE question_id IN (" + ids + ")", actor);
        jdbc.update("DELETE FROM questionbank.question_tag WHERE question_id IN (" + ids + ")", actor);
        jdbc.update("DELETE FROM questionbank.question_version WHERE question_id IN (" + ids + ")", actor);
        jdbc.update("DELETE FROM questionbank.question_source_code_claim WHERE question_id IN (" + ids + ")", actor);
        jdbc.update("DELETE FROM questionbank.question WHERE created_by = ? AND parent_id IS NOT NULL", actor);
        jdbc.update("DELETE FROM questionbank.question WHERE created_by = ? AND parent_id IS NULL", actor);
        jdbc.update("DELETE FROM shared.idempotency_key WHERE scope LIKE ?", actor + ":question.%");
    }

    private UUID newAsset(Instant createdAt) {
        UUID id = UUID.randomUUID();
        String key = "questions/" + id;
        jdbc.update(
                "INSERT INTO questionbank.question_asset"
                        + " (id, kind, object_key, file_name, mime, size, sha256, width, height,"
                        + " uploaded_by, created_at) VALUES (?, 'IMAGE', ?, 'a.png', 'image/png',"
                        + " 10, ?, 4, 4, ?, ?)",
                id, key, new byte[32], actor, Timestamp.from(createdAt));
        objectStorage.put(key, new byte[] {1, 2, 3}, "image/png");
        createdAssets.add(id);
        return id;
    }

    private boolean assetExists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT count(*) > 0 FROM questionbank.question_asset WHERE id = ?",
                Boolean.class, id));
    }

    private String objectKeyOf(UUID id) {
        return jdbc.queryForObject(
                "SELECT object_key FROM questionbank.question_asset WHERE id = ?", String.class, id);
    }

    @Test
    void orphanDeleted() throws Exception {
        UUID old = newAsset(Instant.now().minus(25, ChronoUnit.HOURS));
        String key = objectKeyOf(old);
        assertThat(cleanupJob.run()).isEqualTo(1);
        assertThat(assetExists(old)).isFalse();
        // afterCommit đã chạy vì run() commit thật.
        assertThat(objectStorage.exists(key)).isFalse();
    }

    @Test
    void youngOrphanKept() throws Exception {
        UUID young = newAsset(Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(cleanupJob.run()).isZero();
        assertThat(assetExists(young)).isTrue();
        assertThat(objectStorage.exists(objectKeyOf(young))).isTrue();
    }

    @Test
    void oldVersionAssetKept() throws Exception {
        // Asset chỉ còn ref của phiên bản cũ (đã READY): worker không đụng tới.
        UUID asset = newAsset(Instant.now().minus(30, ChronoUnit.HOURS));
        var created = mockMvc.perform(post(BASE).with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SINGLE_CHOICE\",\"subjectCode\":\""
                                + QuestionTestSupport.SUBJECT_TOAN + "\",\"gradeCode\":\""
                                + QuestionTestSupport.GRADE_G10 + "\",\"difficultyCode\":\""
                                + QuestionTestSupport.LEVEL_NB
                                + "\",\"content\":\"<p><img data-asset-id='" + asset
                                + "'/></p>\",\"body\":"
                                + QuestionTestSupport.singleChoiceBody() + "}"))
                .andExpect(status().isCreated()).andReturn();
        UUID questionId = UUID.fromString(
                mapper.readTree(created.getResponse().getContentAsString()).at("/summary/id").asText());
        long revision = jdbc.queryForObject(
                "SELECT revision FROM questionbank.question WHERE id = ?", Long.class, questionId);
        mockMvc.perform(post(BASE + "/{id}/ready", questionId)
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
        assertThat(cleanupJob.run()).isZero();
        assertThat(assetExists(asset)).isTrue();
    }

    @Test
    void failedObjectDeleteRetriedNextSweep() {
        // R1-3/R1-8: lần xóa object đầu lỗi → ý định còn, object còn; quét sau thử lại và
        // dọn sạch (dòng asset đã mất từ lần quét đầu).
        UUID asset = newAsset(Instant.now().minus(30, ChronoUnit.HOURS));
        String key = objectKeyOf(asset);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            if (failOnce.getAndSet(false)) {
                throw new IllegalStateException("simulated storage outage");
            }
            invocation.callRealMethod();
            return null;
        }).when(objectStorage).delete(key);

        assertThat(cleanupJob.run()).isEqualTo(1);
        assertThat(assetExists(asset)).isFalse();
        assertThat(objectStorage.exists(key)).isTrue(); // object lỡ sót còn nguyên
        assertThat(pendingCount(key)).isEqualTo(1);

        assertThat(cleanupJob.run()).isZero(); // không còn orphan mới, chỉ thử lại ý định
        assertThat(objectStorage.exists(key)).isFalse();
        assertThat(pendingCount(key)).isZero();
    }

    @Test
    void rolledBackCleanupKeepsRowAndObject() {
        // Transaction dọn bị rollback: dòng asset và object đều nguyên, không ghi ý định.
        UUID asset = newAsset(Instant.now().minus(30, ChronoUnit.HOURS));
        String key = objectKeyOf(asset);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            assertThat(cleanupJob.run()).isEqualTo(1);
            status.setRollbackOnly();
            return null;
        });
        assertThat(assetExists(asset)).isTrue();
        assertThat(objectStorage.exists(key)).isTrue();
        assertThat(pendingCount(key)).isZero();
    }

    private int pendingCount(String objectKey) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM questionbank.question_asset_pending_delete WHERE object_key = ?",
                Integer.class, objectKey);
    }


    @Test
    void raceWithSave() throws Exception {
        // TX1 giữ một insert ref chưa commit (khóa FOR KEY SHARE trên dòng asset); quét
        // phải đợi rồi bỏ qua asset thay vì xóa nó → không ref lơ lửng.
        UUID asset = newAsset(Instant.now().minus(30, ChronoUnit.HOURS));
        UUID questionId = newAssetOwnerQuestion();
        CountDownLatch refInserted = new CountDownLatch(1);
        CountDownLatch commitNow = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = pool.submit(() -> {
                try (var conn = dataSource.getConnection();
                        var ps = conn.prepareStatement(
                                "INSERT INTO questionbank.question_asset_ref"
                                        + " (asset_id, question_id, version_no) VALUES (?, ?, 1)")) {
                    conn.setAutoCommit(false);
                    ps.setObject(1, asset);
                    ps.setObject(2, questionId);
                    ps.executeUpdate();
                    refInserted.countDown();
                    if (!commitNow.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("save was not released");
                    }
                    conn.commit();
                }
                return null;
            });
            assertThat(refInserted.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Integer> sweep = pool.submit(() -> cleanupJob.run());
            sweep.get(30, TimeUnit.SECONDS);
            assertThat(assetExists(asset)).isTrue();
            commitNow.countDown();
            writer.get(30, TimeUnit.SECONDS);
            // Asset còn sống, ref đã ghi → không lơ lửng.
            assertThat(assetExists(asset)).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM questionbank.question_asset_ref WHERE asset_id = ?",
                    Integer.class, asset)).isEqualTo(1);
        } finally {
            commitNow.countDown();
            pool.shutdownNow();
        }
    }

    /** Một câu tối thiểu làm chủ ref (FK question_id). */
    private UUID newAssetOwnerQuestion() throws Exception {
        var created = mockMvc.perform(post(BASE).with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(created.getResponse().getContentAsString())
                .at("/summary/id").asText());
    }
}
