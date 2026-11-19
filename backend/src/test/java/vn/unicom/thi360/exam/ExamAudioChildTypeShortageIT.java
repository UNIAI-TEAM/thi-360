package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.SolverInputBuilder;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-17 (UNI-2689): shortage evidence through the check API. Row numbers refer to plan 2026-11-18
 * section 2 "BE-17": 6 (count equals solver units), 7 (AUDIO_SHORTAGE), 9 (CHILD_CONDITION_SHORTAGE),
 * 11 (ITEM_SHAPE_SHORTAGE), 12 (POINTS_SHORTAGE). The generic-shortage assertions prove
 * {@code ExamCheckPathPresenter} presents AUDIO_SHORTAGE and POINTS_SHORTAGE in place of the plain
 * shortage of the same block (FU-BE16-PRESENTER).
 */
@Transactional
class ExamAudioChildTypeShortageIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final String SC4 = choice(4);
    private static final String SC5 = choice(5);
    private static final String TF4 = trueFalse(4);
    private static final String TF3 = trueFalse(3);
    private static final String MIXED_CONDITIONS = """
            "childConditions":[
              {"from":1,"to":2,"answerType":"TRUE_FALSE","itemShape":{"statements":4}},
              {"from":3,"to":8,"answerType":"SINGLE_CHOICE"}]""";

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired SolverInputBuilder builder;

    private record Subject(UUID id, String code) { }
    private record Child(String type, String body) { }

    private static String choice(int options) {
        var body = new StringBuilder("{\"type\":\"SINGLE_CHOICE\",\"options\":[");
        for (int i = 0; i < options; i++) {
            body.append(i == 0 ? "" : ",").append("{\"id\":\"o").append(i).append("\"")
                    .append(i == 0 ? ",\"correct\":true" : "").append("}");
        }
        return body.append("]}").toString();
    }

    /** A playable true/false question: every statement has an answer and the scoring method is set. */
    private static String trueFalse(int statements) {
        return "{\"type\":\"TRUE_FALSE\",\"scoringMethod\":\"PER_STATEMENT\",\"statements\":["
                + String.join(",", java.util.Collections.nCopies(statements, "{\"answer\":true}")) + "]}";
    }

    /** Last exam created by {@link #check}: the witness of its check run proves what was selected. */
    private UUID lastExam;

    private List<UUID> picksAt(String blockId) {
        return CheckWitnessPicks.byBlock(jdbc, mapper, lastExam).getOrDefault(blockId, List.of());
    }

    private Subject subject() {
        UUID id = UUID.randomUUID();
        String code = "AUDIO_" + id.toString().replace("-", "");
        jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,path) VALUES "
                + "(?,?,?,?,(SELECT coalesce(max(value),0)+1 FROM catalog.catalog_item WHERE group_id=?),'Audio',?)",
                id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return new Subject(id, code);
    }

    private UUID audio() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question_asset(id,kind,object_key,file_name,mime,size,sha256) "
                + "VALUES (?,'ATTACHMENT',?,?,'audio/mpeg',1,?)", id, "k/" + id, "f-" + id, new byte[32]);
        return id;
    }

    private static String snapshot(String body, UUID attachment) {
        return "{\"body\":" + body + ",\"attachmentIds\":" + (attachment == null ? "[]" : "[\"" + attachment + "\"]") + "}";
    }

    private UUID single(Subject subject, String body, int points) {
        return single(subject, body, points, false);
    }

    private UUID single(Subject subject, String body, int points, boolean withAudio) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,'SINGLE_CHOICE','READY',?,1,?)", id, "Q-" + id, subject.id(), points);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                id, snapshot(body, withAudio ? audio() : null));
        return id;
    }

    private UUID set(Subject subject, List<Child> children, boolean withAudio) {
        UUID root = UUID.randomUUID();
        List<UUID> ids = new ArrayList<>();
        var body = new StringBuilder("{\"type\":\"QUESTION_SET\",\"children\":[");
        for (int position = 0; position < children.size(); position++) {
            UUID child = UUID.randomUUID();
            ids.add(child);
            body.append(position == 0 ? "" : ",").append("{\"id\":\"").append(child).append("\",\"body\":")
                    .append(children.get(position).body()).append('}');
        }
        body.append("]}");
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,'QUESTION_SET','READY',?,1,1)", root, "S-" + root, subject.id());
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                root, snapshot(body.toString(), withAudio ? audio() : null));
        for (int position = 0; position < children.size(); position++) {
            UUID child = ids.get(position);
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,points) "
                    + "VALUES (?,?,?,'READY',?,?,1)", child, "C-" + child, children.get(position).type(), root, position);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                    child, "{\"body\":" + children.get(position).body() + "}");
        }
        return root;
    }

    private static List<Child> singles(int count) {
        var list = new ArrayList<Child>();
        for (int i = 0; i < count; i++) list.add(new Child("SINGLE_CHOICE", SC4));
        return list;
    }

    private static List<Child> mixed(String firstTwo) {
        var list = new ArrayList<Child>();
        for (int i = 0; i < 2; i++) list.add(new Child("TRUE_FALSE", firstTwo));
        list.addAll(singles(6));
        return list;
    }

    /** Row 7: 3 sets x 4 needed with audio; two audio sets and extra non-audio sets exist. */
    @Test
    void row7AudioShortageAtTheRowWithRequiredAndAvailable() throws Exception {
        var subject = subject();
        set(subject, singles(4), true);
        set(subject, singles(4), true);
        set(subject, singles(4), false);
        set(subject, singles(4), false);
        var result = check(subject, 12, "REQUIRED", """
                {"id":"listen","questionType":"QUESTION_SET","questionCount":12,"setCount":3,"setSize":4,
                 "childAnswerType":"SINGLE_CHOICE"}""");
        var issue = findIssue(result, "AUDIO_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(3);
        assertThat(issue.path("available").asLong()).isEqualTo(2);
        assertThat(issue.path("blockId").asText()).isEqualTo("listen");
        assertThat(issue.path("sectionId").asText()).isEqualTo("s");
        assertThat(issue.path("partId").asText()).isEqualTo("p");
        assertThat(issue.path("subjectCode").asText()).isEqualTo(subject.code());
        assertThat(issue.path("group").asText()).isEqualTo("SOURCE");
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        // Presenter: the specific shortage replaces the plain one of the same block.
        assertThat(codes(result)).doesNotContain("SET_SHORTAGE", "SOURCE_SHORTAGE", "CELL_SHORTAGE");
    }

    /** Row 5 / 7 negative: enough audio sets means no audio shortage; FORBIDDEN is the mirror image. */
    @Test
    void enoughAudioSetsReportNoAudioShortage() throws Exception {
        var subject = subject();
        for (int i = 0; i < 3; i++) set(subject, singles(4), true);
        var result = check(subject, 12, "REQUIRED", """
                {"id":"listen","questionType":"QUESTION_SET","questionCount":12,"setCount":3,"setSize":4,
                 "childAnswerType":"SINGLE_CHOICE"}""");
        assertThat(codes(result)).doesNotContain("AUDIO_SHORTAGE", "SET_SHORTAGE", "SOURCE_SHORTAGE");
        var forbidden = check(subject, 12, "FORBIDDEN", """
                {"id":"read","questionType":"QUESTION_SET","questionCount":12,"setCount":3,"setSize":4,
                 "childAnswerType":"SINGLE_CHOICE"}""");
        // Only audio sets exist, so a FORBIDDEN row finds none: the audio filter is what is short.
        var issue = findIssue(forbidden, "AUDIO_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(3);
        assertThat(issue.path("available").asLong()).isZero();
    }

    /** Row 7: the audio requirement is the section's, so every block of the section inherits it. */
    @Test
    void sectionAudioRequirementIsInheritedByEveryBlock() throws Exception {
        var subject = subject();
        for (int i = 0; i < 2; i++) single(subject, SC4, 1);
        var result = check(subject, 4, "REQUIRED", """
                {"id":"first","questionType":"SINGLE_CHOICE","questionCount":2},
                {"id":"second","questionType":"SINGLE_CHOICE","questionCount":2}""");
        // Neither block declares audio, yet both report the audio shortage: the section's requirement is inherited.
        var blocks = result.path("groups").findValues("issues").stream()
                .flatMap(issues -> StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> "AUDIO_SHORTAGE".equals(issue.path("code").asText()))
                .map(issue -> issue.path("blockId").asText()).toList();
        assertThat(blocks).containsExactlyInAnyOrder("first", "second");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    /**
     * R1-3: one block needs audio AND four options. Relaxing the option count must keep audio
     * required, otherwise the check would blame the option count for a supply problem that is audio.
     * Source: two audio five-option questions and three non-audio four-option ones. Neither filter
     * alone is satisfied by the other dimension's supply, so only removing audio yields three.
     */
    @Test
    void audioAndOptionCountBlockBlamesAudioNotOptionCount() throws Exception {
        var subject = subject();
        single(subject, SC5, 1, true);
        single(subject, SC5, 1, true);
        for (int i = 0; i < 3; i++) single(subject, SC4, 1, false);
        var result = check(subject, 3, "REQUIRED", """
                {"id":"both","questionType":"SINGLE_CHOICE","questionCount":3,"itemShape":{"options":4}}""");
        var issue = findIssue(result, "AUDIO_SHORTAGE");
        assertThat(issue.path("blockId").asText()).isEqualTo("both");
        assertThat(issue.path("required").isNumber()).isTrue();
        assertThat(issue.path("required").asInt()).isEqualTo(3);
        assertThat(issue.path("available").isNumber()).isTrue();
        assertThat(issue.path("available").asLong()).isZero();
        // Neither the option count nor a generic shortage may be reported at this block.
        var atBlock = result.path("groups").findValues("issues").stream()
                .flatMap(issues -> StreamSupport.stream(issues.spliterator(), false))
                .filter(item -> "both".equals(item.path("blockId").asText()))
                .map(item -> item.path("code").asText()).toList();
        assertThat(atBlock).contains("AUDIO_SHORTAGE")
                .doesNotContain("ITEM_SHAPE_SHORTAGE", "SOURCE_SHORTAGE", "CELL_SHORTAGE");
        assertThat(codes(result)).doesNotContain("ITEM_SHAPE_SHORTAGE");
    }

    /** Row 9: eight single-choice sets do not satisfy the mixed row; the shortage is at the block. */
    @Test
    void row9EightSingleChoiceSetReportsChildConditionShortageAtTheBlock() throws Exception {
        var subject = subject();
        set(subject, singles(8), false);
        var result = check(subject, 8, null, mixedBlock());
        var issue = findIssue(result, "CHILD_CONDITION_SHORTAGE");
        assertThat(issue.path("blockId").asText()).isEqualTo("mixed");
        assertThat(issue.path("required").asInt()).isEqualTo(1);
        assertThat(issue.path("available").asLong()).isZero();
        assertThat(issue.path("location").has("childPosition")).isFalse();
        assertThat(codes(result)).doesNotContain("SET_SHORTAGE", "SOURCE_SHORTAGE", "CELL_SHORTAGE");
    }

    /** Row 9: true/false with three statements at positions 1-2 is rejected for a four-statement row. */
    @Test
    void row9ThreeStatementTrueFalseReportsChildConditionShortage() throws Exception {
        var subject = subject();
        set(subject, mixed(TF3), false);
        var result = check(subject, 8, null, mixedBlock());
        var issue = findIssue(result, "CHILD_CONDITION_SHORTAGE");
        assertThat(issue.path("blockId").asText()).isEqualTo("mixed");
        assertThat(codes(result)).doesNotContain("SET_SHORTAGE", "SOURCE_SHORTAGE");
    }

    /** Row 8 through the check: the matching mixed set is selected and the check is valid. */
    @Test
    void row8MatchingMixedSetLeavesNoShortage() throws Exception {
        var subject = subject();
        UUID matching = set(subject, mixed(TF4), false);
        var result = check(subject, 8, null, mixedBlock());
        assertThat(codes(result)).isEmpty();
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(picksAt("mixed")).containsExactly(matching);
    }

    /** Row 11: four options requested; the 5-option questions are rejected. */
    @Test
    void row11OptionCountShortageWhenFiveOptionQuestionsAreRejected() throws Exception {
        var subject = subject();
        single(subject, SC4, 1);
        single(subject, SC4, 1);
        single(subject, SC5, 1);
        single(subject, SC5, 1);
        var result = check(subject, 3, null, """
                {"id":"four","questionType":"SINGLE_CHOICE","questionCount":3,"itemShape":{"options":4}}""");
        var issue = findIssue(result, "ITEM_SHAPE_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(3);
        assertThat(issue.path("available").asLong()).isEqualTo(2);
        assertThat(issue.path("blockId").asText()).isEqualTo("four");
        assertThat(codes(result)).doesNotContain("SOURCE_SHORTAGE", "CELL_SHORTAGE");
    }

    /** Row 11: with enough 4-option questions the check is valid and selects exactly those. */
    @Test
    void row11EnoughFourOptionQuestionsReportNothing() throws Exception {
        var subject = subject();
        var fourOptions = new ArrayList<UUID>();
        for (int i = 0; i < 3; i++) fourOptions.add(single(subject, SC4, 1));
        single(subject, SC5, 1);
        var result = check(subject, 3, null, """
                {"id":"four","questionType":"SINGLE_CHOICE","questionCount":3,"itemShape":{"options":4}}""");
        assertThat(codes(result)).isEmpty();
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(picksAt("four")).containsExactlyInAnyOrderElementsOf(fourOptions);
    }

    /** Row 12: enough questions, but each is worth 1 point and the template needs 6. */
    @Test
    void row12PointsShortageInsteadOfGenericSourceShortage() throws Exception {
        var subject = subject();
        single(subject, SC4, 1);
        single(subject, SC4, 1);
        var result = check(subject, 2, null, """
                {"id":"worth","questionType":"SINGLE_CHOICE","questionCount":2,"requiredPointsPerQuestion":6}""");
        var issue = findIssue(result, "POINTS_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(2);
        assertThat(issue.path("available").asLong()).isZero();
        assertThat(issue.path("blockId").asText()).isEqualTo("worth");
        assertThat(issue.path("sectionId").asText()).isEqualTo("s");
        assertThat(codes(result)).doesNotContain("SOURCE_SHORTAGE", "CELL_SHORTAGE");
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
    }

    /** Row 12: some of the questions have the right points; the available count is the matching ones. */
    @Test
    void row12PartlyMatchingPointsReportTheMatchingCount() throws Exception {
        var subject = subject();
        single(subject, SC4, 6);
        single(subject, SC4, 1);
        single(subject, SC4, 1);
        var result = check(subject, 2, null, """
                {"id":"worth","questionType":"SINGLE_CHOICE","questionCount":2,"requiredPointsPerQuestion":6}""");
        var issue = findIssue(result, "POINTS_SHORTAGE");
        assertThat(issue.path("required").asInt()).isEqualTo(2);
        assertThat(issue.path("available").asLong()).isEqualTo(1);
    }

    /** Row 12 negative: too few questions even ignoring points keeps the generic shortage. */
    @Test
    void row12GenuinelyShortPoolIsNotBlamedOnPoints() throws Exception {
        var subject = subject();
        single(subject, SC4, 6);
        var result = check(subject, 2, null, """
                {"id":"worth","questionType":"SINGLE_CHOICE","questionCount":2,"requiredPointsPerQuestion":6}""");
        assertThat(codes(result)).doesNotContain("POINTS_SHORTAGE").containsAnyOf("CELL_SHORTAGE", "SOURCE_SHORTAGE");
    }

    /**
     * Row 6: the number the count endpoint returns for a section's filter equals the units the solver
     * receives for the same group, for every audio requirement.
     */
    @Test
    void row6AvailableCountEqualsSolverUnits() throws Exception {
        var subject = subject();
        set(subject, singles(4), true);
        set(subject, singles(4), true);
        set(subject, singles(5), true);
        set(subject, singles(4), false);
        for (String audio : List.of("REQUIRED", "FORBIDDEN", "ANY")) {
            String block = """
                    {"id":"b","questionType":"QUESTION_SET","questionCount":4,"setCount":1,"setSize":4,
                     "childAnswerType":"SINGLE_CHOICE"}""";
            String document = document(subject, 4, audio, block);
            var input = builder.build(null, new ExamStructureCodec(mapper).read(document));
            var fixed = (SolvePart.Fixed) input.parts().getFirst();
            var group = fixed.sections().getFirst().groups().getFirst();
            long solverUnits = input.candidates().candidatesFor(group).stream()
                    .mapToLong(candidate -> candidate.units().size()).sum();
            mockMvc.perform(post("/api/v1/admin/exams/available-questions/count")
                            .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectCode\":\"" + subject.code() + "\",\"questionType\":\"QUESTION_SET\","
                                    + "\"childAnswerType\":\"SINGLE_CHOICE\",\"audioRequirement\":\"" + audio + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(solverUnits));
            assertThat(solverUnits).as(audio).isEqualTo(switch (audio) {
                case "REQUIRED" -> 4 + 4 + 5;
                case "FORBIDDEN" -> 4;
                default -> 4 + 4 + 5 + 4;
            });
        }
    }

    /**
     * R1-1/R1-2: the HTTP count query maps childConditions[].answerType, childConditions[].itemShape
     * and itemShape.options onto the source filter. Discriminating fixtures: every total below is
     * the exact set of matching units, so a mapping that dropped a field returns a different total.
     * Each total must also equal the units the solver receives for the same group.
     */
    @Test
    void countEndpointHonoursChildAnswerTypeChildItemShapeAndOptionCount() throws Exception {
        var subject = subject();
        set(subject, mixed(TF4), false);                          // 8: TF4 x2 + SC4 x6
        set(subject, mixed(TF3), false);                          // 8: TF3 x2 + SC4 x6
        set(subject, List.of(new Child("SINGLE_CHOICE", SC4),
                new Child("SINGLE_CHOICE", SC5),
                new Child("SINGLE_CHOICE", SC4)), false);         // 3
        set(subject, List.of(new Child("TRUE_FALSE", TF3),
                new Child("TRUE_FALSE", TF3),
                new Child("SINGLE_CHOICE", SC4),
                new Child("SINGLE_CHOICE", SC5),
                new Child("SINGLE_CHOICE", SC4),
                new Child("SINGLE_CHOICE", SC4)), false);         // 6
        set(subject, singles(4), false);                          // 4: the only set whose every child has four options
        // Type-only positions 1-2 accept every set whose first two children are true/false.
        String typeOnly = "\"childConditions\":[{\"from\":1,\"to\":2,\"answerType\":\"TRUE_FALSE\"}]";
        assertCount(subject, "\"questionType\":\"QUESTION_SET\"," + typeOnly, 8 + 8 + 6);
        assertSolverUnits(subject, "{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":1,"
                + "\"setCount\":1,\"setSize\":8," + typeOnly + "}", 8 + 8 + 6);
        // Type plus shape at the same positions: only the four-statement sets survive.
        String typeAndShape = "\"childConditions\":[{\"from\":1,\"to\":2,\"answerType\":\"TRUE_FALSE\","
                + "\"itemShape\":{\"statements\":4}}]";
        assertCount(subject, "\"questionType\":\"QUESTION_SET\"," + typeAndShape, 8);
        assertSolverUnits(subject, "{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":1,"
                + "\"setCount\":1,\"setSize\":8," + typeAndShape + "}", 8);
        String threeStatements = typeAndShape.replace("\"statements\":4", "\"statements\":3");
        assertCount(subject, "\"questionType\":\"QUESTION_SET\"," + threeStatements, 8 + 6);
        // Shape without a type at position 4: the sets whose fourth child has four options survive.
        // The five-option fourth child (set 4) and the three-child set are rejected.
        String shapeOnly = "\"childConditions\":[{\"from\":4,\"to\":4,"
                + "\"itemShape\":{\"options\":4}}]";
        assertCount(subject, "\"questionType\":\"QUESTION_SET\"," + shapeOnly, 8 + 8 + 4);
        assertSolverUnits(subject, "{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":1,"
                + "\"setCount\":1,\"setSize\":8," + shapeOnly + "}", 8 + 8 + 4);
        // The group's own itemShape applies to every child of a set: only the all-four-option set matches.
        assertCount(subject, "\"questionType\":\"QUESTION_SET\",\"itemShape\":{\"options\":4}", 4);
        assertSolverUnits(subject, "{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":1,"
                + "\"setCount\":1,\"setSize\":4,\"itemShape\":{\"options\":4}}", 4);
        assertCount(subject, "\"questionType\":\"QUESTION_SET\",\"itemShape\":{\"options\":5}", 0);
    }

    /** R1-1: option count on a single question, sent through the HTTP count query. */
    @Test
    void countEndpointHonoursItemShapeOptionsOnSingles() throws Exception {
        var subject = subject();
        single(subject, SC4, 1);
        single(subject, SC4, 1);
        single(subject, SC5, 1);
        assertCount(subject, "\"questionType\":\"SINGLE_CHOICE\",\"itemShape\":{\"options\":4}", 2);
        assertSolverUnits(subject, "{\"id\":\"b\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1,"
                + "\"itemShape\":{\"options\":4}}", 2);
        assertCount(subject, "\"questionType\":\"SINGLE_CHOICE\",\"itemShape\":{\"options\":5}", 1);
    }

    /** R1-1: count-subject unions overlapping group sources; each unit is counted once. */
    @Test
    void countSubjectUnionsOverlappingGroupSources() throws Exception {
        var subject = subject();
        set(subject, singles(4), false);                          // 4 units, matches groups A and B
        set(subject, singles(3), false);                          // 3 units, matches groups A and B
        set(subject, List.of(new Child("SINGLE_CHOICE", SC4),
                new Child("SINGLE_CHOICE", SC5),
                new Child("SINGLE_CHOICE", SC4)), false);         // 3: type matches A, shape fails B
        set(subject, mixed(TF4), false);                          // 8, matches no group
        single(subject, SC4, 1);
        single(subject, SC4, 1);                                  // 2 units, group C only
        String groups = "["
                + "{\"subjectCode\":\"" + subject.code() + "\",\"questionType\":\"QUESTION_SET\","
                + " \"childConditions\":[{\"from\":1,\"to\":3,\"answerType\":\"SINGLE_CHOICE\"}]},"
                + "{\"subjectCode\":\"" + subject.code() + "\",\"questionType\":\"QUESTION_SET\","
                + " \"itemShape\":{\"options\":4}},"
                + "{\"subjectCode\":\"" + subject.code() + "\",\"questionType\":\"SINGLE_CHOICE\"}]";
        var result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/available-questions/count-subject")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCode\":\"" + subject.code() + "\",\"groups\":" + groups + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("total").asLong()).isEqualTo(4 + 3 + 3 + 2);
        assertThat(result.path("commandCount").asLong()).isEqualTo(4 + 3 + 3 + 2);
        // Parity: the same union is the distinct set of units the solver receives for the groups.
        var input = builder.build(null, new ExamStructureCodec(mapper).read(document(subject, 1, null,
                "{\"id\":\"a\",\"questionType\":\"QUESTION_SET\",\"questionCount\":1,\"setCount\":1,"
                        + "\"setSize\":3,\"childConditions\":[{\"from\":1,\"to\":3,"
                        + "\"answerType\":\"SINGLE_CHOICE\"}]},"
                + "{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":1,\"setCount\":1,"
                        + "\"setSize\":3,\"itemShape\":{\"options\":4}},"
                + "{\"id\":\"c\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}")));
        var fixed = (SolvePart.Fixed) input.parts().getFirst();
        var distinct = new java.util.HashSet<String>();
        for (var group : fixed.sections().getFirst().groups()) {
            for (PathCandidate candidate : input.candidates().candidatesFor(group)) {
                for (var unit : candidate.units()) {
                    distinct.add(unit.childId() == null ? candidate.questionId() : unit.childId());
                }
            }
        }
        assertThat(distinct).hasSize(4 + 3 + 3 + 2);
    }

    private void assertCount(Subject subject, String filter, long expected) throws Exception {
        var response = mockMvc.perform(post("/api/v1/admin/exams/available-questions/count")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCode\":\"" + subject.code() + "\"," + filter + "}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        assertThat(mapper.readTree(response.getContentAsString()).path("total").asLong())
                .as(filter).isEqualTo(expected);
    }

    /** The units {@link SolverInputBuilder} hands the solver for a one-block document. */
    private void assertSolverUnits(Subject subject, String block, long expected) {
        var input = builder.build(null, new ExamStructureCodec(mapper).read(document(subject, 1, null, block)));
        var fixed = (SolvePart.Fixed) input.parts().getFirst();
        long units = input.candidates().candidatesFor(fixed.sections().getFirst().groups().getFirst())
                .stream().mapToLong(candidate -> candidate.units().size()).sum();
        assertThat(units).isEqualTo(expected);
    }

    private static String mixedBlock() {
        return "{\"id\":\"mixed\",\"questionType\":\"QUESTION_SET\",\"questionCount\":8,\"setCount\":1,"
                + "\"setSize\":8," + MIXED_CONDITIONS + "}";
    }

    private static String document(Subject subject, int count, String audio, String block) {
        return "{\"parts\":[{\"id\":\"p\",\"questionCount\":" + count + ",\"sections\":[{\"id\":\"s\","
                + "\"subjectCode\":\"" + subject.code() + "\",\"questionCount\":" + count
                + (audio == null ? "" : ",\"audioRequirement\":\"" + audio + "\"") + ",\"blocks\":[" + block + "]}]}]}";
    }

    private List<String> codes(JsonNode result) {
        return result.path("groups").findValuesAsText("code");
    }

    private JsonNode findIssue(JsonNode result, String code) {
        return result.path("groups").findValues("issues").stream()
                .flatMap(issues -> StreamSupport.stream(issues.spliterator(), false))
                .filter(issue -> code.equals(issue.path("code").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + code + " in " + codes(result)));
    }

    private JsonNode check(Subject subject, int count, String audio, String block) throws Exception {
        UUID id = UUID.randomUUID();
        lastExam = id;
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count,duration_minutes) "
                + "VALUES (?,?,?,'Audio','FULL','SHARED',?,60)", id, id.toString(), id.toString(), count);
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'audio')",
                id, document(subject, count, audio, block));
        for (JsonNode declared : mapper.readTree(document(subject, count, audio, block))
                .path("parts").path(0).path("sections").path(0).path("blocks")) {
            jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) "
                    + "VALUES (?,?,?,?,?,?)", UUID.randomUUID(), id, declared.path("id").asText(), "p", "s", subject.id());
        }
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + id + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
