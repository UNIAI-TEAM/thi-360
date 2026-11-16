package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-1 (UNI-2607): nhóm hệ thống COMPETENCY (cây một cấp, D3), liên kết giá trị tới SUBJECT và
 * EXAM qua API danh mục (AC-6), và bộ đếm {@link CatalogLookup#revision()}.
 */
class CatalogCompetencyAndRevisionIT extends AbstractIntegrationTest {

    private static final UUID COMPETENCY_GROUP_ID =
            UUID.fromString("00000000-0000-7000-8100-00000000000a");
    private static final UUID SUBJECT_GROUP_ID =
            UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID EXAM_GROUP_ID =
            UUID.fromString("00000000-0000-7000-8100-000000000001");
    private static final UUID SEEDED_VACT_TOAN_ITEM =
            UUID.fromString("00000000-0000-7000-8400-000000000903");
    private static final UUID SEEDED_TOAN_SUBJECT =
            UUID.fromString("00000000-0000-7000-8200-000000000201");
    private static final UUID SEEDED_VACT_EXAM =
            UUID.fromString("00000000-0000-7000-8400-000000000104");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogLookup catalogLookup;

    private UUID createdCompetencyItemId;

    @AfterEach
    void cleanup() {
        if (createdCompetencyItemId != null) {
            jdbcTemplate.update(
                    "DELETE FROM catalog.catalog_item_ref WHERE item_id = ?", createdCompetencyItemId);
            jdbcTemplate.update(
                    "DELETE FROM catalog.catalog_item WHERE id = ?", createdCompetencyItemId);
            createdCompetencyItemId = null;
        }
    }

    @Test
    void competencyGroupIsSystemTreeWithSubjectAndExamRefs() throws Exception {
        // Nhóm hệ thống COMPETENCY: kiểu cây, một cấp khai báo, ref mặc định SUBJECT + EXAM (D3)
        Map<String, Object> group = jdbcTemplate.queryForMap(
                "SELECT type, kind, is_system, levels FROM catalog.catalog_group WHERE id = ?",
                COMPETENCY_GROUP_ID);
        assertThat(((Number) group.get("type")).intValue()).isEqualTo(1);
        assertThat(((Number) group.get("kind")).intValue()).isEqualTo(3);
        assertThat(group.get("is_system")).isEqualTo(true);
        assertThat(String.valueOf(group.get("levels"))).isEqualTo("[\"Năng lực\"]");

        mockMvc.perform(get("/api/v1/admin/catalog/groups/{id}/ref-groups", COMPETENCY_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.refGroupId=='" + SUBJECT_GROUP_ID + "')]").exists())
                .andExpect(jsonPath("$[?(@.refGroupId=='" + EXAM_GROUP_ID + "')]").exists());
    }

    @Test
    void linksCompetencyToSubjectAndExamThroughCatalogApi() throws Exception {
        // AC-6: tạo giá trị năng lực mới (API), rồi gắn liên kết tới môn và kỳ thi (API)
        String createBody = objectMapper.writeValueAsString(Map.of(
                "code", "TEST-COMP-AC6",
                "nameVi", "Năng lực thử AC6",
                "nameEn", "AC6 test competency"));
        String createdJson = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMPETENCY_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TEST-COMP-AC6"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdJson);
        createdCompetencyItemId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}",
                        createdCompetencyItemId, SEEDED_TOAN_SUBJECT)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownRefs[?(@.refItemId=='" + SEEDED_TOAN_SUBJECT + "')]").exists());

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}",
                        createdCompetencyItemId, SEEDED_VACT_EXAM)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownRefs[?(@.refItemId=='" + SEEDED_VACT_EXAM + "')]").exists());
    }

    @Test
    void seededCompetencyRefsVisibleViaSpi() {
        // Bộ mẫu: VACT-TOAN gắn môn TOAN và kỳ thi VACT
        var refs = catalogLookup.findEffectiveItemRefs(java.util.List.of(SEEDED_VACT_TOAN_ITEM));
        assertThat(refs.get(SEEDED_VACT_TOAN_ITEM))
                .anySatisfy(ref -> {
                    assertThat(ref.refGroupCode()).isEqualTo("SUBJECT");
                    assertThat(ref.refItemCode()).isEqualTo("TOAN");
                })
                .anySatisfy(ref -> {
                    assertThat(ref.refGroupCode()).isEqualTo("EXAM");
                    assertThat(ref.refItemCode()).isEqualTo("VACT");
                });
    }

    @Test
    void revisionIncreasesOnEveryCatalogWrite() throws Exception {
        long before = catalogLookup.revision();

        String createBody = objectMapper.writeValueAsString(Map.of(
                "code", "TEST-COMP-REV",
                "nameVi", "Năng lực thử revision"));
        String createdJson = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMPETENCY_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        createdCompetencyItemId = UUID.fromString(objectMapper.readTree(createdJson).get("id").asText());

        long afterItem = catalogLookup.revision();
        assertThat(afterItem).isGreaterThan(before);

        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/refs/{refId}",
                        createdCompetencyItemId, SEEDED_TOAN_SUBJECT)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk());

        long afterRef = catalogLookup.revision();
        assertThat(afterRef).isGreaterThan(afterItem);
    }
}
