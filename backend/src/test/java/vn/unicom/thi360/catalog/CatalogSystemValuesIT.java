package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.application.CatalogCacheService;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2759 (AD-34): the values the six fixed groups hold are system values. Name, description and
 * order stay editable; code, value, status, position and delete are refused with CATALOG_ITEM_SYSTEM.
 * A value an admin adds to a fixed group is not a system value and behaves as before.
 */
class CatalogSystemValuesIT extends AbstractIntegrationTest {

    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID GRADE_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000003");
    private static final List<UUID> SIX_FIXED_GROUPS = List.of(
            UUID.fromString("00000000-0000-7000-8100-000000000001"),
            SUBJECT_GROUP,
            GRADE_GROUP,
            UUID.fromString("00000000-0000-7000-8100-000000000004"),
            UUID.fromString("00000000-0000-7000-8100-000000000005"),
            UUID.fromString("00000000-0000-7000-8100-000000000006"));
    private static final UUID TOAN = UUID.fromString("00000000-0000-7000-8200-000000000201");
    private static final UUID VAN = UUID.fromString("00000000-0000-7000-8200-000000000202");
    private static final UUID G10 = UUID.fromString("00000000-0000-7000-8200-000000000301");
    private static final UUID G11 = UUID.fromString("00000000-0000-7000-8200-000000000302");
    private static final UUID G12 = UUID.fromString("00000000-0000-7000-8200-000000000303");
    private static final UUID GRADE_MIXED = UUID.fromString("00000000-0000-7000-8400-000000000304");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    private record Snap(UUID id, String code, String nameVi, String nameEn, String description,
                        int displayOrder, short status) {
    }

    private List<Snap> snapshot;
    private final List<UUID> createdItemIds = new ArrayList<>();

    @BeforeEach
    void snapshotFixedGroups() {
        clearInvocations(auditRecorder);
        String groupIds = SIX_FIXED_GROUPS.stream()
                .map(id -> "'" + id + "'")
                .collect(java.util.stream.Collectors.joining(","));
        snapshot = jdbc.query(
                "SELECT id, code, name_vi, name_en, description, display_order, status FROM catalog.catalog_item"
                        + " WHERE group_id IN (" + groupIds + ")",
                (rs, n) -> new Snap(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name_vi"),
                        rs.getString("name_en"), rs.getString("description"), rs.getInt("display_order"),
                        rs.getShort("status")));
    }

    @AfterEach
    void restoreFixedGroups() {
        for (UUID id : createdItemIds) {
            jdbc.update("DELETE FROM catalog.catalog_item WHERE id = ?", id);
        }
        createdItemIds.clear();
        for (Snap row : snapshot) {
            jdbc.update(
                    "UPDATE catalog.catalog_item SET code = ?, code_normalized = lower(?), name_vi = ?, name_en = ?,"
                            + " description = ?, display_order = ?, status = ? WHERE id = ?",
                    row.code(), row.code(), row.nameVi(), row.nameEn(), row.description(), row.displayOrder(),
                    row.status(), row.id());
        }
        jdbc.update("DELETE FROM catalog.catalog_import_preview");
        for (String code : List.of("exam", "subject", "grade", "question_level", "question_format", "task_type")) {
            redis.delete(CatalogCacheService.optionsKey(code));
            redis.delete(CatalogCacheService.treeKey(code));
        }
    }

    @Test
    void systemValueIsFlaggedInTheItemResponse() throws Exception {
        assertThat(item(TOAN).get("system").asBoolean()).isTrue();
        assertThat(item(G10).get("system").asBoolean()).isTrue();
    }

    @Test
    void codeChangeIs409AndKeepsTheCode() throws Exception {
        patchItem(TOAN, Map.of("code", "TOAN_HOC"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_SYSTEM"));
        assertThat(item(TOAN).get("code").asText()).isEqualTo("TOAN");
    }

    @Test
    void valueChangeIs409ButTheSameValueIsAccepted() throws Exception {
        int value = item(G11).get("value").asInt();
        patchItem(G11, Map.of("value", value + 40))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_SYSTEM"));
        assertThat(item(G11).get("value").asInt()).isEqualTo(value);

        patchItem(G11, Map.of("value", value, "nameVi", "Lớp 11 (đối chiếu)"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(value));
    }

    @Test
    void deleteIs409AndKeepsTheRow() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", G12)
                        .param("reason", "dọn dẹp")
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_SYSTEM"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM catalog.catalog_item WHERE id = ?", Integer.class, G12)).isEqualTo(1);
    }

