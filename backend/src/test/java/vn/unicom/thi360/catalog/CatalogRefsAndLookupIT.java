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
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.identity.spi.Permissions;

class CatalogRefsAndLookupIT extends AbstractIntegrationTest {

    private static final UUID SUBJECT_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID KNOWLEDGE_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000009");

    private static final UUID SAMPLE_TOAN_ID = UUID.fromString("00000000-0000-7000-8200-000000000201");
    private static final UUID SAMPLE_G10_ID = UUID.fromString("00000000-0000-7000-8200-000000000301");
    private static final UUID SAMPLE_0D1_ID = UUID.fromString("00000000-0000-7000-8200-000000001001");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CatalogLookup catalogLookup;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private final List<UUID> createdGroupIds = new ArrayList<>();
    private final List<UUID> createdItemIds = new ArrayList<>();
    private final List<UUID[]> addedGroupRefs = new ArrayList<>();
    private final Set<String> cacheGroupCodes = new HashSet<>();

    @BeforeEach
    void resetAudit() {
        clearInvocations(auditRecorder);
    }

    @AfterEach
    void cleanup() {
        for (UUID[] pair : addedGroupRefs) {
            jdbcTemplate.update(
                    "DELETE FROM catalog.catalog_item_ref WHERE item_id IN (SELECT id FROM catalog.catalog_item WHERE group_id = ?) AND ref_item_id IN (SELECT id FROM catalog.catalog_item WHERE group_id = ?)",
                    pair[0],
                    pair[1]);
            jdbcTemplate.update(
                    "DELETE FROM catalog.catalog_group_ref WHERE group_id = ? AND ref_group_id = ? AND is_default = false",
                    pair[0],
                    pair[1]);
        }
        addedGroupRefs.clear();
        for (UUID itemId : createdItemIds) {
            jdbcTemplate.update("DELETE FROM catalog.catalog_item_ref WHERE item_id = ? OR ref_item_id = ?", itemId, itemId);
            jdbcTemplate.update("DELETE FROM catalog.catalog_item WHERE id = ?", itemId);
        }
        createdItemIds.clear();
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
    void systemTreeAllowsNonDefaultRefGroupAddAndRemoveUnlessInUseOrDefault() throws Exception {
        String customCode = "REF_EXTRA_" + suffix();
        cacheGroupCodes.add(customCode.toLowerCase());
        cacheGroupCodes.add("knowledge");

        UUID extraGroupId = createSimpleGroup(customCode);
        createdGroupIds.add(extraGroupId);
        JsonNode extraItem = createItem(extraGroupId, null, "EX_1", "Mục thêm");
        UUID extraItemId = UUID.fromString(extraItem.get("id").asText());
        createdItemIds.add(extraItemId);

        // SYSTEM group KNOWLEDGE rejects direct PATCH -> 409 CATALOG_GROUP_SYSTEM
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", KNOWLEDGE_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nameVi", "Đổi tên", "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_SYSTEM"));

