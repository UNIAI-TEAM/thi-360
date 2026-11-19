package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * CR-4 (UNI-2762, FTM R-12): the set count, set size and short-answer minimum of the science subjects of HSA and
 * QDA at the check step. The limits never block a draft; they block publishing, and only standard exams.
 */
class ExamCheckSubjectLimitsIT extends AbstractSubjectLimitsIT {

    // ---- AC-1 ---------------------------------------------------------------------------------------------

    @Test
    void qdaPhysicsThreeSetsBlocksPublish() throws Exception {
        String base = create("QDA", "SUBJECT", null, "LY", null);
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode physics = section(edited, "LY");
        setShape(physics, 3, 3);
        save(base, before, edited);

        JsonNode result = check(base);
        JsonNode found = issue(result, SET_CODE);
        assertThat(found.path("severity").asText()).isEqualTo("ERROR");
        assertThat(found.path("sectionId").asText()).isEqualTo(physics.path("id").asText());
        assertThat(found.path("subjectCode").asText()).isEqualTo("LY");
        assertThat(found.path("required").asInt()).isEqualTo(2);
        assertThat(found.path("available").asInt()).isEqualTo(3);
        assertThat(codes(result)).doesNotContain(SHORT_CODE);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        publishRefused(base, result);
    }

    // ---- AC-2 ---------------------------------------------------------------------------------------------

    @Test
    void hsaChemistryNeedsShortAnswer() throws Exception {
        String base = create("HSA", "SUBJECT", null, "HOA", null);
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode chemistry = section(edited, "HOA");
        dropShortAnswer(chemistry);
        save(base, before, edited);

        JsonNode result = check(base);
        JsonNode found = issue(result, SHORT_CODE);
        assertThat(found.path("severity").asText()).isEqualTo("ERROR");
        assertThat(found.path("sectionId").asText()).isEqualTo(chemistry.path("id").asText());
        assertThat(found.path("subjectCode").asText()).isEqualTo("HOA");
        assertThat(found.path("required").asInt()).isEqualTo(1);
        assertThat(found.path("available").asInt()).isZero();
        assertThat(codes(result)).doesNotContain(SET_CODE);
        publishRefused(base, result);
    }

    @Test
    void hsaHistoryAllowsZero() throws Exception {
        String base = create("HSA", "SUBJECT", null, "SU", null);
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode history = section(edited, "SU");
        if (history.path("blocks").toString().contains("\"SHORT_ANSWER\"")) dropShortAnswer(history);
        assertThat(history.path("blocks").toString()).doesNotContain("\"SHORT_ANSWER\"");
        save(base, before, edited);

        JsonNode result = check(base);
        assertThat(codes(result)).doesNotContain(SHORT_CODE, SET_CODE);
    }

    // ---- AC-3 ---------------------------------------------------------------------------------------------

    /** Only the stated quantity changes; the other rule must keep its baseline answer (absent). */
    @ParameterizedTest(name = "{0} sets={1} size={2} shortAnswer={3} -> set rule {4}, short rule {5}")
    @CsvSource({
            "QDA, 0, 3, 1, true,  false",
            "QDA, 1, 3, 1, false, false",
            "QDA, 2, 3, 1, false, false",
            "QDA, 3, 3, 1, true,  false",
            "QDA, 1, 2, 1, true,  false",
            "QDA, 1, 4, 1, true,  false",
            "QDA, 1, 3, 0, false, true",
            "HSA, 0, 3, 1, true,  false",
            "HSA, 1, 3, 1, false, false",
            "HSA, 2, 3, 1, false, false",
            "HSA, 3, 3, 1, true,  false",
            "HSA, 1, 2, 1, true,  false",
            "HSA, 1, 4, 1, true,  false",
            "HSA, 1, 3, 0, false, true"})
    void isolatedBounds(String kind, int sets, int size, int shortAnswers, boolean expectSet, boolean expectShort)
            throws Exception {
        String base = create(kind, "SUBJECT", null, "LY", null);
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode physics = section(edited, "LY");
        if (sets != 1 || size != 3) setShape(physics, sets, size);
        if (shortAnswers == 0) dropShortAnswer(physics);
        save(base, before, edited);

        JsonNode result = check(base);
        String sectionId = physics.path("id").asText();
        assertThat(issues(result).stream().filter(item -> SET_CODE.equals(item.path("code").asText()))
                .map(item -> item.path("sectionId").asText() + "/" + item.path("subjectCode").asText()))
                .isEqualTo(expectSet ? java.util.List.of(sectionId + "/LY") : java.util.List.of());
        assertThat(issues(result).stream().filter(item -> SHORT_CODE.equals(item.path("code").asText()))
                .map(item -> item.path("sectionId").asText() + "/" + item.path("subjectCode").asText()))
                .isEqualTo(expectShort ? java.util.List.of(sectionId + "/LY") : java.util.List.of());
    }

