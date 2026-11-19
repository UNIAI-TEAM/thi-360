package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * R1-8: the cover of a duplicated exam is copied in the object store before the command's transaction opens, a missing
 * cover object fails the command, and an object that did not become a cover is removed. Runs on committed rows (the
 * transaction of the command must be the real one), each test removing what it created.
 */
class ExamDuplicateCoverIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean ObjectStorage storage;
    private final List<UUID> exams = new ArrayList<>();
    /** The idempotency scope includes the user: one editor for every call of a test, or a replay would not be one. */
    private static final UUID EDITOR = UUID.fromString("00000000-0000-7000-8800-0000000000e2");
    private final List<UUID> assets = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();

    @BeforeEach
    void resetSpy() {
        Mockito.reset(storage);
    }

    @AfterEach
    void cleanUp() {
        Mockito.reset(storage);
        for (UUID exam : exams) jdbc.update("UPDATE exam.exam SET cover_asset_id = NULL WHERE id = ?", exam);
        for (UUID exam : exams) {
            for (String key : jdbc.queryForList("SELECT object_key FROM exam.exam_cover_asset WHERE exam_id = ?", String.class, exam)) {
                storage.delete(key);
            }
            jdbc.update("DELETE FROM exam.exam_cover_asset WHERE exam_id = ?", exam);
            jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id = ?", exam);
            jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", exam);
            jdbc.update("DELETE FROM exam.exam_structure WHERE exam_id = ?", exam);
            jdbc.update("DELETE FROM exam.exam WHERE id = ?", exam);
        }
        for (String key : keys) storage.delete(key);
    }

    private UUID newExam(String name) {
        UUID kind = jdbc.queryForObject("SELECT i.id FROM catalog.catalog_item i JOIN catalog.catalog_group g ON g.id = i.group_id "
                + "WHERE g.code = 'EXAM' AND i.code = 'HSA'", UUID.class);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing) "
                + "VALUES (?, ?, ?, ?, ?, 'FULL', 'SHARED')", id, id.toString(), id.toString(), name, kind);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id, parts, structure_hash) VALUES (?, '{\"parts\":[]}'::jsonb, 'dup-it')", id);
        exams.add(id);
        return id;
    }

    private UUID cover(UUID exam, boolean withObject, byte[] bytes) {
        UUID asset = UUID.randomUUID();
        String key = "exam-covers/" + asset;
        if (withObject) {
            storage.put(key, bytes, "image/png");
            keys.add(key);
        }
        jdbc.update("INSERT INTO exam.exam_cover_asset (id, exam_id, object_key, content_type, size_bytes, width, height) "
                + "VALUES (?, ?, ?, 'image/png', ?, 600, 700)", asset, exam, key, bytes.length);
        jdbc.update("UPDATE exam.exam SET cover_asset_id = ? WHERE id = ?", asset, exam);
        assets.add(asset);
        return asset;
    }

    private ResultActions duplicate(UUID source, String key, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/exams/" + source + "/duplicate").with(asUser(EDITOR, "editor", Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"));
    }

    private UUID created(ResultActions result) throws Exception {
        JsonNode copy = mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(copy.path("id").asText());
        exams.add(id);
        return id;
    }

    private long copiesNamed(String name) {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam WHERE name = ?", Long.class, name);
    }

    @Test
    void coverObjectIsCopiedOutsideTheCommandTransaction() throws Exception {
        UUID source = newExam("Dup source " + UUID.randomUUID());
        byte[] bytes = {1, 2, 3, 4, 5, 6};
        cover(source, true, bytes);
        List<Boolean> inTransaction = new CopyOnWriteArrayList<>();
        doAnswer(call -> {
            inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            Thread.sleep(150);
            return call.callRealMethod();
        }).when(storage).get(anyString());
        doAnswer(call -> {
            inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            Thread.sleep(150);
            return call.callRealMethod();
        }).when(storage).put(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
        String name = "Dup copy " + UUID.randomUUID();
        UUID copy = created(duplicate(source, UUID.randomUUID().toString(), name));
        assertThat(inTransaction).as("the object store is called for the source read and the copy write").hasSize(2)
                .as("and never while the command's transaction (and its advisory lock) is open").containsOnly(false);
        // The duplicate is complete and readable: new asset, new object, same bytes, served through the cover route.
        UUID copied = jdbc.queryForObject("SELECT cover_asset_id FROM exam.exam WHERE id = ?", UUID.class, copy);
        assertThat(copied).isNotNull().isNotIn(assets);
        mockMvc.perform(get("/api/v1/admin/exams/" + copy + "/cover/" + copied).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk());
        assertThat(storage.get(jdbc.queryForObject("SELECT object_key FROM exam.exam_cover_asset WHERE id = ?", String.class, copied)))
                .contains(bytes);
        assertThat(copiesNamed(name)).isEqualTo(1);
    }

    @Test
    void missingCoverObjectFailsTheCommand() throws Exception {
        UUID source = newExam("Dup source " + UUID.randomUUID());
        cover(source, false, new byte[] {9, 9});
        String name = "Dup copy " + UUID.randomUUID();
        duplicate(source, UUID.randomUUID().toString(), name).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_LISTING_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("coverAssetId"));
        assertThat(copiesNamed(name)).as("no copy without the cover that was asked for").isZero();
    }

    /** R2-1: a committed replay is resolved from the stored response before any object-store call. */
    @Test
    void committedReplayReturnsTheRecordedResponseWithoutTouchingStorage() throws Exception {
        UUID source = newExam("Dup source " + UUID.randomUUID());
        UUID sourceAsset = cover(source, true, new byte[] {7, 7, 7});
        String key = UUID.randomUUID().toString();
        String name = "Dup copy " + UUID.randomUUID();
        String first = duplicate(source, key, name).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID copy = UUID.fromString(mapper.readTree(first).path("id").asText());
        exams.add(copy);
        List<String> copyObjects = jdbc.queryForList("SELECT object_key FROM exam.exam_cover_asset WHERE exam_id = ?", String.class, copy);
        assertThat(copyObjects).hasSize(1);
        // The source object is gone and the store is down: neither may matter to a replay.
        storage.delete("exam-covers/" + sourceAsset);
        Mockito.reset(storage);
        doThrow(new IllegalStateException("storage down")).when(storage).get(anyString());
        doThrow(new IllegalStateException("storage down")).when(storage).put(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
        String again = duplicate(source, key, name).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(again)).as("the exact stored response").isEqualTo(mapper.readTree(first));
        assertThat(copiesNamed(name)).isEqualTo(1);
        Mockito.verify(storage, never()).get(anyString());
        Mockito.verify(storage, never()).put(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
        Mockito.verify(storage, never()).delete(anyString());
        Mockito.reset(storage);
        assertThat(storage.exists(copyObjects.getFirst())).as("the copy's own object is untouched").isTrue();
    }

    /** R2-1: another body under a recorded key is the contractual 409, decided before any object-store call. */
    @Test
    void recordedKeyWithAnotherBodyConflictsWithoutTouchingStorage() throws Exception {
        UUID source = newExam("Dup source " + UUID.randomUUID());
        cover(source, true, new byte[] {8, 8, 8});
        String key = UUID.randomUUID().toString();
        String name = "Dup copy " + UUID.randomUUID();
        created(duplicate(source, key, name));
        Mockito.reset(storage);
        doThrow(new IllegalStateException("storage down")).when(storage).get(anyString());
        doThrow(new IllegalStateException("storage down")).when(storage).put(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
        String other = "Dup other body " + UUID.randomUUID();
        duplicate(source, key, other).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(copiesNamed(other)).isZero();
        assertThat(copiesNamed(name)).isEqualTo(1);
        Mockito.verify(storage, never()).get(anyString());
        Mockito.verify(storage, never()).put(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
        Mockito.verify(storage, never()).delete(anyString());
    }

    @Test
    void coverChangedWhileStagingFailsAndRemovesTheStagedObject() throws Exception {
        UUID source = newExam("Dup source " + UUID.randomUUID());
        cover(source, true, new byte[] {5, 5, 5});
        UUID other = cover(newExam("Dup other " + UUID.randomUUID()), true, new byte[] {6, 6});
        jdbc.update("UPDATE exam.exam_cover_asset SET exam_id = ? WHERE id = ?", source, other);
        List<String> staged = new CopyOnWriteArrayList<>();
        // Between staging the copy and the command's transaction, the source gets another cover.
        doAnswer(call -> {
            Object result = call.callRealMethod();
            staged.add(call.getArgument(0));
            jdbc.update("UPDATE exam.exam SET cover_asset_id = ? WHERE id = ?", other, source);
            return result;
        }).when(storage).put(anyString(), org.mockito.ArgumentMatchers.any(), anyString());
        String name = "Dup copy " + UUID.randomUUID();
        duplicate(source, UUID.randomUUID().toString(), name).andExpect(status().isPreconditionFailed());
        assertThat(copiesNamed(name)).isZero();
        assertThat(staged).hasSize(1);
        assertThat(storage.exists(staged.getFirst())).as("the staged object did not become a cover and is gone").isFalse();
    }
}