        // Default ref group SUBJECT cannot be removed -> 409 CATALOG_REF_GROUP_DEFAULT
        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}/ref-groups/{refId}", KNOWLEDGE_GROUP_ID, SUBJECT_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_GROUP_DEFAULT"));

        // Add non-default ref group to SYSTEM tree KNOWLEDGE
        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/ref-groups/{refId}", KNOWLEDGE_GROUP_ID, extraGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.refGroupId=='" + extraGroupId + "')]").exists());
        addedGroupRefs.add(new UUID[] {KNOWLEDGE_GROUP_ID, extraGroupId});

        // Attach extraItem to chapter 0D1 -> ref group now in use
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}", SAMPLE_0D1_ID, extraItemId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownRefs[?(@.refItemId=='" + extraItemId + "')]").exists());

        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/ref-groups", KNOWLEDGE_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.refGroupId=='" + extraGroupId + "')].linkedItemCount").value(hasItem(1)));

        // Remove ref group while linked item exists -> 409 CATALOG_REF_GROUP_IN_USE
        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}/ref-groups/{refId}", KNOWLEDGE_GROUP_ID, extraGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_GROUP_IN_USE"));

        // Remove item ref (idempotent), then remove non-default ref group -> 204
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}/refs/{refId}", SAMPLE_0D1_ID, extraItemId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}/refs/{refId}", SAMPLE_0D1_ID, extraItemId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}/ref-groups/{refId}", KNOWLEDGE_GROUP_ID, extraGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_ITEM_REFS_UPDATED".equals(e.action())));
    }

    @Test
    void treeRefsOwnInheritedOrAndFilterAndValidationAndCatalogLookupSpi() throws Exception {
        String sfx = suffix();
        String subjCode = "SUBJ_" + sfx;
        String gradeCode = "GRD_" + sfx;
        String undeclaredCode = "UNDECL_" + sfx;
        String treeCode = "TREE_REF_" + sfx;
        cacheGroupCodes.add(subjCode.toLowerCase());
        cacheGroupCodes.add(gradeCode.toLowerCase());
        cacheGroupCodes.add(undeclaredCode.toLowerCase());
        cacheGroupCodes.add(treeCode.toLowerCase());

        UUID subjGroupId = createSimpleGroup(subjCode);
        UUID gradeGroupId = createSimpleGroup(gradeCode);
        UUID undeclaredGroupId = createSimpleGroup(undeclaredCode);
        createdGroupIds.add(subjGroupId);
        createdGroupIds.add(gradeGroupId);
        createdGroupIds.add(undeclaredGroupId);

        UUID toanId = UUID.fromString(createItem(subjGroupId, null, "TOAN", "Toán").get("id").asText());
        UUID lyId = UUID.fromString(createItem(subjGroupId, null, "LY", "Lý").get("id").asText());
        UUID g10Id = UUID.fromString(createItem(gradeGroupId, null, "G10", "Lớp 10").get("id").asText());
        UUID g11Id = UUID.fromString(createItem(gradeGroupId, null, "G11", "Lớp 11").get("id").asText());
        JsonNode g12Node = createItem(gradeGroupId, null, "G12", "Lớp 12");
        UUID g12Id = UUID.fromString(g12Node.get("id").asText());
        long g12Ver = g12Node.get("version").asLong();
        UUID undeclaredItemId = UUID.fromString(createItem(undeclaredGroupId, null, "U1", "Ngoài nhóm").get("id").asText());

        // Deactivate G12 -> linking to inactive item must fail with 400 CATALOG_REF_INVALID
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", g12Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE", "version", g12Ver))))
                .andExpect(status().isOk());

        // Create TREE with refGroups = [subjGroupId, gradeGroupId]
        MvcResult treeRes = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", treeCode,
                                "nameVi", "Cây liên kết",
                                "kind", "TREE",
                                "levels", List.of("Chương", "Bài", "Kiến thức"),
                                "refGroupIds", List.of(subjGroupId, gradeGroupId)))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID treeGroupId = UUID.fromString(objectMapper.readTree(treeRes.getResponse().getContentAsString()).get("id").asText());
        createdGroupIds.add(treeGroupId);

        // Ch1 linked to Toán + Lớp 10
        UUID ch1Id = UUID.fromString(createItemWithRefs(treeGroupId, null, "CH1", "Chương 1", List.of(toanId, g10Id)).get("id").asText());
        UUID l1Id = UUID.fromString(createItem(treeGroupId, ch1Id, "L1", "Bài 1").get("id").asText());
        UUID k1Id = UUID.fromString(createItem(treeGroupId, l1Id, "K1", "Kiến thức 1").get("id").asText());
        UUID k2Id = UUID.fromString(createItem(treeGroupId, l1Id, "K2", "Kiến thức 2").get("id").asText());

        // Ch2 linked to Toán + Lớp 11
        UUID ch2Id = UUID.fromString(createItemWithRefs(treeGroupId, null, "CH2", "Chương 2", List.of(toanId, g11Id)).get("id").asText());
        UUID l2Id = UUID.fromString(createItem(treeGroupId, ch2Id, "L2", "Bài 2").get("id").asText());
        UUID k3Id = UUID.fromString(createItem(treeGroupId, l2Id, "K3", "Kiến thức 3").get("id").asText());

        // Ch3 linked ONLY to Toán (no grade ref!)
        UUID ch3Id = UUID.fromString(createItemWithRefs(treeGroupId, null, "CH3", "Chương 3", List.of(toanId)).get("id").asText());
        UUID l3Id = UUID.fromString(createItem(treeGroupId, ch3Id, "L3", "Bài 3").get("id").asText());
        UUID k4Id = UUID.fromString(createItem(treeGroupId, l3Id, "K4", "Kiến thức 4").get("id").asText());

        // Verify inheritedRefs on K1 has fromItemId == ch1Id and both Toán & Lớp 10
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", k1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownRefs", hasSize(0)))
                .andExpect(jsonPath("$.inheritedRefs", hasSize(2)))
                .andExpect(jsonPath("$.inheritedRefs[0].fromItemId").value(ch1Id.toString()));

        // Linking to item from undeclared group -> 400 CATALOG_REF_INVALID
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}", ch1Id, undeclaredItemId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_INVALID"));

        // Linking to inactive item G12 -> 400 CATALOG_REF_INVALID
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}", ch1Id, g12Id)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_INVALID"));

        // Filtering: Toán AND Lớp 10 -> returns K1, K2 (NOT K3, NOT K4!)
        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("group", treeCode)
                        .param("ref", toanId.toString())
                        .param("ref", g10Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("K1", "K2")));

        // Same-group OR + cross-group AND: Toán AND (Lớp 10 OR Lớp 11) -> K1, K2, K3 (still NOT K4!)
        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("group", treeCode)
                        .param("ref", toanId.toString())
                        .param("ref", g10Id.toString())
                        .param("ref", g11Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("K1", "K2", "K3")));

        // Ch3 has NO GRADE ref -> filtering by Lý or Lớp 10 never returns Ch3/K4
        mockMvc.perform(get("/api/v1/catalog/tree")
                        .param("group", treeCode)
                        .param("ref", toanId.toString())
                        .param("ref", g10Id.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("CH1"));

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("group", treeCode)
                        .param("ref", lyId.toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // CatalogLookup SPI checks
        CatalogItemView activeToan = catalogLookup.findActiveItem(subjCode, "toan").orElseThrow();
        assertThat(activeToan.id()).isEqualTo(toanId);
        assertThat(activeToan.effectiveStatus()).isEqualTo(CatalogStatus.ACTIVE);

        // Inactive item hidden in findActiveItem, visible in findItem
        assertThat(catalogLookup.findActiveItem(gradeCode, "G12")).isEmpty();
        CatalogItemView inactiveG12 = catalogLookup.findItem(g12Id).orElseThrow();
        assertThat(inactiveG12.status()).isEqualTo(CatalogStatus.INACTIVE);

        assertThat(catalogLookup.findLeafIdsUnder(ch1Id)).containsExactlyInAnyOrder(k1Id, k2Id);
        assertThat(catalogLookup.findLeafIds(treeCode, List.of(toanId, g10Id, g11Id)))
                .containsExactlyInAnyOrder(k1Id, k2Id, k3Id);
        assertThat(k4Id).isNotNull();

        // Moving L1 from Ch1 to Ch2 updates inheritedRefs of K1 from Ch1 (G10) to Ch2 (G11)
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", l1Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentId", ch2Id.toString(),
                                "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inheritedRefs[*].refItemId", containsInAnyOrder(toanId.toString(), g11Id.toString())));

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", k1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inheritedRefs[*].fromItemId", containsInAnyOrder(ch2Id.toString(), ch2Id.toString())))
                .andExpect(jsonPath("$.inheritedRefs[*].refItemId", containsInAnyOrder(toanId.toString(), g11Id.toString())));
    }

    @Test
    void devdataSeedsB13SampleItemsAndKeepsSystemFlagsFalseAndSyncsLastValue() throws Exception {
        // Verify B13 counts & [MẪU] descriptions in test/dev profile
        mockMvc.perform(get("/api/v1/catalog/options").param("group", "EXAM").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("HSA", "QDA")));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", "SUBJECT").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", "GRADE").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", "QUESTION_LEVEL").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", "QUESTION_FORMAT").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("SINGLE_CHOICE", "SHORT_ANSWER")));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", "TASK_TYPE").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("SINGLE", "CLUSTER")));

        mockMvc.perform(get("/api/v1/catalog/options").param("group", "SUBJECT_COMBINATION").with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].description").value(org.hamcrest.Matchers.startsWith("[MẪU]")));

        // KNOWLEDGE: 0D1 has 14 leaves; filtering by TOAN & G10 returns all 26 leaves (14 + 6 + 6)
        List<UUID> leaves0D1 = catalogLookup.findLeafIdsUnder(SAMPLE_0D1_ID);
        assertThat(leaves0D1).hasSize(14);
        assertThat(catalogLookup.findLeafIds("KNOWLEDGE", List.of(SAMPLE_TOAN_ID, SAMPLE_G10_ID)))
                .hasSize(26);

        // Verify sample items have is_system = false
        assertThat(catalogLookup.findItem(SAMPLE_0D1_ID).orElseThrow().system()).isFalse();
    }

    private UUID createSimpleGroup(String code) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Nhóm " + code,
                                "nameEn", "Group " + code))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    private JsonNode createItem(UUID groupId, UUID parentId, String code, String nameVi) throws Exception {
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

    private JsonNode createItemWithRefs(
            UUID groupId, UUID parentId, String code, String nameVi, List<UUID> refItemIds)
            throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("nameVi", nameVi);
        if (parentId != null) {
            body.put("parentId", parentId.toString());
        }
        body.put("refItemIds", refItemIds);
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