    // ---- AC-4 ---------------------------------------------------------------------------------------------

    @Test
    void otherKindsUnaffected() throws Exception {
        // Three sets and no short answer in every pair that is not HSA|QDA x science: no new code.
        for (String kind : new String[] {"BCA", "HSCA", "THPTQG", "VACT", "VSAT", "SP2E", "SPT"}) {
            UUID exam = sqlExam(kind, "[" + sectionJson("s-ly", "LY", 3, 3, 0) + "," + sectionJson("s-hoa", "HOA", 3, 3, 0) + "]");
            assertThat(codes(check("/api/v1/admin/exams/" + exam))).as(kind).doesNotContain(SET_CODE, SHORT_CODE);
        }
        // Inside HSA, QDA: a subject outside the table, and a subject the admin adds later.
        for (String kind : new String[] {"HSA", "QDA"}) {
            UUID exam = sqlExam(kind, "[" + sectionJson("s-anh", "ANH", 3, 3, 0) + "," + sectionJson("s-new", "TIN", 3, 4, 0) + "]");
            assertThat(codes(check("/api/v1/admin/exams/" + exam))).as(kind).doesNotContain(SET_CODE, SHORT_CODE);
            UUID same = sqlExam(kind, "[" + sectionJson("s-ly", "LY", 3, 3, 0) + "]");
            assertThat(codes(check("/api/v1/admin/exams/" + same))).as(kind + " control").contains(SET_CODE, SHORT_CODE);
        }
    }

    // ---- AC-5 ---------------------------------------------------------------------------------------------

    @Test
    void reviewExamsExcluded() throws Exception {
        // Review QDA Sinh as the BA describes it: 7 single + 1 set, no short answer. No new code.
        String review = create("QDA", "SUBJECT", null, "SINH", "REVIEW");
        JsonNode reviewBefore = structure(review);
        ObjectNode reviewDoc = reviewBefore.deepCopy();
        ObjectNode reviewSection = (ObjectNode) reviewDoc.at("/parts/0/sections/0");
        reviewSection.put("subjectCode", "SINH").put("questionCount", 10);
        reviewSection.set("blocks", baBlocks());
        save(review, reviewBefore, reviewDoc);
        assertThat(codes(check(review))).doesNotContain(SET_CODE, SHORT_CODE);

        // Review HSA with multiple choice only: no new code.
        String hsaReview = create("HSA", "SUBJECT", null, "HOA", "REVIEW");
        JsonNode hsaBefore = structure(hsaReview);
        ObjectNode hsaDoc = hsaBefore.deepCopy();
        ObjectNode hsaSection = (ObjectNode) hsaDoc.at("/parts/0/sections/0");
        hsaSection.put("subjectCode", "HOA").put("questionCount", 10);
        ArrayNode single = mapper.createArrayNode();
        single.addObject().put("id", "r-single").put("questionType", "SINGLE_CHOICE").put("questionCount", 10);
        hsaSection.set("blocks", single);
        save(hsaReview, hsaBefore, hsaDoc);
        assertThat(codes(check(hsaReview))).doesNotContain(SET_CODE, SHORT_CODE);

        // The same configuration in a standard QDA Sinh exam: the limit applies, through the API too.
        String standard = create("QDA", "SUBJECT", null, "SINH", null);
        JsonNode standardBefore = structure(standard);
        ObjectNode standardDoc = standardBefore.deepCopy();
        ObjectNode standardSection = section(standardDoc, "SINH");
        standardSection.put("questionCount", 10);
        standardSection.set("blocks", baBlocks());
        save(standard, standardBefore, standardDoc);
        JsonNode result = check(standard);
        assertThat(codes(result)).contains(SHORT_CODE).doesNotContain(SET_CODE);
        assertThat(issue(result, SHORT_CODE).path("sectionId").asText()).isEqualTo(standardSection.path("id").asText());
        assertThat(issue(result, SHORT_CODE).path("subjectCode").asText()).isEqualTo("SINH");
    }

