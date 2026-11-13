package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.application.CatalogCacheService;
import vn.unicom.thi360.catalog.spi.CatalogUsage;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;

@Import(FakeCatalogUsageProviderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogCombinationApiIT extends AbstractIntegrationTest {

    private static final UUID EXAM_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000001");
    private static final UUID SUBJECT_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID COMBINATION_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000007");

    private static final UUID SAMPLE_HSA_ID = UUID.fromString("00000000-0000-7000-8200-000000000101");
    private static final UUID SAMPLE_QDA_ID = UUID.fromString("00000000-0000-7000-8200-000000000102");
    private static final UUID SAMPLE_TOAN_ID = UUID.fromString("00000000-0000-7000-8200-000000000201");
    private static final UUID SAMPLE_VAN_ID = UUID.fromString("00000000-0000-7000-8200-000000000202");
    private static final UUID SAMPLE_ANH_ID = UUID.fromString("00000000-0000-7000-8200-000000000203");
    private static final UUID SAMPLE_LY_ID = UUID.fromString("00000000-0000-7000-8200-000000000204");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private FakeCatalogUsageProviderConfig.FakeCatalogUsageRegistry fakeUsageRegistry;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private final List<UUID> createdItemIds = new ArrayList<>();

    @BeforeEach
    void resetState() {
        fakeUsageRegistry.clear();
        clearInvocations(auditRecorder);
    }

    @AfterEach
    void cleanup() {
        fakeUsageRegistry.clear();
        for (int i = createdItemIds.size() - 1; i >= 0; i--) {
            UUID itemId = createdItemIds.get(i);
            jdbcTemplate.update(
                    "DELETE FROM catalog.combination_subject WHERE combination_version_id IN (SELECT id FROM catalog.combination_version WHERE item_id = ?)",
                    itemId);
            jdbcTemplate.update("DELETE FROM catalog.combination_version WHERE item_id = ?", itemId);
            jdbcTemplate.update("DELETE FROM catalog.catalog_item WHERE id = ?", itemId);
        }
        createdItemIds.clear();
        redisTemplate.delete(CatalogCacheService.optionsKey("subject_combination"));
        redisTemplate.delete(CatalogCacheService.optionsKey("exam"));
        redisTemplate.delete(CatalogCacheService.optionsKey("subject"));
    }

    @Test
    void createCombination_appearsInOptionsAndGetItem() throws Exception {
        String code = "HSA_COMBO_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp thử nghiệm",
                                "nameEn", "Test Combination",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_LY_ID, SAMPLE_ANH_ID),
                                "sourceNote", "Quy chế 2026"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.combination.versionNo").value(1))
                .andExpect(jsonPath("$.combination.isCurrent").value(true))
                .andExpect(jsonPath("$.combination.examItemId").value(SAMPLE_HSA_ID.toString()))
                .andExpect(jsonPath("$.combination.examCode").value("HSA"))
                .andExpect(jsonPath("$.combination.sourceNote").value("Quy chế 2026"))
                .andExpect(jsonPath("$.combination.subjects", hasSize(3)))
                .andExpect(jsonPath("$.combination.subjects[0].code").value("TOAN"))
                .andExpect(jsonPath("$.combination.subjects[1].code").value("LY"))
                .andExpect(jsonPath("$.combination.subjects[2].code").value("ANH"))
                .andReturn();

        UUID itemId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText());
        createdItemIds.add(itemId);

        mockMvc.perform(get("/api/v1/catalog/options")
                        .param("group", "SUBJECT_COMBINATION")
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem(code)));

        mockMvc.perform(get("/api/v1/catalog/items/{id}", itemId).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.versionNo").value(1))
                .andExpect(jsonPath("$.combination.subjects[1].code").value("LY"));
    }

    @Test
    void createCombination_duplicateCodeBlocked_andValidationsEnforced() throws Exception {
        String code = "QDA_DUP_" + suffix();
        MvcResult first = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp 1",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID)))))
                .andExpect(status().isCreated())
                .andReturn();
        createdItemIds.add(UUID.fromString(objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText()));

        // Case-insensitive duplicate code -> 409 CATALOG_CODE_TAKEN
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code.toLowerCase(),
                                "nameVi", "Trùng mã",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"));

        // Duplicate subjects -> 400
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "DUP_SUB_" + suffix(),
                                "nameVi", "Trùng môn",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_TOAN_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("DUPLICATE"));

        // Empty subjects -> 400
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "NO_SUB_" + suffix(),
                                "nameVi", "Thiếu môn",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of()))))
                .andExpect(status().isBadRequest());

        // Passing subject as examItemId -> 400
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "BAD_EX_" + suffix(),
                                "nameVi", "Sai kỳ thi",
                                "examItemId", SAMPLE_TOAN_ID,
                                "subjectItemIds", List.of(SAMPLE_VAN_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID_EXAM"));

        // Inactive exam & inactive subject rejected
        UUID inactiveExamId = createSimpleItem(EXAM_GROUP_ID, "EX_INACT_" + suffix(), "Kỳ thi ngừng", "INACTIVE");
        UUID inactiveSubjectId = createSimpleItem(SUBJECT_GROUP_ID, "SUB_INACT_" + suffix(), "Môn ngừng", "INACTIVE");

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "INACT_EX_" + suffix(),
                                "nameVi", "Kỳ thi ngừng",
                                "examItemId", inactiveExamId,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INACTIVE_EXAM"));

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "INACT_SUB_" + suffix(),
                                "nameVi", "Môn ngừng",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(inactiveSubjectId)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INACTIVE_SUBJECT"));
    }

    @Test
    void deactivatedCombinationAbsentFromOptions_butGetItemStillReturnsIt() throws Exception {
        String code = "COMBO_STOP_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp ngừng",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_VAN_ID)))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID itemId = UUID.fromString(created.get("id").asText());
        long version = created.get("version").asLong();
        createdItemIds.add(itemId);

        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng tổ hợp thử nghiệm",
                                "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/catalog/options")
                        .param("group", "SUBJECT_COMBINATION")
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='" + code + "')]").isEmpty());

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.combination.versionNo").value(1));

        mockMvc.perform(get("/api/v1/catalog/items/{id}", itemId).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void editCombination_updatesInPlaceWhenUnreferenced_andCreatesV2WhenReferenced() throws Exception {
        String code = "COMBO_VER_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp phiên bản",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_VAN_ID, SAMPLE_ANH_ID),
                                "sourceNote", "Bản 1"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID itemId = UUID.fromString(created.get("id").asText());
        UUID v1Id = UUID.fromString(created.get("combination").get("id").asText());
        long ver = created.get("version").asLong();
        createdItemIds.add(itemId);

        // Unreferenced -> edit subjects modifies v1 in place
        MvcResult inPlaceRes = mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_LY_ID, SAMPLE_ANH_ID),
                                "sourceNote", "Bản 1 sửa tại chỗ",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.id").value(v1Id.toString()))
                .andExpect(jsonPath("$.combination.versionNo").value(1))
                .andExpect(jsonPath("$.combination.subjects[1].code").value("LY"))
                .andReturn();
        ver = objectMapper.readTree(inPlaceRes.getResponse().getContentAsString()).get("version").asLong();

        // Mark referenced by fake provider -> reorder subjects creates v2; v1 retains old data
        fakeUsageRegistry.setUsages(
                itemId, List.of(new CatalogUsage("Bài thi giả lập", "EXAM_DEFINITION", 1, false, List.of("Đề QĐ-01"))));

        // Editing sourceNote alone without changing subjects/exam -> still in place (versionNo remains 1)
        MvcResult noteOnlyRes = mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceNote", "Chỉ sửa ghi chú",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.versionNo").value(1))
                .andReturn();
        ver = objectMapper.readTree(noteOnlyRes.getResponse().getContentAsString()).get("version").asLong();

        // Changing subject order creates v2
        MvcResult v2Res = mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectItemIds", List.of(SAMPLE_ANH_ID, SAMPLE_TOAN_ID, SAMPLE_LY_ID),
                                "sourceNote", "Bản 2",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.versionNo").value(2))
                .andExpect(jsonPath("$.combination.isCurrent").value(true))
                .andExpect(jsonPath("$.combination.subjects[0].code").value("ANH"))
                .andExpect(jsonPath("$.combination.subjects[1].code").value("TOAN"))
                .andExpect(jsonPath("$.combination.subjects[2].code").value("LY"))
                .andReturn();
        UUID v2Id = UUID.fromString(
                objectMapper.readTree(v2Res.getResponse().getContentAsString()).get("combination").get("id").asText());
        assertThat(v2Id).isNotEqualTo(v1Id);

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/versions", itemId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNo").value(2))
                .andExpect(jsonPath("$[0].isCurrent").value(true))
                .andExpect(jsonPath("$[1].versionNo").value(1))
                .andExpect(jsonPath("$[1].isCurrent").value(false));

        // Historical v1 endpoint still returns old subject list in original order
        mockMvc.perform(get("/api/v1/admin/catalog/combination-versions/{versionId}", v1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.isCurrent").value(false))
                .andExpect(jsonPath("$.subjects[0].code").value("TOAN"))
                .andExpect(jsonPath("$.subjects[1].code").value("LY"))
                .andExpect(jsonPath("$.subjects[2].code").value("ANH"));

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "COMBINATION_VERSION_CREATED".equals(e.action())));
    }

    @Test
    void concurrentCombinationEdits_keepSingleCurrentVersion() throws Exception {
        String code = "COMBO_CONC_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp đồng thời",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_VAN_ID)))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID itemId = UUID.fromString(created.get("id").asText());
        long initialVersion = created.get("version").asLong();
        createdItemIds.add(itemId);

        fakeUsageRegistry.setUsages(
                itemId, List.of(new CatalogUsage("Bài thi giả lập", "EXAM_DEFINITION", 1, false, List.of("Đề HSA-01"))));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> c1 = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                                .with(asUser(Permissions.CATALOG_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_LY_ID),
                                        "version", initialVersion))))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> c2 = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                                .with(asUser(Permissions.CATALOG_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_ANH_ID),
                                        "version", initialVersion))))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> f1 = pool.submit(c1);
            Future<Integer> f2 = pool.submit(c2);
            ready.await();
            start.countDown();
            List<Integer> statuses = List.of(f1.get(), f2.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 412);
        } finally {
            pool.shutdownNow();
        }

        Integer currentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM catalog.combination_version WHERE item_id = ? AND is_current = true",
                Integer.class,
                itemId);
        assertThat(currentCount).isEqualTo(1);
    }

    @Test
    void versionEndpointsRejectMissingPermissionWith403() throws Exception {
        UUID anyId = Ids.newId();
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/versions", anyId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/combination-versions/{versionId}", anyId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/versions", anyId)
                        .with(asStudent()))
                .andExpect(status().isForbidden());
    }

    private UUID createSimpleItem(UUID groupId, String code, String nameVi, String status) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", nameVi,
                                "status", status))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdItemIds.add(id);
        return id;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
