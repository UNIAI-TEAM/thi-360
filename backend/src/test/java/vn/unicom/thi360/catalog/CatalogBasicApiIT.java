package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.application.CatalogCacheService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;

class CatalogBasicApiIT extends AbstractIntegrationTest {

    private static final UUID EXAM_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000001");
    private static final UUID KNOWLEDGE_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000009");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private final List<UUID> createdGroupIds = new ArrayList<>();
    private final List<UUID> createdItemIds = new ArrayList<>();
    private final Set<String> cacheGroupCodes = new HashSet<>();

    @BeforeEach
    void resetAudit() {
        clearInvocations(auditRecorder);
    }

    @AfterEach
    void cleanup() {
        for (UUID itemId : createdItemIds) {
            jdbcTemplate.update("DELETE FROM catalog.catalog_item_ref WHERE item_id = ? OR ref_item_id = ?", itemId, itemId);
            jdbcTemplate.update("DELETE FROM catalog.catalog_item WHERE id = ?", itemId);
        }
        createdItemIds.clear();
        for (UUID groupId : createdGroupIds) {
            jdbcTemplate.update("DELETE FROM catalog.catalog_item WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM catalog.catalog_group_ref WHERE group_id = ? OR ref_group_id = ?", groupId, groupId);
            jdbcTemplate.update("DELETE FROM catalog.catalog_group WHERE id = ?", groupId);
        }
        createdGroupIds.clear();
        for (String code : cacheGroupCodes) {
            redisTemplate.delete(CatalogCacheService.optionsKey(code));
            redisTemplate.delete(CatalogCacheService.treeKey(code));
        }
        cacheGroupCodes.clear();
    }

