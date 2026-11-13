package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
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

class CatalogTreeApiIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private final List<UUID> createdGroupIds = new ArrayList<>();
    private final Set<String> cacheGroupCodes = new HashSet<>();

    @BeforeEach
    void resetAudit() {
        clearInvocations(auditRecorder);
    }

    @AfterEach
    void cleanup() {
        for (UUID groupId : createdGroupIds) {
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
    }

    @Test
    void treeLevelsValidationAndLockWhenItemsExist() throws Exception {
        String code = "TREE_LVL_" + suffix();
        cacheGroupCodes.add(code.toLowerCase());

        // < 2 levels rejected
        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Cây lỗi",
                                "kind", "TREE",
                                "levels", List.of("Một cấp")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Duplicate level names rejected
        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Cây trùng cấp",
                                "kind", "TREE",
                                "levels", List.of("Cấp 1", "cấp 1")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Valid 3 levels created
        MvcResult groupRes = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Cây kiến thức",
                                "kind", "TREE",
                                "levels", List.of("Chương", "Bài", "Kiến thức")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("TREE"))
                .andExpect(jsonPath("$.levels", hasSize(3)))
                .andReturn();
        JsonNode groupNode = objectMapper.readTree(groupRes.getResponse().getContentAsString());
        UUID groupId = UUID.fromString(groupNode.get("id").asText());
        long groupVersion = groupNode.get("version").asLong();
        createdGroupIds.add(groupId);

        // Changing level count when empty succeeds
        groupVersion = objectMapper.readTree(
                mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", groupId)
                                .with(asUser(Permissions.CATALOG_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "levels", List.of("Phần", "Chương", "Bài", "Mục"),
                                        "version", groupVersion))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.levels", hasSize(4)))
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();

        // Add one root item -> level count becomes locked, rename still allowed
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "ROOT_1",
                                "nameVi", "Phần 1"))))
                .andExpect(status().isCreated());

        // Adding/removing level count now -> 409 CATALOG_LEVELS_LOCKED
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "levels", List.of("Chương", "Bài", "Kiến thức"),
                                "version", groupVersion))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_LEVELS_LOCKED"));

        // Renaming same count succeeds
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "levels", List.of("P", "C", "B", "K"),
                                "version", groupVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levels[0]").value("P"));
    }

    @Test
    void treeDescendantsNonPrefixLeafLimitDuplicateAcrossBranchesInactiveAncestorMoveAndDelete()
            throws Exception {
        String code = "TREE_FULL_" + suffix();
        cacheGroupCodes.add(code.toLowerCase());

        UUID groupId = createTreeGroup(code, List.of("Chương", "Bài", "Kiến thức"));
        createdGroupIds.add(groupId);

        // Chapter 1 & Chapter 2
        JsonNode ch1 = createNode(groupId, null, "CH_ONE", "Chương 1");
        UUID ch1Id = UUID.fromString(ch1.get("id").asText());
        long ch1Ver = ch1.get("version").asLong();

        JsonNode ch2 = createNode(groupId, null, "CH_TWO", "Chương 2");
        UUID ch2Id = UUID.fromString(ch2.get("id").asText());
        long ch2Ver = ch2.get("version").asLong();

        // Lessons under Ch1 (unrelated code prefix!)
        JsonNode l1 = createNode(groupId, ch1Id, "LESSON_ALPHA", "Bài Alpha");
        UUID l1Id = UUID.fromString(l1.get("id").asText());
        JsonNode l2 = createNode(groupId, ch1Id, "LESSON_BETA", "Bài Beta");
        UUID l2Id = UUID.fromString(l2.get("id").asText());

        // Leaves under L1 and L2 (deliberately non-prefix codes!)
        JsonNode k1 = createNode(groupId, l1Id, "XYZ_CONCEPT_1", "Kiến thức 1");
        UUID k1Id = UUID.fromString(k1.get("id").asText());
        JsonNode k2 = createNode(groupId, l1Id, "QRS_CONCEPT_2", "Kiến thức 2");
        UUID k2Id = UUID.fromString(k2.get("id").asText());
        JsonNode k3 = createNode(groupId, l2Id, "MNO_CONCEPT_3", "Kiến thức 3");
        UUID k3Id = UUID.fromString(k3.get("id").asText());

        // Duplicate code across different branches blocked
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "xyz_concept_1",
                                "nameVi", "Trùng khác nhánh",
                                "parentId", l2Id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"));

        // Adding child under leaf blocked with CATALOG_TREE_DEPTH_EXCEEDED
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "TOO_DEEP",
                                "nameVi", "Quá cấp",
                                "parentId", k1Id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_TREE_DEPTH_EXCEEDED"));

        // Public descendants of Chapter 1 returns all 3 leaves despite non-prefix codes
        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", ch1Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("XYZ_CONCEPT_1", "QRS_CONCEPT_2", "MNO_CONCEPT_3")));

        // Public descendants of Lesson 1 returns ONLY its 2 leaves
        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", l1Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("XYZ_CONCEPT_1", "QRS_CONCEPT_2")));

        // Public descendants with leafOnly=false includes lessons + leaves under Chapter 1 (5 nodes)
        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", ch1Id.toString())
                        .param("leafOnly", "false")
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));

        // Admin tree search with q returns matched leaf AND its ancestor chain
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/tree", groupId)
                        .param("q", "QRS_CONCEPT_2")
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("CH_ONE", "LESSON_ALPHA", "QRS_CONCEPT_2")))
                .andExpect(jsonPath("$[?(@.code=='QRS_CONCEPT_2')].matched").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.code=='CH_ONE')].matched").value(hasItem(false)));

        // Reorder siblings under same parent succeeds; mixing parents fails
        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/items/order", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemIds", List.of(k2Id, k1Id)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("QRS_CONCEPT_2"))
                .andExpect(jsonPath("$[1].code").value("XYZ_CONCEPT_1"));

        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/items/order", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemIds", List.of(k1Id, k3Id)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INVALID"));

        // Deactivate Chapter 1 -> tree and descendants hide Chapter 1 and children; admin shows effectiveStatus INACTIVE
        ch1Ver = objectMapper.readTree(
                mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", ch1Id)
                                .with(asUser(Permissions.CATALOG_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "status", "INACTIVE",
                                        "reason", "Tạm ngừng chương",
                                        "version", ch1Ver))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();

        mockMvc.perform(get("/api/v1/catalog/tree")
                        .param("group", code)
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("CH_TWO"));

        mockMvc.perform(get("/api/v1/catalog/tree")
                        .param("group", code)
                        .param("parentId", ch1Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", l1Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Admin still sees child's own status ACTIVE and effectiveStatus INACTIVE
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", k1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.effectiveStatus").value("INACTIVE"));

        // Adding child under node with inactive ancestor blocked
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "UNDER_INACTIVE",
                                "nameVi", "Dưới cha ngừng",
                                "parentId", l1Id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INACTIVE"));

        // Reactivate Chapter 1 -> descendants immediately restored!
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", ch1Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACTIVE",
                                "version", ch1Ver))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", l1Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Move branch: move Lesson 1 (and its 2 leaves!) from Chapter 1 to Chapter 2
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", l1Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", ch2Id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(ch2Id.toString()))
                .andExpect(jsonPath("$.path").value("/" + ch2Id + "/" + l1Id + "/"));

        // Descendants path of k1 and k2 updated in same tx!
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", k1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/" + ch2Id + "/" + l1Id + "/" + k1Id + "/"));

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", ch2Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("QRS_CONCEPT_2", "XYZ_CONCEPT_1")));

        // Chapter 1 now only has Lesson 2's 1 leaf
        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", ch1Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("MNO_CONCEPT_3"));

        // Invalid moves: wrong depth, self/cycle, inactive parent
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", l1Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", l2Id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INVALID"));

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", ch2Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", ch1Id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INVALID"));

        // Deactivate Chapter 2, then try moving Lesson 2 under Chapter 2 -> CATALOG_PARENT_INACTIVE
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", ch2Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "version", ch2Ver))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", l2Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", ch2Id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INACTIVE"));

        // Delete node with children blocked with CATALOG_NODE_HAS_CHILDREN; deleting leaf then parent works
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", l2Id)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_NODE_HAS_CHILDREN"));

        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", k3Id)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", l2Id)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_ITEM_MOVED".equals(e.action())));
    }

    @Test
    void treeTooLargeBlockedAt50000Nodes() throws Exception {
        String code = "TREE_BIG_" + suffix();
        cacheGroupCodes.add(code.toLowerCase());
        UUID groupId = createTreeGroup(code, List.of("Cấp 1", "Cấp 2"));
        createdGroupIds.add(groupId);

        jdbcTemplate.update(
                """
                INSERT INTO catalog.catalog_item
                    (id, group_id, code, code_normalized, value, name_vi, display_order, status, is_system, parent_id, depth, path)
                SELECT gen_random_uuid(), ?, 'N_' || gs, 'n_' || gs, gs, 'Nút ' || gs, gs, 1, false, NULL, 1, '/' || gs || '/'
                FROM generate_series(1, 50000) AS gs
                """,
                groupId);

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "OVER_LIMIT",
                                "nameVi", "Vượt giới hạn"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_TREE_TOO_LARGE"));
    }

    private UUID createTreeGroup(String code, List<String> levels) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Cây " + code,
                                "kind", "TREE",
                                "levels", levels))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    private JsonNode createNode(UUID groupId, UUID parentId, String code, String nameVi) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("nameVi", nameVi);
        if (parentId != null) {
            body.put("parentId", parentId.toString());
        }
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