    // ---- AC-9 (green first: the 2B rules stay as they are) ------------------------------------------------

    @Test
    void declaredDimensionTotals() throws Exception {
        // QDA Lý, section of 25.
        assertThat(codes(check(matrixExam("QDA", "LY", 25, "[{\"code\":\"NB\",\"value\":10},{\"code\":\"TH\",\"value\":10},{\"code\":\"VD\",\"value\":5}]")))).doesNotContain("SUBJECT_MATRIX_TOTAL_MISMATCH");
        JsonNode qdaBad = check(matrixExam("QDA", "LY", 25, "[{\"code\":\"NB\",\"value\":10},{\"code\":\"TH\",\"value\":5},{\"code\":\"VD\",\"value\":5}]"));
        JsonNode qdaMismatch = issue(qdaBad, "SUBJECT_MATRIX_TOTAL_MISMATCH");
        assertThat(qdaMismatch.path("sectionId").asText()).isEqualTo("s");
        assertThat(qdaMismatch.path("dimension").asText()).isEqualTo("DIFFICULTY");
        assertThat(qdaMismatch.path("required").asInt()).isEqualTo(25);
        assertThat(qdaMismatch.path("available").asInt()).isEqualTo(20);
        // THPTQG Lý Phần I, 18 questions.
        assertThat(codes(check(matrixExam("THPTQG", "LY", 18, "[{\"code\":\"NB\",\"value\":8},{\"code\":\"TH\",\"value\":6},{\"code\":\"VD\",\"value\":4}]")))).doesNotContain("SUBJECT_MATRIX_TOTAL_MISMATCH");
        JsonNode thptBad = check(matrixExam("THPTQG", "LY", 18, "[{\"code\":\"NB\",\"value\":8},{\"code\":\"TH\",\"value\":6},{\"code\":\"VD\",\"value\":2}]"));
        JsonNode thptMismatch = issue(thptBad, "SUBJECT_MATRIX_TOTAL_MISMATCH");
        assertThat(thptMismatch.path("sectionId").asText()).isEqualTo("s");
        assertThat(thptMismatch.path("dimension").asText()).isEqualTo("DIFFICULTY");
        assertThat(thptMismatch.path("required").asInt()).isEqualTo(18);
        assertThat(thptMismatch.path("available").asInt()).isEqualTo(16);
        // An empty dimension is an empty array and no error; a zero cell is valid.
        JsonNode zero = check(matrixExam("QDA", "LY", 25, "[{\"code\":\"NB\",\"value\":0},{\"code\":\"TH\",\"value\":10},{\"code\":\"VD\",\"value\":15}]"));
        assertThat(codes(zero)).doesNotContain("SUBJECT_MATRIX_TOTAL_MISMATCH", "MATRIX_NEGATIVE", "MATRIX_NON_INTEGER");
        // A total that differs at a group is reported at the group.
        UUID group = sqlExam("QDA", "[{\"id\":\"p\",\"questionCount\":25,\"sections\":[{\"id\":\"s\",\"subjectCode\":\"LY\",\"questionCount\":25,"
                + "\"blocks\":[{\"id\":\"b\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":25,\"matrix\":{\"mode\":\"SEPARATE\",\"unit\":\"COUNT\","
                + "\"byGrade\":[],\"byDifficulty\":[{\"code\":\"NB\",\"value\":10},{\"code\":\"TH\",\"value\":10},{\"code\":\"VD\",\"value\":4}]}}]}]}]");
        JsonNode groupResult = check("/api/v1/admin/exams/" + group);
        JsonNode groupIssue = issue(groupResult, "MATRIX_TOTAL_MISMATCH");
        assertThat(groupIssue.path("blockId").asText()).isEqualTo("b");
        assertThat(groupIssue.path("dimension").asText()).isEqualTo("DIFFICULTY");
    }

