package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.AudioRequirement;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.ChildCondition;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.ItemShape;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * BE-17 (UNI-2689) source filters. Row numbers refer to plan 2026-11-18 section 2 "BE-17":
 * 1-5 audio, 6 count equals solver units, 8-10 per-position child conditions, 11 option count.
 */
@Transactional
class QuestionSourceAudioChildTypeIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final String TF4 = "{\"type\":\"TRUE_FALSE\",\"statements\":[{},{},{},{}]}";
    private static final String TF3 = "{\"type\":\"TRUE_FALSE\",\"statements\":[{},{},{}]}";
    private static final String SC4 = choice("SINGLE_CHOICE", 4);
    private static final String SC5 = choice("SINGLE_CHOICE", 5);

    @Autowired QuestionSourcePort source;
    @Autowired JdbcTemplate jdbc;

    private static String choice(String type, int options) {
        var body = new StringBuilder("{\"type\":\"" + type + "\",\"options\":[");
        for (int i = 0; i < options; i++) body.append(i == 0 ? "" : ",").append("{\"id\":\"o").append(i).append("\"}");
        return body.append("]}").toString();
    }

    private record Child(String type, String body) { }

    private UUID asset(String mime) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question_asset(id,kind,object_key,file_name,mime,size,sha256) "
                + "VALUES (?,'ATTACHMENT',?,?,?,1,?)", id, "k/" + id, "f-" + id, mime, new byte[32]);
        return id;
    }

    private UUID single(UUID subject, String type, String body, UUID... attachments) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,?,'READY',?,1,1)", id, "Q-" + id, type, subject);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                id, snapshot(body, attachments));
        return id;
    }

    /** Question set whose children never carry files: only the root can carry audio. */
    private UUID set(UUID subject, List<Child> children, UUID... rootAttachments) {
        UUID root = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,'QUESTION_SET','READY',?,1,1)", root, "S-" + root, subject);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                root, snapshot("{\"type\":\"QUESTION_SET\",\"children\":[]}", rootAttachments));
        for (int position = 0; position < children.size(); position++) {
            UUID child = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,points) "
                    + "VALUES (?,?,?,'READY',?,?,1)", child, "C-" + child, children.get(position).type(), root, position);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                    child, "{\"body\":" + children.get(position).body() + "}");
        }
        return root;
    }

    private static String snapshot(String body, UUID... attachments) {
        var ids = new StringBuilder("[");
        for (int i = 0; i < attachments.length; i++) ids.append(i == 0 ? "" : ",").append('"').append(attachments[i]).append('"');
        return "{\"body\":" + body + ",\"attachmentIds\":" + ids.append(']') + "}";
    }

    private static QuestionSourcePort.Filter filter(UUID subject, QuestionType type, AudioRequirement audio,
            ItemShape shape, List<ChildCondition> conditions) {
        return new QuestionSourcePort.Filter(subject, null, null, type, null, null, List.of(), List.of(), null,
                shape, null, conditions, List.of(), List.of(), List.of(), null, List.of(), audio);
    }

    private List<UUID> accepted(QuestionSourcePort.Filter filter) {
        return source.findCandidates(filter, 100).stream().map(QuestionSourcePort.Candidate::questionId).toList();
    }

    private static List<Child> children(int count, String type, String body) {
        var list = new ArrayList<Child>();
        for (int i = 0; i < count; i++) list.add(new Child(type, body));
        return list;
    }

    /** Row 1. */
    @Test
    void row1RequiredAcceptsSingleWithMp3() {
        UUID subject = UUID.randomUUID();
        UUID mp3 = single(subject, "SINGLE_CHOICE", SC4, asset("audio/mpeg"));
        single(subject, "SINGLE_CHOICE", SC4);
        var required = filter(subject, QuestionType.SINGLE_CHOICE, AudioRequirement.REQUIRED, null, null);
        assertThat(accepted(required)).containsExactly(mp3);
        assertThat(source.count(required).total()).isEqualTo(1);
    }

    /** Row 2: a PDF or an image is an attachment but not audio. */
    @Test
    void row2RequiredRejectsPdfAndImageOnly() {
        UUID subject = UUID.randomUUID();
        single(subject, "SINGLE_CHOICE", SC4, asset("application/pdf"));
        single(subject, "SINGLE_CHOICE", SC4, asset("image/png"), asset("application/pdf"));
        single(subject, "SINGLE_CHOICE", SC4);
        var required = filter(subject, QuestionType.SINGLE_CHOICE, AudioRequirement.REQUIRED, null, null);
        assertThat(accepted(required)).isEmpty();
        assertThat(source.count(required).total()).isZero();
        // The non-audio attachments are still there: FORBIDDEN accepts all three.
        assertThat(accepted(filter(subject, QuestionType.SINGLE_CHOICE, AudioRequirement.FORBIDDEN, null, null)))
                .hasSize(3);
    }

    /** Row 2 (mixed): audio next to other files still counts. */
    @Test
    void audioNextToOtherFilesIsAudio() {
        UUID subject = UUID.randomUUID();
        UUID mixed = single(subject, "SINGLE_CHOICE", SC4, asset("application/pdf"), asset("audio/mpeg"));
        assertThat(accepted(filter(subject, QuestionType.SINGLE_CHOICE, AudioRequirement.REQUIRED, null, null)))
                .containsExactly(mixed);
    }

    /** Row 3: audio on the root accepts the whole set although no child has a file. */
    @Test
    void row3RequiredAcceptsWholeSetByRootAudio() {
        UUID subject = UUID.randomUUID();
        UUID audioSet = set(subject, children(4, "SINGLE_CHOICE", SC4), asset("audio/mpeg"));
        set(subject, children(4, "SINGLE_CHOICE", SC4));
        var required = filter(subject, QuestionType.QUESTION_SET, AudioRequirement.REQUIRED, null, null);
        var candidates = source.findCandidates(required, 100);
        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.questionId()).isEqualTo(audioSet);
            assertThat(candidate.units()).hasSize(4);
        });
        assertThat(source.count(required).total()).isEqualTo(4);
    }

    /** Row 4: FORBIDDEN rejects the whole set when its root has audio. */
    @Test
    void row4ForbiddenRejectsWholeSetByRootAudio() {
        UUID subject = UUID.randomUUID();
        set(subject, children(4, "SINGLE_CHOICE", SC4), asset("audio/mpeg"));
        UUID plain = set(subject, children(4, "SINGLE_CHOICE", SC4));
        UUID pdfOnly = set(subject, children(4, "SINGLE_CHOICE", SC4), asset("application/pdf"));
        var forbidden = filter(subject, QuestionType.QUESTION_SET, AudioRequirement.FORBIDDEN, null, null);
        assertThat(accepted(forbidden)).containsExactlyInAnyOrder(plain, pdfOnly);
        assertThat(source.count(forbidden).total()).isEqualTo(8);
    }

    /** Row 5: ANY, unset and the pre-BE-17 constructor give the same result. */
    @Test
    void row5AnyAndUnsetAreUnchanged() {
        UUID subject = UUID.randomUUID();
        UUID withAudio = single(subject, "SINGLE_CHOICE", SC4, asset("audio/mpeg"));
        UUID plain = single(subject, "SINGLE_CHOICE", SC4);
        UUID withPdf = single(subject, "SINGLE_CHOICE", SC4, asset("application/pdf"));
        var any = filter(subject, QuestionType.SINGLE_CHOICE, AudioRequirement.ANY, null, null);
        var unset = filter(subject, QuestionType.SINGLE_CHOICE, null, null, null);
        var legacy = new QuestionSourcePort.Filter(subject, null, null, QuestionType.SINGLE_CHOICE, null, null,
                List.of(), List.of(), null, null, null, null);
        assertThat(unset.audioRequirement()).isEqualTo(AudioRequirement.ANY);
        for (var f : List.of(any, unset, legacy)) {
            assertThat(accepted(f)).containsExactlyInAnyOrder(withAudio, plain, withPdf);
            assertThat(source.count(f).total()).isEqualTo(3);
        }
    }

    /** Row 6: the count of a filter is the number of units findCandidates hands the solver. */
    @Test
    void row6CountEqualsSolverUnits() {
        UUID subject = UUID.randomUUID();
        set(subject, children(4, "SINGLE_CHOICE", SC4), asset("audio/mpeg"));
        set(subject, children(5, "SINGLE_CHOICE", SC4), asset("audio/mpeg"));
        set(subject, children(4, "SINGLE_CHOICE", SC4));
        for (var audio : AudioRequirement.values()) {
            var f = filter(subject, QuestionType.QUESTION_SET, audio, null, null);
            long units = source.findCandidates(f, 100).stream().mapToLong(c -> c.units().size()).sum();
            assertThat(source.count(f).total()).as(audio.name()).isEqualTo(units);
        }
    }

    /** Row 6 through the HTTP count endpoint, with the filter the web admin sends. */
    @Test
    void row6CountEndpointFiltersByAudio() throws Exception {
        UUID id = UUID.randomUUID();
        String code = "AUDIOCOUNT_" + id.toString().replace("-", "");
        jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,path) VALUES "
                + "(?,?,?,?,(SELECT coalesce(max(value),0)+1 FROM catalog.catalog_item WHERE group_id=?),'Audio',?)",
                id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        single(id, "SINGLE_CHOICE", SC4, asset("audio/mpeg"));
        single(id, "SINGLE_CHOICE", SC4, asset("application/pdf"));
        single(id, "SINGLE_CHOICE", SC4);
        for (var expected : List.of("REQUIRED:1", "FORBIDDEN:2", "ANY:3")) {
            String[] parts = expected.split(":");
            mockMvc.perform(post("/api/v1/admin/exams/available-questions/count")
                            .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectCode\":\"" + code + "\",\"questionType\":\"SINGLE_CHOICE\","
                                    + "\"audioRequirement\":\"" + parts[0] + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(Integer.parseInt(parts[1])));
        }
        mockMvc.perform(post("/api/v1/admin/exams/available-questions/count")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectCode\":\"" + code + "\",\"questionType\":\"SINGLE_CHOICE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
    }

    /** Row 8: a set of 8 whose positions 1-2 are four-statement true/false and 3-8 single choice. */
    @Test
    void row8MixedSetIsAccepted() {
        UUID subject = UUID.randomUUID();
        UUID mixed = set(subject, mixedChildren(4, 6));
        assertThat(accepted(mixedFilter(subject, 4))).containsExactly(mixed);
        assertThat(source.count(mixedFilter(subject, 4)).total()).isEqualTo(8);
    }

    /** Row 9: eight single choice, or true/false with three statements at 1-2, are rejected. */
    @Test
    void row9WrongChildTypesAreRejected() {
        UUID subject = UUID.randomUUID();
        set(subject, children(8, "SINGLE_CHOICE", SC4));
        set(subject, mixedChildren(3, 6));
        List<Child> oneShort = new ArrayList<>(mixedChildren(4, 6));
        oneShort.set(1, new Child("TRUE_FALSE", TF3));
        set(subject, oneShort);
        List<Child> swapped = new ArrayList<>(children(2, "SINGLE_CHOICE", SC4));
        swapped.addAll(children(6, "TRUE_FALSE", TF4));
        set(subject, swapped);
        assertThat(accepted(mixedFilter(subject, 4))).isEmpty();
        assertThat(source.count(mixedFilter(subject, 4)).total()).isZero();
    }

    /** Row 9 edge: a position beyond the number of children is a miss, not an error. */
    @Test
    void row9ShortSetIsRejected() {
        UUID subject = UUID.randomUUID();
        set(subject, mixedChildren(4, 4));
        assertThat(accepted(mixedFilter(subject, 4))).isEmpty();
    }

    /** Row 10: a condition with only nodeIds behaves as before; an empty condition matches nothing. */
    @Test
    void row10NodeOnlyConditionIsUnchanged() {
        UUID subject = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID matching = set(subject, children(3, "SINGLE_CHOICE", SC4));
        UUID elsewhere = set(subject, children(3, "SINGLE_CHOICE", SC4));
        link(matching, 0, node);
        link(elsewhere, 0, other);
        var onlyNodes = new ChildCondition(1, 1, List.of(node));
        assertThat(onlyNodes.answerType()).isNull();
        assertThat(onlyNodes.itemShape()).isNull();
        assertThat(accepted(filter(subject, QuestionType.QUESTION_SET, null, null, List.of(onlyNodes))))
                .containsExactly(matching);
        assertThat(accepted(filter(subject, QuestionType.QUESTION_SET, null, null,
                List.of(new ChildCondition(1, 1, List.of()))))).isEmpty();
    }

    /** Row 10: knowledge nodes and answer type are ANDed on the same position. */
    @Test
    void nodesAndAnswerTypeAreAndedPerPosition() {
        UUID subject = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        UUID both = set(subject, List.of(new Child("TRUE_FALSE", TF4), new Child("SINGLE_CHOICE", SC4)));
        UUID wrongType = set(subject, List.of(new Child("SINGLE_CHOICE", SC4), new Child("SINGLE_CHOICE", SC4)));
        link(both, 0, node);
        link(wrongType, 0, node);
        var condition = new ChildCondition(1, 1, List.of(node), QuestionType.TRUE_FALSE, null);
        assertThat(accepted(filter(subject, QuestionType.QUESTION_SET, null, null, List.of(condition))))
                .containsExactly(both);
    }

    /** Row 11: option count on a single question. */
    @Test
    void row11OptionCountFiltersSingleQuestions() {
        UUID subject = UUID.randomUUID();
        UUID four = single(subject, "SINGLE_CHOICE", SC4);
        single(subject, "SINGLE_CHOICE", SC5);
        UUID multiFour = single(subject, "MULTIPLE_CHOICE", choice("MULTIPLE_CHOICE", 4));
        var f = filter(subject, null, null, new ItemShape(null, null, null, 4), null);
        assertThat(accepted(f)).containsExactlyInAnyOrder(four, multiFour);
        var legacyShape = new ItemShape(null, null, null);
        assertThat(legacyShape.options()).isNull();
        assertThat(accepted(filter(subject, null, null, legacyShape, null))).hasSize(3);
    }

    /** Row 11: option count inside a per-position condition. */
    @Test
    void row11OptionCountInsideChildCondition() {
        UUID subject = UUID.randomUUID();
        UUID ok = set(subject, children(3, "SINGLE_CHOICE", SC4));
        set(subject, List.of(new Child("SINGLE_CHOICE", SC4), new Child("SINGLE_CHOICE", SC5),
                new Child("SINGLE_CHOICE", SC4)));
        var condition = new ChildCondition(1, 3, List.of(), QuestionType.SINGLE_CHOICE,
                new ItemShape(null, null, null, 4));
        assertThat(accepted(filter(subject, QuestionType.QUESTION_SET, null, null, List.of(condition))))
                .containsExactly(ok);
    }

    private List<Child> mixedChildren(int statements, int singles) {
        String tf = "{\"type\":\"TRUE_FALSE\",\"statements\":["
                + String.join(",", java.util.Collections.nCopies(statements, "{}")) + "]}";
        List<Child> list = new ArrayList<>(children(2, "TRUE_FALSE", tf));
        list.addAll(children(singles, "SINGLE_CHOICE", SC4));
        return list;
    }

    private QuestionSourcePort.Filter mixedFilter(UUID subject, int statements) {
        return filter(subject, QuestionType.QUESTION_SET, null, null, List.of(
                new ChildCondition(1, 2, List.of(), QuestionType.TRUE_FALSE, new ItemShape(statements, null, null)),
                new ChildCondition(3, 8, List.of(), QuestionType.SINGLE_CHOICE, null)));
    }

    private void link(UUID root, int position, UUID node) {
        UUID child = jdbc.queryForObject(
                "SELECT id FROM questionbank.question WHERE parent_id=? AND position=?", UUID.class, root, position);
        jdbc.update("INSERT INTO questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) "
                + "VALUES (?,?,true)", child, node);
    }
}
