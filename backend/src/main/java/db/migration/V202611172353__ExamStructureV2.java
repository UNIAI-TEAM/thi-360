package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import db.migration.examv1.LegacyKnowledgeMatch;
import db.migration.examv1.LegacyMatrixUnit;
import db.migration.examv1.LegacyOptionKind;

/**
 * Converts stored v1 exam structures to the structure v2 JSON contract (2B 3.5).
 *
 * <p>The converter works on raw {@link JsonNode} documents instead of deserializing into typed
 * v1 records so that every field of the stored JSON is preserved verbatim; only the keys listed
 * in 2B 3.5 are rewritten or dropped. The v1 enum vocabulary that appears inside the documents
 * (option kinds, knowledge match, matrix units) lives in {@code db.migration.examv1} and is
 * referenced below.
 */
public class V202611172353__ExamStructureV2 extends BaseJavaMigration {
    private static final Logger LOG = Logger.getLogger(V202611172353__ExamStructureV2.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        migrateWorkingStructures(connection);
        migrateTemplates(connection);
        migratePublishedVersions(connection);
    }

    static JsonNode convert(JsonNode structure, String kindCode) {
        if (structure != null && structure.isArray()) {
            return convertParts((ArrayNode) structure, kindCode);
        }
        if (structure == null || !structure.isObject()) {
            return structure;
        }
        ObjectNode root = (ObjectNode) structure.deepCopy();
        JsonNode parts = root.get("parts");
        if (parts != null && parts.isArray()) {
            root.set("parts", convertParts((ArrayNode) parts, kindCode));
        }
        return root;
    }

