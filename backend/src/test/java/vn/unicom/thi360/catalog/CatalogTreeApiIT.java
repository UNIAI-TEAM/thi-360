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
    void descendantsOfChapter() throws Exception {
        TreeFixture fx = createSampleTree("TREE_CH_");
        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", fx.ch1Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("XYZ_CONCEPT_1", "QRS_CONCEPT_2", "MNO_CONCEPT_3")));

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", fx.ch1Id().toString())
                        .param("leafOnly", "false")
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void descendantsOfLessonNonPrefixCodes() throws Exception {
        TreeFixture fx = createSampleTree("TREE_LSN_");
        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", fx.l1Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("XYZ_CONCEPT_1", "QRS_CONCEPT_2")));
    }

    @Test
    void childUnderLeafBlocked() throws Exception {
        TreeFixture fx = createSampleTree("TREE_LEAF_");
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", fx.groupId())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "TOO_DEEP",
                                "nameVi", "Quá cấp",
                                "parentId", fx.k1Id()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_TREE_DEPTH_EXCEEDED"));
    }

    @Test
    void inactiveAncestorHidesChildrenAndReactivationRestores() throws Exception {
        TreeFixture fx = createSampleTree("TREE_INACT_");

        long ch1Ver = objectMapper.readTree(
                mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", fx.ch1Id())
                                .with(asUser(Permissions.CATALOG_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "status", "INACTIVE",
                                        "reason", "Tạm ngừng chương",
                                        "version", fx.ch1Ver()))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();

        mockMvc.perform(get("/api/v1/catalog/tree")
                        .param("group", fx.code())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("CH_TWO"));

        mockMvc.perform(get("/api/v1/catalog/tree")
                        .param("group", fx.code())
                        .param("parentId", fx.ch1Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", fx.l1Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.k1Id())
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.effectiveStatus").value("INACTIVE"));

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", fx.groupId())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "UNDER_INACTIVE",
                                "nameVi", "Dưới cha ngừng",
                                "parentId", fx.l1Id()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INACTIVE"));

        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", fx.ch1Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACTIVE",
                                "version", ch1Ver))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", fx.l1Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void moveUpdatesAllDescendantPaths() throws Exception {
        TreeFixture fx = createSampleTree("TREE_MOVE_");

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", fx.l1Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", fx.ch2Id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(fx.ch2Id().toString()))
                .andExpect(jsonPath("$.path").value("/" + fx.ch2Id() + "/" + fx.l1Id() + "/"));

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.k1Id())
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/" + fx.ch2Id() + "/" + fx.l1Id() + "/" + fx.k1Id() + "/"));

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", fx.ch2Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("QRS_CONCEPT_2", "XYZ_CONCEPT_1")));

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("nodeId", fx.ch1Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("MNO_CONCEPT_3"));

        // Invalid moves: wrong depth, self/cycle, inactive parent
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", fx.l1Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", fx.l2Id()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INVALID"));

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", fx.ch2Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", fx.ch1Id()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INVALID"));

        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", fx.ch2Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE", "version", fx.ch2Ver()))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", fx.l2Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("parentId", fx.ch2Id()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INACTIVE"));

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_ITEM_MOVED".equals(e.action())));
    }

    @Test
    void deleteWithChildrenBlocked() throws Exception {
        TreeFixture fx = createSampleTree("TREE_DEL_");
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", fx.l2Id())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_NODE_HAS_CHILDREN"));

        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", fx.k3Id())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", fx.l2Id())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());
    }

    @Test
    void duplicateCodeAcrossBranchesBlocked() throws Exception {
        TreeFixture fx = createSampleTree("TREE_DUP_");
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", fx.groupId())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "xyz_concept_1",
                                "nameVi", "Trùng khác nhánh",
                                "parentId", fx.l2Id()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"));
    }

    @Test
    void treeSearchIncludesAncestorsAndReorderRequiresSameParent() throws Exception {
        TreeFixture fx = createSampleTree("TREE_SRCH_");
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/tree", fx.groupId())
                        .param("q", "QRS_CONCEPT_2")
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("CH_ONE", "LESSON_ALPHA", "QRS_CONCEPT_2")))
                .andExpect(jsonPath("$[?(@.code=='QRS_CONCEPT_2')].matched").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.code=='CH_ONE')].matched").value(hasItem(false)));

        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/items/order", fx.groupId())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", List.of(fx.k2Id(), fx.k1Id())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("QRS_CONCEPT_2"))
                .andExpect(jsonPath("$[1].code").value("XYZ_CONCEPT_1"));

        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/items/order", fx.groupId())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", List.of(fx.k1Id(), fx.k3Id())))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_PARENT_INVALID"));
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


    private TreeFixture createSampleTree(String prefix) throws Exception {
        String code = prefix + suffix();
        cacheGroupCodes.add(code.toLowerCase());
        UUID groupId = createTreeGroup(code, List.of("Chương", "Bài", "Kiến thức"));
        createdGroupIds.add(groupId);

        JsonNode ch1 = createNode(groupId, null, "CH_ONE", "Chương 1");
        UUID ch1Id = UUID.fromString(ch1.get("id").asText());
        long ch1Ver = ch1.get("version").asLong();
        JsonNode ch2 = createNode(groupId, null, "CH_TWO", "Chương 2");
        UUID ch2Id = UUID.fromString(ch2.get("id").asText());
        long ch2Ver = ch2.get("version").asLong();

        JsonNode l1 = createNode(groupId, ch1Id, "LESSON_ALPHA", "Bài Alpha");
        UUID l1Id = UUID.fromString(l1.get("id").asText());
        JsonNode l2 = createNode(groupId, ch1Id, "LESSON_BETA", "Bài Beta");
        UUID l2Id = UUID.fromString(l2.get("id").asText());

        UUID k1Id = UUID.fromString(createNode(groupId, l1Id, "XYZ_CONCEPT_1", "Kiến thức 1").get("id").asText());
        UUID k2Id = UUID.fromString(createNode(groupId, l1Id, "QRS_CONCEPT_2", "Kiến thức 2").get("id").asText());
        UUID k3Id = UUID.fromString(createNode(groupId, l2Id, "MNO_CONCEPT_3", "Kiến thức 3").get("id").asText());

        return new TreeFixture(code, groupId, ch1Id, ch1Ver, ch2Id, ch2Ver, l1Id, l2Id, k1Id, k2Id, k3Id);
    }

    private record TreeFixture(
            String code,
            UUID groupId,
            UUID ch1Id,
            long ch1Ver,
            UUID ch2Id,
            long ch2Ver,
            UUID l1Id,
            UUID l2Id,
            UUID k1Id,
            UUID k2Id,
            UUID k3Id) {}
    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
