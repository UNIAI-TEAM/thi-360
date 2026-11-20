package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * CR-4 (UNI-2762, FTM R-12): the set count, set size and short-answer minimum of the science subjects of HSA and
 * QDA at the check step. The limits never block a draft; they block publishing, and only standard exams.
 */
class ExamCheckSubjectLimitsIT extends AbstractSubjectLimitsIT {
    @Autowired private EntityManager entityManager;

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
        if (quota.equals("PERCENT:QUESTION_SET=12,SHORT_ANSWER=3,SINGLE_CHOICE=85")) {
            assertThat(result.path("status").asText()).as("3/1/21 check: %s", result.path("groups")).isEqualTo("VALID");
            UUID id = UUID.fromString(base.substring(base.lastIndexOf('/') + 1));
            ExamListingFixture.fill(jdbc, id);
            JsonNode exam = json(get(base).with(asUser(Permissions.EXAM_VIEW)), 200);
            ObjectNode body = mapper.createObjectNode().put("expectedRevision", exam.path("revision").asLong());
            ArrayNode paths = body.putArray("pathIds");
            result.path("paths").forEach(path -> paths.add(path.path("pathId").asText()));
            json(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 200);
        }
    }

    // ---- undetermined size, option blocks (R1-16, R1-13) ------------------------------------------------------

    /**
     * One fixed set of 3 and one flexible set (no count, no size): the total of sets is in range but the size of the
     * flexible one is unknown, so the issue says what is required and leaves "available" empty instead of echoing 3.
     */
    @Test
    void flexibleSetLeavesTheAvailableSizeEmpty() throws Exception {
        String base = create("QDA", "SUBJECT", null, "LY", null);
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode physics = section(edited, "LY");
        ObjectNode single = blockOfType(physics, "SINGLE_CHOICE");
        single.put("questionCount", single.path("questionCount").asInt() - 3);
        ((ArrayNode) physics.path("blocks")).addObject().put("id", "flexible-set").put("questionType", "QUESTION_SET")
                .put("questionCount", 3).put("childAnswerType", "SINGLE_CHOICE");
        save(base, before, edited);

        JsonNode found = issue(check(base), SET_CODE);
        assertThat(found.path("sectionId").asText()).isEqualTo(physics.path("id").asText());
        assertThat(found.path("required").asInt()).isEqualTo(3);
        assertThat(found.path("available").isNull()).as("available: %s", found).isTrue();
    }

    /**
     * R1-13: R-12 reads {@code sections}, {@code subjectPool} and {@code options[].sections}. The blocks a SUBJECT
     * option holds directly are none of them (no stored subject code), so a Lý option with only single choice raises
     * neither code, where the same blocks in a section of Lý would.
     */
    @Test
    void blocksOfASubjectOptionAreNotASubjectSection() throws Exception {
        String options = "[{\"id\":\"p\",\"questionCount\":10,\"participation\":{\"mode\":\"CHOICE\",\"choose\":1},\"options\":["
                + "{\"id\":\"ly-opt\",\"kind\":\"SUBJECT\",\"code\":\"LY\",\"blocks\":[{\"id\":\"b1\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":10}]},"
                + "{\"id\":\"anh-opt\",\"kind\":\"SUBJECT\",\"code\":\"ANH\",\"blocks\":[{\"id\":\"b2\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":10}]}]}]";
        for (String kind : new String[] {"HSA", "QDA"}) {
            JsonNode result = check("/api/v1/admin/exams/" + sqlExam(kind, options));
            assertThat(codes(result)).as(kind + " option blocks").doesNotContain(SET_CODE, SHORT_CODE);
            // Control: the same single-choice blocks as a section of Lý break the rules.
            UUID section = sqlExam(kind, "[{\"id\":\"s-ly\",\"subjectCode\":\"LY\",\"questionCount\":10,\"blocks\":"
                    + "[{\"id\":\"b1\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":10}]}]");
            assertThat(codes(check("/api/v1/admin/exams/" + section))).as(kind + " section control").contains(SET_CODE, SHORT_CODE);
        }
    }

    /**
     * R2-1 + AD-85: a stored section inside an option without direct blocks is checked by the R-12 rules when its id
     * is distinct ("ly-opt-sec"). Under the conservative rule a stored "{option}:blocks" id inside its own option
     * names the holder of the option's direct blocks — the draft is ambiguous, so the located structure error on the
     * option replaces the per-section verdicts and the path cannot publish either.
     */
    @Test
    void aStoredOptionSectionWithTheSyntheticLookingIdIsStillChecked() throws Exception {
        for (String kind : new String[] {"HSA", "QDA"}) for (String id : new String[] {"ly-opt:blocks", "ly-opt-sec"}) {
            String base = create(kind, "SUBJECT", null, "LY", null);
            JsonNode before = structure(base);
            ObjectNode edited = before.deepCopy();
            // The template's own Lý section (so points, totals and sources are in order), moved into a SUBJECT option
            // that has no direct blocks, with both R-12 rules broken: three sets and no short answer.
            ObjectNode part = (ObjectNode) edited.at("/parts/0");
            ObjectNode stored = section(edited, "LY").deepCopy();
            stored.put("id", id);
            setShape(stored, 3, 3);
            dropShortAnswer(stored);
            part.putArray("sections");
            part.putObject("participation").put("mode", "CHOICE").put("choose", 1);
            part.putArray("options").addObject().put("id", "ly-opt").put("kind", "SUBJECT").put("code", "LY")
                    .putArray("sections").add(stored);
            save(base, before, edited);

            JsonNode result = check(base);
            String as = kind + " " + id;
            assertThat(result.path("status").asText()).as(as).isEqualTo("INVALID");
            JsonNode lyPath = java.util.stream.StreamSupport.stream(result.path("paths").spliterator(), false)
                    .filter(path -> "ly-opt".equals(path.path("pathId").asText())).findFirst().orElseThrow();
            assertThat(lyPath.path("result").asText()).as(as).isEqualTo("INVALID");
            java.util.Set<String> errors = new java.util.TreeSet<>();
            for (JsonNode list : lyPath.findValues("issues")) for (JsonNode item : list) {
                if ("ERROR".equals(item.path("severity").asText())) errors.add(item.path("code").asText());
            }
            if ("ly-opt:blocks".equals(id)) {
                // AD-85: the stored id names the option's direct-blocks holder: the ambiguity error is located on the
                // option, and the ambiguous section is not scored against the per-subject limits at all.
                assertThat(errors).as(as + " errors of the path").contains("OPTION_EMPTY")
                        .doesNotContain(SET_CODE, SHORT_CODE);
                JsonNode located = issues(result).stream()
                        .filter(item -> "OPTION_EMPTY".equals(item.path("code").asText())).findFirst().orElseThrow();
                assertThat(located.path("optionId").asText()).as(as).isEqualTo("ly-opt");
            } else {
                // Otherwise valid: the two R-12 errors are the only errors of the path.
                JsonNode set = issue(result, SET_CODE);
                assertThat(set.path("sectionId").asText()).as(as).isEqualTo(id);
                assertThat(set.path("subjectCode").asText()).as(as).isEqualTo("LY");
                assertThat(issue(result, SHORT_CODE).path("sectionId").asText()).as(as).isEqualTo(id);
                assertThat(errors).as(as + " errors of the path").containsExactlyInAnyOrder(SET_CODE, SHORT_CODE);
            }
            publishRefused(base, result);
        }
    }

    /**
     * R2-1: a stored section may not take the id of the holder the checker and the solver build for the direct blocks of
     * the same option, or two sections of one path would share an id: PUT refuses the structure.
     */
    @Test
    void aStoredSectionCannotTakeTheIdOfTheDirectBlocksHolderOfItsOption() throws Exception {
        String base = create("HSA", "SUBJECT", null, "LY", null);
        JsonNode before = structure(base);
        ObjectNode body = mapper.createObjectNode().put("expectedRevision", before.path("revision").asLong());
        body.set("parts", mapper.readTree("[{\"id\":\"p\",\"name\":\"P\",\"questionCount\":10,"
                + "\"participation\":{\"mode\":\"CHOICE\",\"choose\":1},\"options\":["
                + "{\"id\":\"ly-opt\",\"kind\":\"SUBJECT\",\"code\":\"LY\",\"blocks\":[{\"id\":\"b1\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":10}],"
                + "\"sections\":[{\"id\":\"ly-opt:blocks\",\"subjectCode\":\"LY\",\"questionCount\":10,"
                + "\"blocks\":[{\"id\":\"b2\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":10}]}]}]}]"));
        JsonNode refused = json(put(base + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 422);
        assertThat(refused.path("code").asText()).isEqualTo("EXAM_STRUCTURE_INVALID");
        assertThat(structure(base).path("revision").asLong()).isEqualTo(before.path("revision").asLong());
    }

    /**
     * R3-2: PUT refuses the collision, but a draft saved by older code may hold it: an option with direct blocks AND a
     * real section named "{option}:blocks" (distinct block ids, matching totals). The checker must not let the holder
     * of the direct blocks stand in for that section, or its R-12 errors vanish and the path publishes. The check
     * reports a structure error located on that option, so its path cannot publish; the independent path of the
     * other option is not restricted.
     */
    @Test
    void aSavedCollisionWithTheDirectBlocksHolderMakesThatOptionInvalid() throws Exception {
        for (String kind : new String[] {"HSA", "QDA"}) {
            // Saved with the ids apart, the R-12 errors of the section are found: only the Lý option with it is invalid.
            String apartBase = optionsWithDirectBlocks(kind);
            JsonNode apart = check(apartBase);
            assertThat(pathResults(apart)).as(kind + " saved apart: %s", apart.path("paths"))
                    .containsEntry("ly-opt", "INVALID").containsEntry("ly2-opt", "VALID");
            assertThat(codes(apart)).as(kind + " saved apart").contains(SET_CODE, SHORT_CODE);

            // What an older PUT accepted, written straight to the draft: the section takes the id of the holder of the
            // direct blocks. The first check of that draft is the one under test.
            String base = optionsWithDirectBlocks(kind);
            String examId = base.substring(base.lastIndexOf('/') + 1);
            // The write of the PUT is still pending in the persistence context: let it reach the table first, or it
            // would overwrite the raw update below when the check flushes.
            entityManager.flush();
            entityManager.clear();
            assertThat(jdbc.update("UPDATE exam.exam_structure SET parts = replace(parts::text, '\"ly-opt-sec\"', '\"ly-opt:blocks\"')::jsonb "
                    + "WHERE exam_id = ?::uuid", examId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?::uuid", String.class, examId))
                    .as("the stored draft holds the collision").contains("\"ly-opt:blocks\"").doesNotContain("\"ly-opt-sec\"");
            String as = kind + " collision";
            JsonNode result = check(base);
            assertThat(pathResults(result)).as(as + ": %s", result.path("paths"))
                    .containsEntry("ly-opt", "INVALID").containsEntry("ly2-opt", "VALID");
            JsonNode located = null;
            for (JsonNode item : issues(result)) {
                if ("OPTION_EMPTY".equals(item.path("code").asText()) && "ly-opt".equals(item.path("optionId").asText())) located = item;
            }
            assertThat(located).as(as + ": structure error on the option in %s", issues(result)).isNotNull();
            assertThat(located.path("severity").asText()).isEqualTo("ERROR");
            assertThat(located.path("partId").asText()).isNotBlank();
            assertThat(result.path("status").asText()).as(as).isEqualTo("INVALID");
            ObjectNode refusal = mapper.createObjectNode().put("expectedRevision",
                    json(get(base).with(asUser(Permissions.EXAM_VIEW)), 200).path("revision").asLong());
            refusal.putArray("pathIds").add("ly-opt");
            JsonNode refused = json(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(refusal.toString()), 422);
            assertThat(refused.path("code").asText()).as(as).isEqualTo("PATH_NOT_VALID");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?::uuid",
                    Long.class, examId)).as(as).isZero();
        }
    }

    /**
     * R4-1 (AD-85 conservative R1-13): a draft saved by older code gives the real section of "b-opt" the id of the
     * holder of another option's direct blocks ("a-opt:blocks"). The draft is ambiguous: both owning options get a
     * located structure error, so the aliased option's path is never certified on the other option's cached proof.
     * The non-colliding sibling stays VALID.
     */
    @Test
    void aCrossOptionCollisionKeepsTheAliasedOptionUnpublished() throws Exception {
        for (String kind : new String[] {"HSA", "QDA"}) {
            String base = optionsWithAliasedSection(kind);
            String examId = base.substring(base.lastIndexOf('/') + 1);
            // The first check warms "a-opt:blocks" as the valid holder of the direct blocks.
            JsonNode warm = check(base);
            assertThat(warm.path("status").asText()).as(kind + " warm: %s", warm.path("paths")).isEqualTo("VALID");
            entityManager.flush();
            entityManager.clear();

            // The legacy write an old PUT allowed: b-opt's real section takes the holder id, with distinct groups and
            // a required topic quota no candidate meets (solving the real section can only fail).
            ObjectNode edited = (ObjectNode) mapper.readTree(jdbc.queryForObject(
                    "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?::uuid", String.class, examId));
            ObjectNode aliased = null;
            for (JsonNode p : edited.path("parts")) for (JsonNode o : p.path("options"))
                for (JsonNode s : o.path("sections")) if ("b-sec".equals(s.path("id").asText())) aliased = (ObjectNode) s;
            assertThat(aliased).isNotNull();
            aliased.put("id", "a-opt:blocks");
            ObjectNode quota = aliased.putArray("topicQuotas").addObject();
            quota.put("id", "b-topic").put("count", 9).put("basis", "QUESTION").put("enforcement", "REQUIRED");
            quota.putArray("nodeIds").add("00000000-0000-7000-8200-000000001201");
            quota.putArray("gradeCodes").add("NO_SUCH_GRADE");
            assertThat(jdbc.update("UPDATE exam.exam_structure SET parts = ?::jsonb WHERE exam_id = ?::uuid",
                    mapper.writeValueAsString(edited), examId)).isEqualTo(1);

            String as = kind + " cross-option collision";
            JsonNode result = check(base);
            assertThat(pathResults(result)).as(as + ": %s", result.path("paths"))
                    .containsEntry("a-opt", "INVALID").containsEntry("b-opt", "INVALID")
                    .containsEntry("c-opt", "VALID");
            java.util.Set<String> flagged = new java.util.TreeSet<>();
            for (JsonNode item : issues(result)) {
                if ("OPTION_EMPTY".equals(item.path("code").asText())) flagged.add(item.path("optionId").asText());
            }
            assertThat(flagged).as(as + ": ambiguous options in %s", issues(result)).contains("a-opt", "b-opt");
            assertThat(result.path("status").asText()).as(as).isEqualTo("INVALID");
            ObjectNode refusal = mapper.createObjectNode().put("expectedRevision",
                    json(get(base).with(asUser(Permissions.EXAM_VIEW)), 200).path("revision").asLong());
            refusal.putArray("pathIds").add("b-opt");
            JsonNode refused = json(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(refusal.toString()), 422);
            assertThat(refused.path("code").asText()).as(as).isEqualTo("PATH_NOT_VALID");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?::uuid",
                    Long.class, examId)).as(as).isZero();
        }
    }

    /**
     * R4-1 (AD-85 conservative R1-13): a unit whose section ids include an ambiguous "{option}:blocks" id never reuses
     * a cached proof and is never saved again. A first check warms a VALID unit for ("LY", ["a-opt:blocks","c-sec"])
     * while no option named "a-opt" exists; the legacy edit then adds "a-opt" with direct blocks of exactly the stored
     * content and a required topic quota the source cannot fill. The next check must solve the real stored section:
     * the topic failure appears and the path cannot publish.
     */
    @Test
    void aLegacyCollisionNeverBorrowsACachedProof() throws Exception {
        UUID ly = UUID.fromString("00000000-0000-7000-8200-000000000204");
        UUID id = sqlExam("HSA", """
                [{"id":"p","questionCount":18,"participation":{"mode":"CHOICE","choose":1},"options":[
                  {"id":"c-opt","kind":"SUBJECT","code":"LY","sections":[
                    {"id":"c-sec","subjectCode":"LY","questionCount":9,"blocks":[
                      {"id":"c-single","questionType":"SINGLE_CHOICE","questionCount":5},
                      {"id":"c-set","questionType":"QUESTION_SET","questionCount":3,"setCount":1,"setSize":3,
                       "childAnswerType":"SINGLE_CHOICE"},
                      {"id":"c-fill","questionType":"SHORT_ANSWER","questionCount":1}]}]}],
                 "sections":[{"id":"a-opt:blocks","subjectCode":"LY","questionCount":9,"blocks":[
                   {"id":"a-single","questionType":"SINGLE_CHOICE","questionCount":5},
                   {"id":"a-set","questionType":"QUESTION_SET","questionCount":3,"setCount":1,"setSize":3,
                    "childAnswerType":"SINGLE_CHOICE"},
                   {"id":"a-fill","questionType":"SHORT_ANSWER","questionCount":1}]}]}]
                """);
        for (String block : new String[] {"a-single", "a-set", "a-fill"}) blockRef(id, block, "a-opt:blocks", ly);
        for (String block : new String[] {"c-single", "c-set", "c-fill"}) blockRef(id, block, "c-sec", ly);
        jdbc.update("UPDATE exam.exam SET question_count = 18 WHERE id = ?", id);
        String base = "/api/v1/admin/exams/" + id;

        JsonNode warm = check(base);
        assertThat(warm.path("status").asText()).as("warm: %s", warm.path("paths")).isEqualTo("VALID");
        java.sql.Timestamp warmedAt = jdbc.queryForObject(
                "SELECT checked_at FROM exam.exam_check_unit WHERE exam_id = ? AND unit_key = ?",
                java.sql.Timestamp.class, id, "[\"LY\",[\"a-opt:blocks\",\"c-sec\"]]");
        assertThat(jdbc.queryForObject("SELECT outcome FROM exam.exam_check_unit WHERE exam_id = ? AND unit_key = ?",
                String.class, id, "[\"LY\",[\"a-opt:blocks\",\"c-sec\"]]")).as("warm proof").isEqualTo("VALID");

        // Legacy write: option "a-opt" appears with direct blocks of exactly the stored section's shape, and the stored
        // section gains a required topic quota no candidate meets. Flush the check's writes first so they cannot
        // overwrite the raw update, then clear so the next check reads the stored draft.
        entityManager.flush();
        entityManager.clear();
        ObjectNode edited = (ObjectNode) mapper.readTree(jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_structure WHERE exam_id = ?::uuid", String.class, id));
        ObjectNode part = (ObjectNode) edited.path("parts").get(0);
        JsonNode storedBlocks = null;
        for (JsonNode section : part.path("sections")) {
            if ("a-opt:blocks".equals(section.path("id").asText())) {
                storedBlocks = section.path("blocks");
                ObjectNode quota = ((ObjectNode) section).putArray("topicQuotas").addObject();
                quota.put("id", "a-topic").put("count", 9).put("basis", "QUESTION").put("enforcement", "REQUIRED");
                quota.putArray("nodeIds").add("00000000-0000-7000-8200-000000001201");
                quota.putArray("gradeCodes").add("NO_SUCH_GRADE");
            }
        }
        // The holder the checker builds for these blocks serializes exactly like the stored section (minus the quota),
        // so a fingerprint that ignores the stored section still matches the warm unit row.
        ((ArrayNode) part.path("options")).addObject().put("id", "a-opt").put("kind", "SUBJECT").put("code", "LY")
                .putArray("sections");
        ((ObjectNode) part.path("options").get(part.path("options").size() - 1))
                .set("blocks", storedBlocks.deepCopy());
        entityManager.clear();
        assertThat(jdbc.update("UPDATE exam.exam_structure SET parts = ?::jsonb WHERE exam_id = ?::uuid",
                mapper.writeValueAsString(edited), id)).isEqualTo(1);

        JsonNode result = check(base);
        // The ambiguous path is never certified: it is not VALID, its stored section was evaluated fresh (its own
        // blocks are reported), and the warm proof row is left untouched — never reused, never overwritten.
        assertThat(pathResults(result)).as("post-edit: %s", result.path("paths"))
                .containsEntry("a-opt", "INVALID");
        assertThat(pathResults(result).get("c-opt")).as("post-edit c-opt: %s", result.path("paths"))
                .isNotEqualTo("VALID");
        boolean evaluated = false;
        for (JsonNode path : result.path("paths")) {
            if (!"c-opt".equals(path.path("pathId").asText())) continue;
            for (JsonNode block : path.path("blocks")) {
                evaluated |= "a-opt:blocks".equals(block.path("sectionId").asText());
            }
        }
        assertThat(evaluated).as("the stored section itself was evaluated: %s", result.path("paths")).isTrue();
        assertThat(jdbc.queryForObject("SELECT checked_at FROM exam.exam_check_unit WHERE exam_id = ? AND unit_key = ?",
                java.sql.Timestamp.class, id, "[\"LY\",[\"a-opt:blocks\",\"c-sec\"]]"))
                .as("the ambiguous unit is never written again").isEqualTo(warmedAt);
        ObjectNode refusal = mapper.createObjectNode().put("expectedRevision",
                json(get(base).with(asUser(Permissions.EXAM_VIEW)), 200).path("revision").asLong());
        refusal.putArray("pathIds").add("c-opt");
        // An INVALID path is refused PATH_NOT_VALID (422); an UNCONFIRMED one CHECK_REQUIRED (409). Either way the
        // path that shares the ambiguous id never publishes.
        var response = mockMvc.perform(post(base + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(refusal.toString())).andReturn().getResponse();
        JsonNode refused = mapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isIn(409, 422);
        assertThat(refused.path("code").asText()).isIn("CHECK_REQUIRED", "PATH_NOT_VALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?::uuid",
                Long.class, id)).isZero();
    }

    private void blockRef(UUID exam, String blockId, String sectionId, UUID subject) {
        jdbc.update("INSERT INTO exam.exam_block_ref(id, exam_id, block_id, part_id, section_id, subject_item_id) "
                + "VALUES (?, ?, ?, 'p', ?, ?)", UUID.randomUUID(), exam, blockId, sectionId, subject);
    }

    /**
     * Three Lý options of one choice part, in the reviewer's order: "b-opt" first, then "a-opt" with nine single-choice
     * direct blocks (the "a-opt:blocks" holder) and no sections, then the sibling "c-opt". Every section is nine R-12
     * clean questions (one set of three, five singles, one short answer).
     */
    private String optionsWithAliasedSection(String kind) throws Exception {
        String base = create(kind, "SUBJECT", null, "LY", null);
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode template = section(edited, "LY");
        ObjectNode single = blockOfType(template, "SINGLE_CHOICE");
        ObjectNode set = blockOfType(template, "QUESTION_SET");
        ObjectNode fill = blockOfType(template, "SHORT_ANSWER");
        ObjectNode bSection = ownSection(template, "b-sec", 9, blockLike(set, "b-set", 3, 1),
                blockLike(single, "b-single", 5, null), blockLike(fill, "b-fill", 1, null));
        ObjectNode cSection = ownSection(template, "c-sec", 9, blockLike(set, "c-set", 3, 1),
                blockLike(single, "c-single", 5, null), blockLike(fill, "c-fill", 1, null));
        ObjectNode part = (ObjectNode) edited.at("/parts/0");
        part.put("questionCount", 9);
        if (part.has("questionCountOnPaper")) part.put("questionCountOnPaper", 9);
        if (part.has("questionCountRequired")) part.put("questionCountRequired", 9);
        part.remove("maxPoints");
        part.putArray("sections");
        part.putObject("participation").put("mode", "CHOICE").put("choose", 1);
        ArrayNode options = part.putArray("options");
        options.addObject().put("id", "b-opt").put("kind", "SUBJECT").put("code", "LY")
                .putArray("sections").add(bSection);
        options.addObject().put("id", "a-opt").put("kind", "SUBJECT").put("code", "LY")
                .putArray("blocks").add(blockLike(single, "a-direct", 9, null));
        options.addObject().put("id", "c-opt").put("kind", "SUBJECT").put("code", "LY")
                .putArray("sections").add(cSection);
        save(base, before, edited);
        entityManager.flush();
        entityManager.clear();
        jdbc.update("UPDATE exam.exam SET question_count = 9, max_score = NULL WHERE id = ?::uuid",
                base.substring(base.lastIndexOf('/') + 1));
        entityManager.clear();
        return base;
    }

    /**
     * Two Lý options, each with direct blocks of 9 single-choice questions and one stored section of 9. The section of
     * "ly-opt" is stored as "ly-opt-sec" with both R-12 rules broken (three sets, no short answer); the section of
     * "ly2-opt" has one set and one short answer. All ids are distinct; the questions of a path are 18 for the part,
     * the exam and the path, so that a section and the direct blocks of one option agree in size (9 = 9) and nothing
     * but the shared id can tell them apart.
     */
    private String optionsWithDirectBlocks(String kind) throws Exception {
        String base = create(kind, "SUBJECT", null, "LY", null);
        JsonNode before = structure(base);
        ObjectNode edited = before.deepCopy();
        ObjectNode template = section(edited, "LY");
        ObjectNode single = blockOfType(template, "SINGLE_CHOICE");
        ObjectNode set = blockOfType(template, "QUESTION_SET");
        ObjectNode fill = blockOfType(template, "SHORT_ANSWER");
        ObjectNode broken = ownSection(template, "ly-opt-sec", 9, blockLike(set, "ly-opt-sec-set", 9, 3));
        ObjectNode clean = ownSection(template, "ly2-sec", 9, blockLike(single, "ly2-sec-single", 5, null),
                blockLike(set, "ly2-sec-set", 3, 1), blockLike(fill, "ly2-sec-fill", 1, null));
        ObjectNode part = (ObjectNode) edited.at("/parts/0");
        part.put("questionCount", 18);
        if (part.has("questionCountOnPaper")) part.put("questionCountOnPaper", 18);
        if (part.has("questionCountRequired")) part.put("questionCountRequired", 18);
        part.remove("maxPoints");
        part.putArray("sections");
        part.putObject("participation").put("mode", "CHOICE").put("choose", 1);
        ArrayNode options = part.putArray("options");
        ObjectNode colliding = options.addObject().put("id", "ly-opt").put("kind", "SUBJECT").put("code", "LY");
        colliding.putArray("blocks").add(blockLike(single, "ly-opt-direct", 9, null));
        colliding.putArray("sections").add(broken);
        ObjectNode control = options.addObject().put("id", "ly2-opt").put("kind", "SUBJECT").put("code", "LY");
        control.putArray("blocks").add(blockLike(single, "ly2-opt-direct", 9, null));
        control.putArray("sections").add(clean);
        save(base, before, edited);
        // The exam holds the 18 questions and no fixed score; the pending PUT is written first, then the exam row.
        entityManager.flush();
        entityManager.clear();
        jdbc.update("UPDATE exam.exam SET question_count = 18, max_score = NULL WHERE id = ?::uuid", base.substring(base.lastIndexOf('/') + 1));
        entityManager.clear();
        return base;
    }

    /** A copy of the template section under a new id with the given blocks only (no matrix or answer quota of the template). */
    private static ObjectNode ownSection(ObjectNode template, String id, int questionCount, ObjectNode... blocks) {
        ObjectNode copy = template.deepCopy();
        copy.put("id", id).put("questionCount", questionCount);
        copy.remove("matrix");
        copy.remove("answerQuotas");
        ArrayNode array = copy.putArray("blocks");
        for (ObjectNode block : blocks) array.add(block);
        return copy;
    }

    /** A copy of a template block under a new id and count; {@code setCount} makes it that many sets of 3. */
    private static ObjectNode blockLike(ObjectNode like, String id, int questionCount, Integer setCount) {
        ObjectNode block = like.deepCopy();
        block.put("id", id).put("questionCount", questionCount);
        block.remove("matrix");
        if (setCount != null) block.put("setCount", setCount).put("setSize", 3);
        return block;
    }

    private static java.util.Map<String, String> pathResults(JsonNode result) {
        java.util.Map<String, String> outcome = new java.util.TreeMap<>();
        for (JsonNode path : result.path("paths")) outcome.put(path.path("pathId").asText(), path.path("result").asText());
        return outcome;
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