    private static void migrateWorkingStructures(Connection connection) throws Exception {
        try (PreparedStatement read = connection.prepareStatement(
                "SELECT s.exam_id, s.parts, COALESCE(k.code, t.kind_code) AS kind_code "
                        + "FROM exam.exam_structure s JOIN exam.exam e ON e.id = s.exam_id "
                        + "LEFT JOIN catalog.catalog_item k ON k.id = e.kind_item_id "
                        + "LEFT JOIN exam.exam_template t ON t.code = e.template_code");
                ResultSet rows = read.executeQuery();
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE exam.exam_structure SET parts = CAST(? AS jsonb), structure_hash = NULL WHERE exam_id = ?")) {
            while (rows.next()) {
                JsonNode before = JSON.readTree(rows.getString("parts"));
                if (hasAllMatch(before)) {
                    LOG.warning("EXAM.STRUCTURE_MIGRATE exam_id=" + rows.getObject("exam_id") + " match=ALL");
                }
                JsonNode after = convert(before, rows.getString("kind_code"));
                if (!before.equals(after)) {
                    update.setString(1, JSON.writeValueAsString(after));
                    update.setObject(2, rows.getObject("exam_id"));
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    private static void migrateTemplates(Connection connection) throws Exception {
        try (PreparedStatement read = connection.prepareStatement(
                "SELECT code, kind_code, structure FROM exam.exam_template");
                ResultSet rows = read.executeQuery();
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE exam.exam_template SET structure = CAST(? AS jsonb) WHERE code = ?")) {
            while (rows.next()) {
                JsonNode before = JSON.readTree(rows.getString("structure"));
                JsonNode after = convert(before, rows.getString("kind_code"));
                if (!before.equals(after)) {
                    update.setString(1, JSON.writeValueAsString(after));
                    update.setString(2, rows.getString("code"));
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    private static void migratePublishedVersions(Connection connection) throws Exception {
        try (PreparedStatement read = connection.prepareStatement(
                "SELECT v.id, v.parts, COALESCE(k.code, t.kind_code) AS kind_code "
                        + "FROM exam.exam_version v JOIN exam.exam e ON e.id = v.exam_id "
                        + "LEFT JOIN catalog.catalog_item k ON k.id = e.kind_item_id "
                        + "LEFT JOIN exam.exam_template t ON t.code = e.template_code");
                ResultSet rows = read.executeQuery();
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE exam.exam_version SET parts = CAST(? AS jsonb), state = CASE "
                                + "WHEN ? THEN 'RETIRED' ELSE state END WHERE id = ?")) {
            while (rows.next()) {
                JsonNode before = JSON.readTree(rows.getString("parts"));
                String kind = rows.getString("kind_code");
                if (hasAllMatch(before)) {
                    LOG.warning("EXAM.STRUCTURE_MIGRATE exam_version=" + rows.getObject("id")
                            + " kind=" + kind + " match=ALL");
                }
                JsonNode after = convert(before, kind);
                boolean retire = "QDA".equals(kind) || "BCA".equals(kind);
                if (!before.equals(after) || retire) {
                    update.setString(1, JSON.writeValueAsString(after));
                    update.setBoolean(2, retire);
                    update.setObject(3, rows.getObject("id"));
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    private static ArrayNode convertParts(ArrayNode parts, String kindCode) {
        ArrayNode converted = NODES.arrayNode();
        for (JsonNode partNode : parts) {
            if (!partNode.isObject()) {
                converted.add(partNode);
                continue;
            }
            ObjectNode part = (ObjectNode) partNode.deepCopy();
            ArrayNode sections = convertSections(array(part.get("sections")), false);
            part.set("sections", sections);

            ArrayNode options = array(part.get("options"));
            boolean hasCombination = false;
            for (JsonNode option : options) {
                hasCombination |= LegacyOptionKind.COMBINATION.name().equals(option.path("kind").asText());
            }
            if (hasCombination) {
                part.set("subjectPool", mergeCombinationSections(options));
                part.set("codeSource", codeSource(kindCode, part.get("subjectPool")));
                part.remove("options");
            } else if (part.has("options")) {
                part.set("options", convertOptions(options, kindCode));
            }
            converted.add(convertNode(part, false));
        }
        return converted;
    }

    private static ArrayNode convertOptions(ArrayNode options, String kindCode) {
        ArrayNode converted = NODES.arrayNode();
        for (JsonNode optionNode : options) {
            if (!optionNode.isObject()) {
                converted.add(optionNode);
                continue;
            }
            ObjectNode option = (ObjectNode) optionNode.deepCopy();
            option.set("sections", convertSections(array(option.get("sections")),
                    LegacyOptionKind.SUBJECT_CHOICE.name().equals(option.path("kind").asText())));
            ArrayNode blocks = NODES.arrayNode();
            for (JsonNode block : array(option.get("blocks"))) {
                blocks.add(convertNode(block, true));
            }
            option.set("blocks", blocks);
            converted.add(convertNode(option, false));
        }
        return converted;
    }

    private static ArrayNode mergeCombinationSections(ArrayNode options) {
        Map<String, JsonNode> bySubject = new LinkedHashMap<>();
        for (JsonNode option : options) {
            if (!LegacyOptionKind.COMBINATION.name().equals(option.path("kind").asText())) {
                continue;
            }
            for (JsonNode section : array(option.get("sections"))) {
                String subjectCode = section.path("subjectCode").asText("");
                if (!subjectCode.isEmpty()) {
                    bySubject.putIfAbsent(subjectCode, convertSection(section, false));
                }
            }
        }
        ArrayNode pool = NODES.arrayNode();
        bySubject.values().forEach(pool::add);
        return pool;
    }

    private static ObjectNode codeSource(String kindCode, JsonNode subjectPool) {
        String branch = null;
        Set<String> subjects = new HashSet<>();
        for (JsonNode section : array(subjectPool)) {
            subjects.add(section.path("subjectCode").asText());
        }
        if ("QDA".equals(kindCode)) {
            if (!subjects.isEmpty() && subjects.stream().allMatch("ANH"::equals)) {
                branch = "ENGLISH";
            } else if (!subjects.contains("ANH")) {
                branch = "SCIENCE";
            }
        }
        ObjectNode source = NODES.objectNode();
        source.put("source", "GLOBAL_LIST");
        if (branch == null) {
            source.putNull("branch");
        } else {
            source.put("branch", branch);
        }
        return source;
    }

    private static ArrayNode convertSections(ArrayNode sections, boolean subjectChoice) {
        ArrayNode converted = NODES.arrayNode();
        for (JsonNode section : sections) {
            converted.add(convertSection(section, subjectChoice));
        }
        return converted;
    }

    private static ObjectNode convertSection(JsonNode sectionNode, boolean subjectChoice) {
        ObjectNode section = sectionNode.isObject()
                ? (ObjectNode) sectionNode.deepCopy() : NODES.objectNode();
        if (subjectChoice) {
            section.put("questionCount", 17);
            convertPercentAnswerQuotas(section, 17);
        }
        if (section.has("matrix") && section.get("matrix").isObject()) {
            ObjectNode matrix = (ObjectNode) section.get("matrix");
            addMatrixDefaults(matrix);
            if (subjectChoice && LegacyMatrixUnit.PERCENT.name().equals(matrix.path("unit").asText())) {
                matrix.put("unit", LegacyMatrixUnit.COUNT.name());
                convertPercentDimension(matrix, "byGrade", 17);
                convertPercentDimension(matrix, "byDifficulty", 17);
                convertPercentDimension(matrix, "byCompetency", 17);
                convertPercentCells(matrix, 17);
            }
        }
        ArrayNode blocks = array(section.get("blocks"));
        if (subjectChoice) {
            resolveRemainderBlocks(blocks, 17);
        }
        ArrayNode convertedBlocks = NODES.arrayNode();
        for (JsonNode block : blocks) {
            convertedBlocks.add(convertNode(block, true));
        }
        section.set("blocks", convertedBlocks);
        section.putIfAbsent("topicQuotas", NODES.arrayNode());
        return (ObjectNode) convertNode(section, false);
    }

    /**
     * Turns the "take the remainder" blocks of a SUBJECT_CHOICE section into normal
     * fixed-count blocks: questionCount = section total − sum of the other blocks, the name
     * loses the remainder wording, and the {@code *_REMAINDER} templateGroupCode is cleared —
     * the remainder mechanism no longer exists (H3: the section itself declares all 17).
     */
    private static void resolveRemainderBlocks(ArrayNode blocks, int total) {
        List<ObjectNode> remainder = new ArrayList<>();
        int assigned = 0;
        for (JsonNode node : blocks) {
            if (!node.isObject()) {
                continue;
            }
            ObjectNode block = (ObjectNode) node;
            if (isRemainder(block)) {
                remainder.add(block);
            } else if (block.hasNonNull("questionCount")) {
                assigned += Math.max(0, block.path("questionCount").asInt());
            }
        }
        for (int i = 0; i < remainder.size(); i++) {
            ObjectNode block = remainder.get(i);
            block.put("questionCount", i == 0 ? Math.max(0, total - assigned) : 0);
            block.put("name", block.path("name").asText("").replace("lấy phần còn lại", "câu đơn"));
            block.putNull("templateGroupCode");
        }
    }

    private static boolean isRemainder(ObjectNode block) {
        return block.path("templateGroupCode").asText("").endsWith("_REMAINDER")
                || block.path("name").asText("").contains("phần còn lại");
    }

    private static ObjectNode convertNode(JsonNode node, boolean block) {
        if (!node.isObject()) {
            return NODES.objectNode();
        }
        ObjectNode converted = (ObjectNode) node.deepCopy();
        converted.remove("taskTypeCode");
        converted.remove("match");
        converted.remove("includeDescendants");
        if (converted.has("knowledgeScope") && converted.get("knowledgeScope").isObject()) {
            ObjectNode scope = (ObjectNode) converted.get("knowledgeScope");
            scope.remove("match");
            scope.remove("includeDescendants");
        }
        if (block) {
            if (converted.path("matrix").isObject()) {
                addMatrixDefaults((ObjectNode) converted.get("matrix"));
            }
            if (!converted.has("shuffleUnits")) {
                converted.put("shuffleUnits", true);
            }
            if (!converted.has("shuffleEditable")) {
                converted.put("shuffleEditable", false);
            }
            converted.putIfAbsent("slots", NODES.arrayNode());
            converted.putIfAbsent("childConditions", NODES.arrayNode());
            converted.putIfAbsent("answerFormats", NODES.arrayNode());
            if (!converted.has("omitted")) {
                converted.put("omitted", false);
            }
            converted.putIfAbsent("topicQuotas", NODES.arrayNode());
        }
        return converted;

    }

    private static void addMatrixDefaults(ObjectNode matrix) {
        if (!matrix.has("gradeEnforcement")) {
            matrix.put("gradeEnforcement", "REQUIRED");
        }
        if (!matrix.has("difficultyEnforcement")) {
            matrix.put("difficultyEnforcement", "REQUIRED");
        }
        if (!matrix.has("basis")) {
            matrix.put("basis", "QUESTION");
        }
    }

    private static void convertPercentAnswerQuotas(ObjectNode section, int total) {
        JsonNode quotas = section.get("answerQuotas");
        if (quotas == null || !quotas.isObject() || !LegacyMatrixUnit.PERCENT.name().equals(quotas.path("unit").asText())) {
            return;
        }
        ObjectNode output = (ObjectNode) quotas.deepCopy();
        output.put("unit", LegacyMatrixUnit.COUNT.name());
        output.set("items", apportion(array(quotas.get("items")), "value", total));
        section.set("answerQuotas", output);
    }

    private static void convertPercentDimension(ObjectNode matrix, String field, int total) {
        JsonNode values = matrix.get(field);
        if (values != null && values.isArray()) {
            matrix.set(field, apportion((ArrayNode) values, "value", total));
        }
    }

    private static void convertPercentCells(ObjectNode matrix, int total) {
        JsonNode cells = matrix.get("cells");
        if (cells != null && cells.isArray()) {
            matrix.set("cells", apportion((ArrayNode) cells, "value", total));
        }
    }

    private static ArrayNode apportion(ArrayNode values, String valueField, int total) {
        ArrayNode output = NODES.arrayNode();
        if (values.isEmpty()) {
            return output;
        }
        List<Allocation> allocations = new ArrayList<>();
        int assigned = 0;
        for (int i = 0; i < values.size(); i++) {
            ObjectNode value = values.get(i).isObject()
                    ? (ObjectNode) values.get(i).deepCopy() : NODES.objectNode();
            BigDecimal percent = value.path(valueField).decimalValue();
            BigDecimal exact = percent.multiply(BigDecimal.valueOf(total)).divide(BigDecimal.valueOf(100));
            int floor = exact.setScale(0, java.math.RoundingMode.FLOOR).intValueExact();
            allocations.add(new Allocation(i, value, floor, exact.subtract(BigDecimal.valueOf(floor))));
            assigned += floor;
        }
        allocations.sort(Comparator.comparing(Allocation::remainder).reversed()
                .thenComparingInt(Allocation::index));
        int remaining = Math.max(0, total - assigned);
        for (int i = 0; i < allocations.size(); i++) {
            Allocation allocation = allocations.get(i);
            allocation.value().put(valueField, allocation.floor() + (i < remaining ? 1 : 0));
        }
        allocations.sort(Comparator.comparingInt(Allocation::index));
        allocations.forEach(a -> output.add(a.value()));
        return output;
    }

    private record Allocation(int index, ObjectNode value, int floor, BigDecimal remainder) {
    }

    private static ArrayNode array(JsonNode node) {
        return node != null && node.isArray() ? (ArrayNode) node : NODES.arrayNode();
    }

    private static boolean hasAllMatch(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject() && LegacyKnowledgeMatch.ALL.name().equals(node.path("match").asText())) {
            return true;
        }
        for (JsonNode child : node) {
            if (hasAllMatch(child)) {
                return true;
            }
        }
        return false;
    }
}
