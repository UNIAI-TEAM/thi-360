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
    void systemTreeDefaultRefGroupNotRemovable() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", KNOWLEDGE_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nameVi", "Đổi tên", "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_GROUP_SYSTEM"));

        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}/ref-groups/{refId}", KNOWLEDGE_GROUP_ID, SUBJECT_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_GROUP_DEFAULT"));
    }

    @Test
    void systemTreeAddAndRemoveNonDefaultRefGroup() throws Exception {
        String customCode = "REF_EXTRA_" + suffix();
        cacheGroupCodes.add(customCode.toLowerCase());
        cacheGroupCodes.add("knowledge");

        UUID extraGroupId = createSimpleGroup(customCode);
        createdGroupIds.add(extraGroupId);
        UUID extraItemId = UUID.fromString(createItem(extraGroupId, null, "EX_1", "Mục thêm").get("id").asText());
        createdItemIds.add(extraItemId);

        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/ref-groups/{refId}", KNOWLEDGE_GROUP_ID, extraGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.refGroupId=='" + extraGroupId + "')]").exists());
        addedGroupRefs.add(new UUID[] {KNOWLEDGE_GROUP_ID, extraGroupId});

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}", SAMPLE_0D1_ID, extraItemId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownRefs[?(@.refItemId=='" + extraItemId + "')]").exists());

        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/ref-groups", KNOWLEDGE_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.refGroupId=='" + extraGroupId + "')].linkedItemCount").value(hasItem(1)));

        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}/ref-groups/{refId}", KNOWLEDGE_GROUP_ID, extraGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_GROUP_IN_USE"));

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
    void chapterRefsReturnAllChapterLeavesOnDescendants() throws Exception {
        RefFixture fx = createRefTreeFixture();

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.k1Id())
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownRefs", hasSize(0)))
                .andExpect(jsonPath("$.inheritedRefs", hasSize(2)))
                .andExpect(jsonPath("$.inheritedRefs[0].fromItemId").value(fx.ch1Id().toString()));

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("group", fx.treeCode())
                        .param("ref", fx.toanId().toString())
                        .param("ref", fx.g10Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("K1", "K2")));
    }

    @Test
    void refFilterOrWithinSameGroupAndAcrossDifferentGroups() throws Exception {
        RefFixture fx = createRefTreeFixture();

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("group", fx.treeCode())
                        .param("ref", fx.toanId().toString())
                        .param("ref", fx.g10Id().toString())
                        .param("ref", fx.g11Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("K1", "K2", "K3")));
    }

    @Test
    void nodeWithoutRefInFilteredGroupExcluded() throws Exception {
        RefFixture fx = createRefTreeFixture();

        mockMvc.perform(get("/api/v1/catalog/tree")
                        .param("group", fx.treeCode())
                        .param("ref", fx.toanId().toString())
                        .param("ref", fx.g10Id().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("CH1"));

        mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                        .param("group", fx.treeCode())
                        .param("ref", fx.lyId().toString())
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void refToUndeclaredOrInactiveGroupItemReturns400() throws Exception {
        RefFixture fx = createRefTreeFixture();

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}", fx.ch1Id(), fx.undeclaredItemId())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_INVALID"));

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}", fx.ch1Id(), fx.g12Id())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_INVALID"));
    }

    @Test
    void moveUpdatesInheritedRefsFromNewParent() throws Exception {
        RefFixture fx = createRefTreeFixture();

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", fx.l1Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentId", fx.ch2Id().toString(),
                                "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inheritedRefs[*].refItemId", containsInAnyOrder(fx.toanId().toString(), fx.g11Id().toString())));

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.k1Id())
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inheritedRefs[*].fromItemId", containsInAnyOrder(fx.ch2Id().toString(), fx.ch2Id().toString())))
                .andExpect(jsonPath("$.inheritedRefs[*].refItemId", containsInAnyOrder(fx.toanId().toString(), fx.g11Id().toString())));
    }

    @Test
    void catalogLookupSpiCoversActiveInactiveAndLeafIds() throws Exception {
        RefFixture fx = createRefTreeFixture();

        CatalogItemView activeToan = catalogLookup.findActiveItem(fx.subjCode(), "toan").orElseThrow();
        assertThat(activeToan.id()).isEqualTo(fx.toanId());
        assertThat(activeToan.effectiveStatus()).isEqualTo(CatalogStatus.ACTIVE);

        assertThat(catalogLookup.findActiveItem(fx.gradeCode(), "G12")).isEmpty();
        CatalogItemView inactiveG12 = catalogLookup.findItem(fx.g12Id()).orElseThrow();
        assertThat(inactiveG12.status()).isEqualTo(CatalogStatus.INACTIVE);

        assertThat(catalogLookup.findLeafIdsUnder(fx.ch1Id())).containsExactlyInAnyOrder(fx.k1Id(), fx.k2Id());
        assertThat(catalogLookup.findLeafIds(fx.treeCode(), List.of(fx.toanId(), fx.g10Id(), fx.g11Id())))
                .containsExactlyInAnyOrder(fx.k1Id(), fx.k2Id(), fx.k3Id());
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

    @Test
    void findLeafIdsEmptyAndNullRefsExcludeInactiveAncestors() throws Exception {
        RefFixture fx = createRefTreeFixture();
        long ch3Ver = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.ch3Id())
                                .with(asUser(Permissions.CATALOG_VIEW)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();

        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", fx.ch3Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng chương 3",
                                "version", ch3Ver))))
                .andExpect(status().isOk());

        assertThat(catalogLookup.findLeafIds(fx.treeCode(), List.of()))
                .containsExactlyInAnyOrder(fx.k1Id(), fx.k2Id(), fx.k3Id());
        assertThat(catalogLookup.findLeafIds(fx.treeCode(), null))
                .containsExactlyInAnyOrder(fx.k1Id(), fx.k2Id(), fx.k3Id());
    }

    @Test
    void inactiveRefGroupRejectedOnNewWritesWhileExistingLinksRemainReadableAndInlineRefOpsBumpVersion() throws Exception {
        RefFixture fx = createRefTreeFixture();
        String extraCode = "EXTRA_" + suffix();
        UUID extraGroupId = createSimpleGroup(extraCode);
        createdGroupIds.add(extraGroupId);
        cacheGroupCodes.add(extraCode.toLowerCase());

        // Warm public tree cache BEFORE mutating inline refs and renaming target item
        mockMvc.perform(get("/api/v1/catalog/tree").param("group", fx.treeCode()).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownRefs", hasSize(2)));

        // Inline PUT/DELETE ref on item bumps item version; idempotent repeats do not
        long ch1Ver = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.ch1Id())
                                .with(asUser(Permissions.CATALOG_VIEW)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refItemId}", fx.ch1Id(), fx.lyId())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ch1Ver + 1));

        // Idempotent repeat does not bump version again
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refItemId}", fx.ch1Id(), fx.lyId())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ch1Ver + 1));

        // Warmed tree cache reflects newly attached ownRef
        mockMvc.perform(get("/api/v1/catalog/tree").param("group", fx.treeCode()).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownRefs", hasSize(3)));

        // Stale PATCH using pre-ref version returns 412 VERSION_CONFLICT
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", fx.ch1Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nameVi", "Cũ", "version", ch1Ver))))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}/refs/{refItemId}", fx.ch1Id(), fx.lyId())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.ch1Id())
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ch1Ver + 2));

        // Inline PUT/DELETE ref-group on tree group bumps group version; idempotent repeats do not
        long treeVer = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}", fx.treeGroupId())
                                .with(asUser(Permissions.CATALOG_VIEW)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();

        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/ref-groups/{refGroupId}", fx.treeGroupId(), extraGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}", fx.treeGroupId())
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(treeVer + 1));

        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/ref-groups/{refGroupId}", fx.treeGroupId(), extraGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}", fx.treeGroupId())
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(treeVer + 1));
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", fx.treeGroupId())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nameVi", "Tên cũ", "version", treeVer))))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(delete("/api/v1/admin/catalog/groups/{id}/ref-groups/{refGroupId}", fx.treeGroupId(), extraGroupId)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}", fx.treeGroupId())
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(treeVer + 2));

        // Warm public tree immediately before renaming target item (no intervening tree mutation)
        mockMvc.perform(get("/api/v1/catalog/tree").param("group", fx.treeCode()).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownRefs[?(@.code=='TOAN')].nameVi", hasItem("Toán")));

        // Renaming referenced item in subjGroup evicts dependent tree cache
        long toanVer = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.toanId())
                                .with(asUser(Permissions.CATALOG_VIEW)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", fx.toanId())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nameVi", "Toán nâng cao", "version", toanVer))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/catalog/tree").param("group", fx.treeCode()).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownRefs[?(@.code=='TOAN')].nameVi", hasItem("Toán nâng cao")));
        // Deactivate subjGroup: new refs to toanId/lyId are rejected on create, update, and inline PUT,
        // while existing ch1Id link remains readable and editable if retained
        long subjVer = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}", fx.subjGroupId())
                                .with(asUser(Permissions.CATALOG_VIEW)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();
        mockMvc.perform(patch("/api/v1/admin/catalog/groups/{id}", fx.subjGroupId())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng nhóm môn",
                                "version", subjVer))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", fx.treeGroupId())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "CH4",
                                "nameVi", "Chương 4",
                                "refItemIds", List.of(fx.toanId())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_INVALID"));

        long ch2Ver = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.ch2Id())
                                .with(asUser(Permissions.CATALOG_VIEW)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("version").asLong();
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", fx.ch2Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refItemIds", List.of(fx.lyId()),
                                "version", ch2Ver))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_INVALID"));

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refItemId}", fx.ch2Id(), fx.lyId())
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_REF_INVALID"));

        // Existing link on ch1Id remains readable and retained on unrelated PATCH
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", fx.ch1Id())
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownRefs[*].refItemId", hasItem(fx.toanId().toString())));
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", fx.ch1Id())
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nameVi", "Chương 1 sửa tên",
                                "refItemIds", List.of(fx.toanId(), fx.g10Id()),
                                "version", ch1Ver + 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameVi").value("Chương 1 sửa tên"));
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

    private RefFixture createRefTreeFixture() throws Exception {
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

        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", g12Id)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng lớp 12",
                                "version", g12Ver))))
                .andExpect(status().isOk());
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

        UUID ch1Id = UUID.fromString(createItemWithRefs(treeGroupId, null, "CH1", "Chương 1", List.of(toanId, g10Id)).get("id").asText());
        UUID l1Id = UUID.fromString(createItem(treeGroupId, ch1Id, "L1", "Bài 1").get("id").asText());
        UUID k1Id = UUID.fromString(createItem(treeGroupId, l1Id, "K1", "Kiến thức 1").get("id").asText());
        UUID k2Id = UUID.fromString(createItem(treeGroupId, l1Id, "K2", "Kiến thức 2").get("id").asText());

        UUID ch2Id = UUID.fromString(createItemWithRefs(treeGroupId, null, "CH2", "Chương 2", List.of(toanId, g11Id)).get("id").asText());
        UUID l2Id = UUID.fromString(createItem(treeGroupId, ch2Id, "L2", "Bài 2").get("id").asText());
        UUID k3Id = UUID.fromString(createItem(treeGroupId, l2Id, "K3", "Kiến thức 3").get("id").asText());

        UUID ch3Id = UUID.fromString(createItemWithRefs(treeGroupId, null, "CH3", "Chương 3", List.of(toanId)).get("id").asText());
        UUID l3Id = UUID.fromString(createItem(treeGroupId, ch3Id, "L3", "Bài 3").get("id").asText());
        createItem(treeGroupId, l3Id, "K4", "Kiến thức 4");

        return new RefFixture(
                subjGroupId, treeGroupId, subjCode, gradeCode, treeCode, toanId, lyId, g10Id, g11Id, g12Id,
                undeclaredItemId, ch1Id, ch2Id, ch3Id, l1Id, k1Id, k2Id, k3Id);
    }

    private record RefFixture(
            UUID subjGroupId,
            UUID treeGroupId,
            String subjCode,
            String gradeCode,
            String treeCode,
            UUID toanId,
            UUID lyId,
            UUID g10Id,
            UUID g11Id,
            UUID g12Id,
            UUID undeclaredItemId,
            UUID ch1Id,
            UUID ch2Id,
            UUID ch3Id,
            UUID l1Id,
            UUID k1Id,
            UUID k2Id,
            UUID k3Id) {}
}