    @Test
    void deactivateIs409AndKeepsTheValueActive() throws Exception {
        patchItem(VAN, Map.of("status", "INACTIVE", "reason", "không dùng nữa"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_SYSTEM"));
        assertThat(item(VAN).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void moveIs409() throws Exception {
        mockMvc.perform(put("/api/v1/admin/catalog/items/{id}/parent", G10)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentId", G11, "version", item(G10).get("version").asLong()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_SYSTEM"));
    }

    @Test
    void renamingNamesAndDescriptionIs200AndAudited() throws Exception {
        patchItem(TOAN, Map.of("nameVi", "Toán (đổi tên)", "nameEn", "Maths", "description", "Môn Toán"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameVi").value("Toán (đổi tên)"))
                .andExpect(jsonPath("$.nameEn").value("Maths"))
                .andExpect(jsonPath("$.description").value("Môn Toán"))
                .andExpect(jsonPath("$.code").value("TOAN"))
                .andExpect(jsonPath("$.system").value(true));

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "CATALOG_ITEM_UPDATED".equals(e.action())));
    }

    @Test
    void reorderIs200() throws Exception {
        List<UUID> reversed = List.of(GRADE_MIXED, G12, G11, G10);
        mockMvc.perform(put("/api/v1/admin/catalog/groups/{id}/items/order", GRADE_GROUP)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("itemIds", reversed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(GRADE_MIXED.toString()))
                .andExpect(jsonPath("$[3].id").value(G10.toString()));

        for (int position = 0; position < reversed.size(); position++) {
            assertThat(jdbc.queryForObject(
                    "SELECT display_order FROM catalog.catalog_item WHERE id = ?", Integer.class, reversed.get(position)))
                    .isEqualTo(position);
        }
    }

    @Test
    void valueAddedToAFixedGroupIsNotSystemAndStaysEditableAndDeletable() throws Exception {
        String code = "NEWG_" + shortCode();
        MvcResult created = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", GRADE_GROUP)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "nameVi", "Khối thêm"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.system").value(false))
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        createdItemIds.add(id);
        int value = item(id).get("value").asInt();

        String renamed = code + "_B";
        patchItem(id, Map.of("code", renamed, "nameVi", "Khối thêm (sửa)", "value", value + 40))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(renamed))
                .andExpect(jsonPath("$.value").value(value));

        patchItem(id, Map.of("status", "INACTIVE", "reason", "thử ngừng"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", id)
                        .param("reason", "thử xóa")
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM catalog.catalog_item WHERE id = ?", Integer.class, id)).isZero();
    }

    @Test
    void excelRowsThatTouchASystemValueAreRowErrorsAndNothingIsWritten() throws Exception {
        String newCode = "NEWS_" + shortCode();
        byte[] xlsx = buildXlsx(List.of(
                List.of("Mã*", "Tên tiếng Việt*", "Tên tiếng Anh", "Mô tả", "Trạng thái"),
                List.of("TOAN", "Toán đổi mã", "", "", "Hoạt động"),
                List.of("VAN", "Văn ngừng", "", "", "INACTIVE"),
                List.of(newCode, "Môn mới", "", "", "Hoạt động")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "subjects.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        MvcResult preview = mockMvc.perform(multipart("/api/v1/admin/catalog/groups/{id}/items/import", SUBJECT_GROUP)
                        .file(file)
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.errorRows").value(2))
                .andExpect(jsonPath("$.canConfirm").value(false))
                .andReturn();
        JsonNode body = objectMapper.readTree(preview.getResponse().getContentAsString());
        Map<Integer, String> errorCodeByRow = new LinkedHashMap<>();
        for (JsonNode error : body.get("errors")) {
            errorCodeByRow.put(error.get("rowNo").asInt(), error.get("code").asText());
            assertThat(error.get("column").asText()).isEqualTo("code");
        }
        assertThat(errorCodeByRow).containsOnly(
                Map.entry(2, "CATALOG_ITEM_SYSTEM"), Map.entry(3, "CATALOG_ITEM_SYSTEM"));

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items/import/{previewId}/confirm",
                        SUBJECT_GROUP, UUID.fromString(body.get("previewId").asText()))
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"));

        assertThat(item(VAN).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(item(TOAN).get("nameVi").asText()).isNotEqualTo("Toán đổi mã");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM catalog.catalog_item WHERE group_id = ? AND code_normalized = lower(?)",
                Integer.class, SUBJECT_GROUP, newCode)).isZero();
    }

    private JsonNode item(UUID id) throws Exception {
        String json = mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private ResultActions patchItem(UUID id, Map<String, Object> changes) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(changes);
        body.put("version", item(id).get("version").asLong());
        return mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", id)
                .with(asUser(Permissions.CATALOG_MODIFY))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static byte[] buildXlsx(List<List<String>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    row.createCell(c).setCellValue(cells.get(c));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