    @Test
    void migrationSeedsNineSystemGroupsWithI18nNamesAndKnowledgeDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalog/groups")
                        .param("type", "SYSTEM")
                        .param("size", "50")
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(9)))
                .andExpect(jsonPath("$.items[?(@.code=='EXAM')].nameVi").value("Kỳ thi"))
                .andExpect(jsonPath("$.items[?(@.code=='EXAM')].nameEn").value("Exam"))
                .andExpect(jsonPath("$.items[?(@.code=='QUESTION_TAG')].nameEn").value("Question tag"))
                .andExpect(jsonPath("$.items[?(@.code=='KNOWLEDGE')].kind").value("TREE"))
                .andExpect(jsonPath("$.items[?(@.code=='KNOWLEDGE')].levels[0]").value("Chương"))
                .andExpect(jsonPath("$.items[?(@.code=='KNOWLEDGE')].levels[1]").value("Bài"))
                .andExpect(jsonPath("$.items[?(@.code=='KNOWLEDGE')].levels[2]").value("Kiến thức"));

        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/ref-groups", KNOWLEDGE_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("SUBJECT"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].code").value("GRADE"))
                .andExpect(jsonPath("$[1].isDefault").value(true));
    }

    @Test
    void everyAdminEndpointRejectsMissingPermissionWith403() throws Exception {
        UUID anyId = EXAM_GROUP_ID;
        UUID otherId = KNOWLEDGE_GROUP_ID;

        // Read endpoints require CATALOG.VIEW
        mockMvc.perform(get("/api/v1/admin/catalog/groups").with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}", anyId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/items", anyId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", anyId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/tree", otherId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/ref-groups", otherId).with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());

        // Write endpoints require CATALOG.MODIFY (CATALOG.VIEW alone -> 403)
        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "G_403", "nameVi", "Test"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", anyId)
                        .with(asUser(Permissions.CATALOG_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nameVi", "X", "version", 0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}", anyId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", anyId)
                        .with(asUser(Permissions.CATALOG_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "I_403", "nameVi", "Test"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", anyId)
                        .with(asUser(Permissions.CATALOG_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nameVi", "X", "version", 0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/items/order", anyId)
                        .with(asUser(Permissions.CATALOG_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", List.of(anyId)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", anyId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", anyId)
                        .with(asUser(Permissions.CATALOG_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", otherId))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/ref-groups/{refId}", otherId, anyId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}/ref-groups/{refId}", otherId, anyId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}", anyId, otherId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}/refs/{refId}", anyId, otherId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isForbidden());

        // Student cannot access admin catalog endpoints
        mockMvc.perform(get("/api/v1/admin/catalog/groups").with(asStudent()))
                .andExpect(status().isForbidden());

        // Public authenticated endpoints reject unauthenticated with 401, allow student
        mockMvc.perform(get("/api/v1/catalog/options").param("group", "EXAM"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/catalog/options").param("group", "EXAM").with(asStudent()))
                .andExpect(status().isOk());
    }

    @Test
    void groupCodeDuplicateCaseInsensitive409() throws Exception {
        String code = "CUSTOM_GRP_" + suffix();
        cacheGroupCodes.add(code.toLowerCase());

        UUID groupId = createCustomGroup(code, "Nhóm tùy chỉnh", "Custom Group");
        assertThat(groupId).isNotNull();

        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code.toLowerCase(),
                                "nameVi", "Trùng hoa thường"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"));

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_GROUP_CREATED".equals(e.action())));
    }

    @Test
    void itemCodeDuplicateCaseInsensitive409() throws Exception {
        String groupCode = "ITEM_DUP_" + suffix();
        cacheGroupCodes.add(groupCode.toLowerCase());
        UUID groupId = createCustomGroup(groupCode, "Nhóm trùng mã", null);

        createItem(groupId, "ITEM_A", "Giá trị A", "Value A");

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "item_a",
                                "nameVi", "Trùng mã"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"));
    }

    @Test
    void valueNotReusedAfterDelete() throws Exception {
        String groupCode = "VAL_GRP_" + suffix();
        cacheGroupCodes.add(groupCode.toLowerCase());
        UUID groupId = createCustomGroup(groupCode, "Nhóm giá trị", null);

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "ITEM_A", "nameVi", "Giá trị A"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(1));

        UUID i2Id = UUID.fromString(createItem(groupId, "ITEM_B", "Giá trị B", null).get("id").asText());

        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", i2Id)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "ITEM_C", "nameVi", "Giá trị C"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(3));
    }

    @Test
    void inactiveGroupRejectsItem() throws Exception {
        String groupCode = "INACT_GRP_" + suffix();
        cacheGroupCodes.add(groupCode.toLowerCase());
        UUID groupId = createCustomGroup(groupCode, "Nhóm sẽ ngừng", null);

        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", groupCode).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "BLOCKED", "nameVi", "Không nhận"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_INACTIVE"));
    }

    @Test
    void optionsHidesInactiveAndReflectsEdits() throws Exception {
        String groupCode = "OPT_GRP_" + suffix();
        cacheGroupCodes.add(groupCode.toLowerCase());
        UUID groupId = createCustomGroup(groupCode, "Nhóm options", null);

        UUID i1Id = UUID.fromString(createItem(groupId, "ITEM_A", "Giá trị A", "Value A").get("id").asText());
        JsonNode i2Node = createItem(groupId, "ITEM_C", "Giá trị C", "Value C");
        UUID i2Id = UUID.fromString(i2Node.get("id").asText());

        mockMvc.perform(get("/api/v1/catalog/options").param("group", groupCode).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("ITEM_A"))
                .andExpect(jsonPath("$[1].code").value("ITEM_C"));
        assertThat(redisTemplate.hasKey(CatalogCacheService.optionsKey(groupCode.toLowerCase()))).isTrue();

        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/items/order", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", List.of(i2Id, i1Id)))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/catalog/options").param("group", groupCode).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ITEM_C"))
                .andExpect(jsonPath("$[1].code").value("ITEM_A"));

        long i2Ver = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", i2Id).with(asUser(Permissions.CATALOG_VIEW)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();

        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", i2Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng thử",
                                "version", i2Ver))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/catalog/options").param("group", groupCode).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("ITEM_A"));

        mockMvc.perform(get("/api/v1/catalog/items/{id}", i2Id).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", "DOES_NOT_EXIST").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_ITEM_DEACTIVATED".equals(e.action())));
        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_ITEM_REORDERED".equals(e.action())));
    }

    @Test
    void systemGroupPatchDeleteDeactivate409() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", EXAM_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nameVi", "Sửa tên", "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_SYSTEM"));

        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", EXAM_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE", "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_SYSTEM"));

        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}", EXAM_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_SYSTEM"));
    }

    @Test
    void systemGroupAcceptsItem() throws Exception {
        MvcResult sysItemRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", EXAM_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "TEST_EXAM_" + suffix(),
                                "nameVi", "Kỳ thi thử"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.system").value(false))
                .andReturn();
        UUID sysItemId = UUID.fromString(objectMapper.readTree(sysItemRes.getResponse().getContentAsString()).get("id").asText());
        createdItemIds.add(sysItemId);
        cacheGroupCodes.add("exam");
    }

    @Test
    void groupValidationNotEmptyAndSystemItemProtection() throws Exception {
        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "INVALID CODE!",
                                "nameVi", "Sai định dạng"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String groupCode = "EMPTY_CHK_" + suffix();
        cacheGroupCodes.add(groupCode.toLowerCase());
        UUID groupId = createCustomGroup(groupCode, "Nhóm kiểm tra xóa", null);
        createItem(groupId, "ITEM_1", "Giá trị 1", null);

        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_NOT_EMPTY"));

        UUID sysItemId = Ids.newId();
        createdItemIds.add(sysItemId);
        jdbcTemplate.update(
                """
                INSERT INTO catalog.catalog_item
                (id, group_id, code, code_normalized, value, name_vi, display_order, status, is_system, parent_id, depth, path)
                VALUES (?, ?, 'SYS_VAL', 'sys_val', 99, 'Giá trị hệ thống', 99, 1, true, NULL, 1, ?)
                """,
                sysItemId,
                groupId,
                "/" + sysItemId + "/");
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", sysItemId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_SYSTEM"));
        jdbcTemplate.update("DELETE FROM catalog.catalog_item WHERE id = ?", sysItemId);
    }

    @Test
    void groupSearchMatchesNameEnCaseInsensitivelyAndBlankNameEnStoresNull() throws Exception {
        String searchGroupCode = "SRCH_GRP_" + suffix();
        cacheGroupCodes.add(searchGroupCode.toLowerCase());
        UUID searchGroupId = createCustomGroup(searchGroupCode, "Nhóm tìm kiếm", "UniqueEnglishPhrase");

        mockMvc.perform(get("/api/v1/admin/catalog/groups")
                        .param("q", "englishphrase")
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.code=='" + searchGroupCode + "')]", hasSize(1)));

        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", searchGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nameEn", "   ",
                                "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").doesNotExist());
    }
    @Test
    void concurrentItemCreationGeneratesUniqueValuesUnderGroupRowLock() throws Exception {
        String groupCode = "LOCK_GRP_" + suffix();
        cacheGroupCodes.add(groupCode.toLowerCase());

        MvcResult groupRes = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", groupCode,
                                "nameVi", "Nhóm kiểm tra khóa"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID groupId = UUID.fromString(objectMapper.readTree(groupRes.getResponse().getContentAsString()).get("id").asText());
        createdGroupIds.add(groupId);

        int threads = 6;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int idx = i;
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of(
                                            "code", "CONC_" + idx,
                                            "nameVi", "Mục " + idx))))
                            .andExpect(status().isCreated())
                            .andReturn();
                    return objectMapper.readTree(res.getResponse().getContentAsString()).get("value").asInt();
                });
            }
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> t : tasks) {
                futures.add(pool.submit(t));
            }
            ready.await();
            start.countDown();

            Set<Integer> values = new HashSet<>();
            for (Future<Integer> f : futures) {
                values.add(f.get());
            }
            assertThat(values).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
        } finally {
            pool.shutdownNow();
        }
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID createCustomGroup(String code, String nameVi, String nameEn) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("nameVi", nameVi);
        if (nameEn != null) {
            body.put("nameEn", nameEn);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdGroupIds.add(id);
        return id;
    }

    private JsonNode createItem(UUID groupId, String code, String nameVi, String nameEn) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("nameVi", nameVi);
        if (nameEn != null) {
            body.put("nameEn", nameEn);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }
}