    // ---- answer-type quota of the short-answer minimum: same semantics as the solver (R1-1..R1-3) ----------

    /**
     * The template's QDA Lý section (25 questions: 21 single choice, one set of 3, one short answer) with an
     * answer-type quota on top. The check resolves the quota with the solver's own conversion: COUNT as declared
     * (a missing unit is COUNT), PERCENT by largest remainder with ties in declaration order.
     */
    @ParameterizedTest(name = "{0} -> short answers {1}")
    @CsvSource(delimiter = '|', value = {
            // reviewer R1-1: 12% / 3% / 85% of 25 resolves to 3 / 1 / 21 (the set row counts the set children)
            "PERCENT:QUESTION_SET=12,SHORT_ANSWER=3,SINGLE_CHOICE=85 | 1",
            // R1-3: no unit is COUNT, a value of 1 is one question and not 1 percent
            "NONE:QUESTION_SET=3,SHORT_ANSWER=1,SINGLE_CHOICE=21 | 1",
            "COUNT:QUESTION_SET=3,SHORT_ANSWER=1,SINGLE_CHOICE=21 | 1",
            // R1-2: 3 % of 25 is 0.75, the largest remainder takes the missing question (a floor would give 0)
            "PERCENT:SINGLE_CHOICE=97,SHORT_ANSWER=3 | 1",
            // exact tie at .5: the earlier declared row takes the missing question
            "PERCENT:SHORT_ANSWER=2,SINGLE_CHOICE=98 | 1",
            "PERCENT:SINGLE_CHOICE=98,SHORT_ANSWER=2 | 0",
            // a genuine zero apportionment
            "PERCENT:SINGLE_CHOICE=99,SHORT_ANSWER=1 | 0",
            "COUNT:SINGLE_CHOICE=25,SHORT_ANSWER=0 | 0"})
    void shortAnswerMinimumFollowsTheSolversQuotaResolution(String quota, int resolvedShortAnswers) throws Exception {
        String base = create("QDA", "SUBJECT", null, "LY", null);
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode physics = section(edited, "LY");
        assertThat(physics.path("questionCount").asInt()).isEqualTo(25);
        String[] unitAndRows = quota.split(":");
        ObjectNode quotas = physics.putObject("answerQuotas");
        if (!"NONE".equals(unitAndRows[0])) quotas.put("unit", unitAndRows[0]);
        ArrayNode items = quotas.putArray("items");
        for (String row : unitAndRows[1].split(",")) {
            String[] pair = row.split("=");
            items.addObject().put("answerType", pair[0]).put("value", Integer.parseInt(pair[1]));
        }
        save(base, before, edited);

        JsonNode result = check(base);
        if (resolvedShortAnswers >= 1) {
            assertThat(codes(result)).as("check issues").doesNotContain(SHORT_CODE, SET_CODE);
        } else {
            JsonNode found = issue(result, SHORT_CODE);
            assertThat(found.path("sectionId").asText()).isEqualTo(physics.path("id").asText());
            assertThat(found.path("available").asInt()).isZero();
            assertThat(codes(result)).doesNotContain(SET_CODE);
        }
    }

