package vn.unicom.thi360.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.application.CatalogCacheService;
import vn.unicom.thi360.catalog.spi.CatalogUsage;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;

@Import(FakeCatalogUsageProviderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogUsageAndRulesIT extends AbstractIntegrationTest {

    private static final UUID EXAM_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000001");
    private static final UUID SUBJECT_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID COMBINATION_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000007");
    private static final UUID SAMPLE_HSA_ID = UUID.fromString("00000000-0000-7000-8200-000000000101");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private FakeCatalogUsageProviderConfig.FakeCatalogUsageRegistry fakeUsageRegistry;

    private final List<UUID> createdGroupIds = new ArrayList<>();
    private final List<UUID> createdItemIds = new ArrayList<>();
    private final List<String> cacheGroupCodes = new ArrayList<>();

    @BeforeEach
    void resetState() {
        fakeUsageRegistry.clear();
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
            jdbcTemplate.update("DELETE FROM catalog.catalog_item_ref WHERE item_id = ? OR ref_item_id = ?", itemId, itemId);
            jdbcTemplate.update("DELETE FROM catalog.catalog_item WHERE id = ?", itemId);
        }
        createdItemIds.clear();
        for (int i = createdGroupIds.size() - 1; i >= 0; i--) {
            UUID groupId = createdGroupIds.get(i);
            jdbcTemplate.update(
                    "DELETE FROM catalog.catalog_item_ref WHERE item_id IN (SELECT id FROM catalog.catalog_item WHERE group_id = ?)",
                    groupId);
            for (short d = 6; d >= 1; d--) {
                jdbcTemplate.update("DELETE FROM catalog.catalog_item WHERE group_id = ? AND depth = ?", groupId, d);
            }
            jdbcTemplate.update("DELETE FROM catalog.catalog_group_ref WHERE group_id = ? OR ref_group_id = ?", groupId, groupId);
            jdbcTemplate.update("DELETE FROM catalog.catalog_group WHERE id = ?", groupId);
        }
        createdGroupIds.clear();
        for (String code : cacheGroupCodes) {
            redisTemplate.delete(CatalogCacheService.optionsKey(code));
            redisTemplate.delete(CatalogCacheService.treeKey(code));
        }
        cacheGroupCodes.clear();
        redisTemplate.delete(CatalogCacheService.optionsKey("subject_combination"));
        redisTemplate.delete(CatalogCacheService.optionsKey("subject"));
        redisTemplate.delete(CatalogCacheService.optionsKey("exam"));
    }

    @Test
    void deleteSubjectUsedByCombination_returns409WithUsageList_andDeactivateBlockedWhileActive() throws Exception {
        String subjCode = "SUB_USE_" + suffix();
        JsonNode subject = createItem(SUBJECT_GROUP_ID, subjCode, "Môn dùng trong tổ hợp");
        UUID subjectId = UUID.fromString(subject.get("id").asText());
        long subjectVersion = subject.get("version").asLong();

        String comboCode = "COMBO_USE_" + suffix();
        JsonNode combo = createCombination(comboCode, "Tổ hợp thử", SAMPLE_HSA_ID, List.of(subjectId));
        UUID comboId = UUID.fromString(combo.get("id").asText());
        long comboVersion = combo.get("version").asLong();

        // GET /items/{id}/usages and GET /groups/{id}/usages report CombinationUsageProvider
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/usages", subjectId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.blocking").value(true))
                .andExpect(jsonPath("$.usages[0].objectType").value("SUBJECT_COMBINATION"))
                .andExpect(jsonPath("$.usages[0].sampleLabels[0]").value(comboCode + " - Tổ hợp thử"));

        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/usages", SUBJECT_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usages[?(@.objectType=='SUBJECT_COMBINATION')].count").value(hasItem(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));

        // Deleting subject used by combination -> 409 CATALOG_ITEM_IN_USE with fieldErrors
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", subjectId)
                        .param("reason", "Thử xóa môn đang dùng")
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_IN_USE"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("SUBJECT_COMBINATION"));

        // Deactivating subject while combination is ACTIVE -> 409 CATALOG_CHANGE_BLOCKED
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", subjectId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng môn",
                                "version", subjectVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CHANGE_BLOCKED"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("BLOCKING"));

        // Deactivate combination first -> blocking becomes false, so subject can be deactivated (still cannot delete)
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", comboId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng tổ hợp",
                                "version", comboVersion))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", subjectId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng môn sau khi ngừng tổ hợp",
                                "version", subjectVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", subjectId)
                        .param("reason", "Xóa vẫn bị chặn")
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_IN_USE"));
    }

    @Test
    void fakeProviderBlocks_cannotDeactivate_andCodeLockedWhenReferenced() throws Exception {
        String groupCode = "GRP_BLK_" + suffix();
        cacheGroupCodes.add(groupCode.toLowerCase());
        UUID groupId = createCustomGroup(groupCode, "Nhóm kiểm tra chặn", "SIMPLE", null, null);
        JsonNode item = createItem(groupId, "VAL_BLK", "Giá trị bị chặn");
        UUID itemId = UUID.fromString(item.get("id").asText());
        long itemVer = item.get("version").asLong();

        fakeUsageRegistry.setUsages(
                itemId,
                List.of(new CatalogUsage(
                        "Đề thi đang mở bán", "PUBLISHED_EXAM", 2, true, List.of("Đề HSA-2026"))));

        // Deactivate item blocked -> 409 CATALOG_CHANGE_BLOCKED
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Thử ngừng",
                                "version", itemVer))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CHANGE_BLOCKED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("PUBLISHED_EXAM"));

        // Change item code while referenced -> 409 CATALOG_CODE_LOCKED
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "VAL_RENAMED",
                                "version", itemVer))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_LOCKED"));

        // Change group code while any item in group is referenced -> 409 CATALOG_CODE_LOCKED
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", groupCode + "_NEW",
                                "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_LOCKED"));
    }

    @Test
    void treeRefUsageProvider_blocksDelete_allowsDeactivateWithNonBlockingUsage() throws Exception {
        String refGroupCode = "REF_SRC_" + suffix();
        String treeCode = "TREE_REF_" + suffix();
        cacheGroupCodes.add(refGroupCode.toLowerCase());
        cacheGroupCodes.add(treeCode.toLowerCase());

        UUID refGroupId = createCustomGroup(refGroupCode, "Nhóm nguồn", "SIMPLE", null, null);
        JsonNode refItem = createItem(refGroupId, "TAG1", "Nhãn 1");
        UUID refItemId = UUID.fromString(refItem.get("id").asText());
        long refItemVer = refItem.get("version").asLong();

        UUID treeGroupId = createCustomGroup(
                treeCode, "Cây liên kết", "TREE", List.of("Chương", "Bài"), List.of(refGroupId));
        createTreeItem(treeGroupId, null, "CH1", "Chương 1", List.of(refItemId));

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/usages", refItemId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.blocking").value(false))
                .andExpect(jsonPath("$.usages[0].objectType").value("TREE_NODE"));

        // Delete blocked with CATALOG_ITEM_IN_USE
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", refItemId)
                        .param("reason", "Xóa nhãn")
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_IN_USE"));

        // Deactivate succeeds because TreeRefUsageProvider is non-blocking
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", refItemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng nhãn",
                                "version", refItemVer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void moveTreeBranchWithReferencedChild_returns409CatalogItemInUse_andBranchUsagesAggregated() throws Exception {
        String treeCode = "TREE_MOVE_" + suffix();
        cacheGroupCodes.add(treeCode.toLowerCase());
        UUID treeGroupId = createCustomGroup(
                treeCode, "Cây chuyển nhánh", "TREE", List.of("Chương", "Bài", "Kiến thức"), null);

        UUID ch1Id = createTreeItem(treeGroupId, null, "CH1", "Chương 1", null);
        UUID ch2Id = createTreeItem(treeGroupId, null, "CH2", "Chương 2", null);
        UUID les1Id = createTreeItem(treeGroupId, ch1Id, "LES1", "Bài 1", null);
        UUID leaf1Id = createTreeItem(treeGroupId, les1Id, "LEAF1", "Kiến thức 1", null);

        fakeUsageRegistry.setUsages(
                leaf1Id,
                List.of(new CatalogUsage("Ngân hàng câu hỏi", "QUESTION", 3, true, List.of("Q-001"))));

        // Usages on chapter and lesson aggregate leaf's usage across the whole branch
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/usages", ch1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.blocking").value(true))
                .andExpect(jsonPath("$.usages[0].objectType").value("QUESTION"));

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/usages", les1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3));

        // Moving les1 whose child leaf1 is referenced -> 409 CATALOG_ITEM_IN_USE
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", les1Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", ch2Id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_IN_USE"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("QUESTION"));

        // Deactivating ch1 whose descendant leaf1 has blocking usage -> 409 CATALOG_CHANGE_BLOCKED
        long ch1Ver = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", ch1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andReturn().getResponse().getContentAsString()).get("version").asLong();
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", ch1Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng nhánh",
                                "version", ch1Ver))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CHANGE_BLOCKED"));
    }

    @Test
    void usageEndpointsRejectMissingPermissionWith403() throws Exception {
        UUID anyId = Ids.newId();
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/usages", anyId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/usages", anyId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/usages", anyId)
                        .with(asStudent()))
                .andExpect(status().isForbidden());
    }

    private UUID createCustomGroup(
            String code, String nameVi, String kind, List<String> levels, List<UUID> refGroupIds) throws Exception {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("code", code);
        payload.put("nameVi", nameVi);
        payload.put("kind", kind);
        if (levels != null) {
            payload.put("levels", levels);
        }
        if (refGroupIds != null) {
            payload.put("refGroupIds", refGroupIds);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdGroupIds.add(id);
        return id;
    }

    private JsonNode createItem(UUID groupId, String code, String nameVi) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "nameVi", nameVi))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        createdItemIds.add(UUID.fromString(node.get("id").asText()));
        return node;
    }

    private UUID createTreeItem(
            UUID groupId, UUID parentId, String code, String nameVi, List<UUID> refItemIds) throws Exception {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("code", code);
        payload.put("nameVi", nameVi);
        if (parentId != null) {
            payload.put("parentId", parentId);
        }
        if (refItemIds != null) {
            payload.put("refItemIds", refItemIds);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdItemIds.add(id);
        return id;
    }

    private JsonNode createCombination(
            String code, String nameVi, UUID examItemId, List<UUID> subjectItemIds) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", nameVi,
                                "examItemId", examItemId,
                                "subjectItemIds", subjectItemIds))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        createdItemIds.add(UUID.fromString(node.get("id").asText()));
        return node;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
