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
    void groupCaseInsensitiveUniquenessValidationAndSystemProtection() throws Exception {
        String code = "CUSTOM_GRP_" + suffix();
        cacheGroupCodes.add(code.toLowerCase());

        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "  " + code + "  ",
                                "nameVi", "Nhóm tùy chỉnh",
                                "nameEn", "Custom Group"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.type").value("CUSTOM"))
                .andExpect(jsonPath("$.system").value(false))
                .andExpect(jsonPath("$.kind").value("SIMPLE"))
                .andReturn();

        UUID groupId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText());
        createdGroupIds.add(groupId);

        // Case-insensitive duplicate blocked
        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code.toLowerCase(),
                                "nameVi", "Trùng hoa thường"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"));

        // Invalid regex rejected with 400
        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "INVALID CODE!",
                                "nameVi", "Sai định dạng"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // SYSTEM group rejects PATCH, deactivate, and DELETE with 409 CATALOG_GROUP_SYSTEM
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", EXAM_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nameVi", "Sửa tên",
                                "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_SYSTEM"));

        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", EXAM_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_SYSTEM"));

        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}", EXAM_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_SYSTEM"));

        // Adding item to SYSTEM group is allowed
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

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_GROUP_CREATED".equals(e.action())));
    }

    @Test
    void groupSearchMatchesNameEnCaseInsensitivelyAndBlankNameEnStoresNull() throws Exception {
        String searchGroupCode = "SRCH_GRP_" + suffix();
        cacheGroupCodes.add(searchGroupCode.toLowerCase());
        MvcResult searchRes = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", searchGroupCode,
                                "nameVi", "Nhóm tìm kiếm",
                                "nameEn", "UniqueEnglishPhrase"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode searchNode = objectMapper.readTree(searchRes.getResponse().getContentAsString());
        UUID searchGroupId = UUID.fromString(searchNode.get("id").asText());
        long searchGroupVer = searchNode.get("version").asLong();
        createdGroupIds.add(searchGroupId);

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
                                "version", searchGroupVer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").doesNotExist());
    }

    @Test
    void itemsValueNeverReusedCaseInsensitiveUniquenessInactiveGroupAndOptionsCache() throws Exception {
        String groupCode = "OPT_GRP_" + suffix();
        cacheGroupCodes.add(groupCode.toLowerCase());

        MvcResult groupRes = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", groupCode,
                                "nameVi", "Nhóm thử nghiệm"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode groupNode = objectMapper.readTree(groupRes.getResponse().getContentAsString());
        UUID groupId = UUID.fromString(groupNode.get("id").asText());
        long groupVersion = groupNode.get("version").asLong();
        createdGroupIds.add(groupId);

        // Create item 1 & item 2
        MvcResult i1Res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "ITEM_A",
                                "nameVi", "Giá trị A",
                                "nameEn", "Value A"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(1))
                .andReturn();
        UUID i1Id = UUID.fromString(objectMapper.readTree(i1Res.getResponse().getContentAsString()).get("id").asText());

        MvcResult i2Res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "ITEM_B",
                                "nameVi", "Giá trị B",
                                "nameEn", "Value B"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(2))
                .andReturn();
        JsonNode i2Node = objectMapper.readTree(i2Res.getResponse().getContentAsString());
        UUID i2Id = UUID.fromString(i2Node.get("id").asText());

        // Case-insensitive duplicate item code blocked
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "item_a",
                                "nameVi", "Trùng mã"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"));

        // Non-empty group cannot be deleted
        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_NOT_EMPTY"));

        // Delete item 2, then create item 3 -> value must be 3 (never reuse 2)
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", i2Id)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());

        MvcResult i3Res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "ITEM_C",
                                "nameVi", "Giá trị C",
                                "nameEn", "Value C"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(3))
                .andReturn();
        JsonNode i3Node = objectMapper.readTree(i3Res.getResponse().getContentAsString());
        UUID i3Id = UUID.fromString(i3Node.get("id").asText());
        long i3Version = i3Node.get("version").asLong();

        // System item cannot be deleted
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

        // Populate Redis cache via options
        mockMvc.perform(get("/api/v1/catalog/options").param("group", groupCode).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("ITEM_A"))
                .andExpect(jsonPath("$[1].code").value("ITEM_C"));
        assertThat(redisTemplate.hasKey(CatalogCacheService.optionsKey(groupCode.toLowerCase()))).isTrue();

        // Reorder items -> cache evicted & reflected immediately
        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/items/order", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", List.of(i3Id, i1Id)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ITEM_C"))
                .andExpect(jsonPath("$[1].code").value("ITEM_A"));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", groupCode).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ITEM_C"))
                .andExpect(jsonPath("$[1].code").value("ITEM_A"));

        // Re-read i3 version after reorder
        long i3LatestVersion = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", i3Id).with(asUser(Permissions.CATALOG_VIEW)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();
        assertThat(i3LatestVersion).isGreaterThanOrEqualTo(i3Version);

        // Deactivate item 3 -> hidden in options, visible in GET /api/v1/catalog/items/{id}
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", i3Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng thử",
                                "version", i3LatestVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", groupCode).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("ITEM_A"));

        mockMvc.perform(get("/api/v1/catalog/items/{id}", i3Id).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // Deactivate group -> cannot add item & options returns empty
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "version", groupVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", groupCode).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "BLOCKED",
                                "nameVi", "Không nhận"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_INACTIVE"));

        // Nonexistent group returns empty options
        mockMvc.perform(get("/api/v1/catalog/options").param("group", "DOES_NOT_EXIST").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_ITEM_DEACTIVATED".equals(e.action())));
        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_ITEM_REORDERED".equals(e.action())));
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
}