    // ---- fixtures -----------------------------------------------------------------------------------------

    private String matrixExam(String kind, String subject, int count, String byDifficulty) {
        UUID exam = sqlExam(kind, "[{\"id\":\"p\",\"questionCount\":" + count + ",\"sections\":[{\"id\":\"s\",\"subjectCode\":\"" + subject
                + "\",\"questionCount\":" + count + ",\"matrix\":{\"mode\":\"SEPARATE\",\"unit\":\"COUNT\",\"byGrade\":[],\"byDifficulty\":"
                + byDifficulty + "},\"blocks\":[{\"id\":\"b\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":" + count + "}]}]}]");
        return "/api/v1/admin/exams/" + exam;
    }

    /** One section of 25 questions: the set block (if any), the short-answer block (if any), single choice for the rest. */
    private static String sectionJson(String id, String subject, int sets, int size, int shortAnswers) {
        int single = 25 - sets * size - shortAnswers;
        StringBuilder blocks = new StringBuilder("{\"id\":\"" + id + "-single\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":" + single + "}");
        if (sets > 0) {
            blocks.append(",{\"id\":\"").append(id).append("-set\",\"questionType\":\"QUESTION_SET\",\"questionCount\":").append(sets * size)
                    .append(",\"setCount\":").append(sets).append(",\"setSize\":").append(size).append(",\"childAnswerType\":\"SINGLE_CHOICE\"}");
        }
        if (shortAnswers > 0) {
            blocks.append(",{\"id\":\"").append(id).append("-short\",\"questionType\":\"SHORT_ANSWER\",\"questionCount\":").append(shortAnswers).append('}');
        }
        return "{\"id\":\"" + id + "\",\"subjectCode\":\"" + subject + "\",\"questionCount\":25,\"blocks\":[" + blocks + "]}";
    }

    private UUID sqlExam(String kind, String parts) {
        String document = parts.startsWith("[{\"id\":\"p\"") ? parts : "[{\"id\":\"p\",\"questionCount\":50,\"sections\":" + parts + "}]";
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,kind_item_id,scope,timing,question_count,duration_minutes) "
                + "VALUES (?,?,?,'Limits',(SELECT i.id FROM catalog.catalog_item i JOIN catalog.catalog_group g ON g.id = i.group_id "
                + "WHERE g.code = 'EXAM' AND i.code = ?),'FULL','SHARED',50,60)", id, id.toString(), id.toString(), kind);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,?)",
                id, "{\"parts\":" + document + "}", id.toString());
        return id;
    }

    private ArrayNode baBlocks() {
        ArrayNode blocks = mapper.createArrayNode();
        blocks.addObject().put("id", "ba-single").put("questionType", "SINGLE_CHOICE").put("questionCount", 7);
        blocks.addObject().put("id", "ba-set").put("questionType", "QUESTION_SET").put("questionCount", 3)
                .put("setCount", 1).put("setSize", 3).put("childAnswerType", "SINGLE_CHOICE");
        return blocks;
    }

    /** The publish request of every path of the check is refused because the check is not VALID. */
    private void publishRefused(String base, JsonNode check) throws Exception {
        JsonNode exam = json(get(base).with(asUser(Permissions.EXAM_VIEW)), 200);
        ObjectNode body = mapper.createObjectNode().put("expectedRevision", exam.path("revision").asLong());
        ArrayNode pathIds = body.putArray("pathIds");
        check.path("paths").forEach(path -> pathIds.add(path.path("pathId").asText()));
        JsonNode refused = json(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422);
        assertThat(refused.path("code").asText()).isEqualTo("PATH_NOT_VALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?::uuid",
                Long.class, base.substring(base.lastIndexOf('/') + 1))).isZero();
    }
}
