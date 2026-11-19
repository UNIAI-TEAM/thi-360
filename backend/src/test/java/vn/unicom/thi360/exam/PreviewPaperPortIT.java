package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.spi.PreviewPaperPort;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.PreviewGoneException;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * EX-2 (decision D-b, BS 9): {@code PreviewPaperPort} reads the generated trial paper back from Redis and extends
 * its life when the admin starts a trial. Previews are produced by {@code POST /exams/{id}/preview}, the only
 * producer, so the port is checked against what that endpoint returned.
 */
@Transactional
class PreviewPaperPortIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final long SEED = 20261119L;
    private static final String KEY = "exam:preview:";

    @Autowired private PreviewPaperPort port;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private record Subject(UUID id, String code) {}

    // --- AC-20 ---------------------------------------------------------------------------------------------

    @Test
    void loadReturnsTheOrderedPaper() throws Exception {
        UUID examId = orderedExam();
        JsonNode result = generate(examId);
        PreviewPaperPort.PreviewPaper paper = port.load(examId, previewId(result));

        List<String> expected = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        for (JsonNode part : result.path("paper").path("parts")) {
            expected.add("part " + part.path("partId").asText() + " " + part.path("firstNumber").asInt() + "-"
                    + part.path("lastNumber").asInt());
            for (JsonNode block : part.path("blocks")) {
                expected.add("block " + block.path("blockId").asText());
                for (JsonNode item : block.path("items")) {
                    if ("SET".equals(item.path("kind").asText())) {
                        expected.add("set " + item.path("setId").asText() + " v" + item.path("version").asInt());
                        for (JsonNode child : item.path("children")) expected.add(question(child));
                    } else expected.add(question(item.path("question")));
                }
            }
        }
        for (PreviewPaperPort.Part part : paper.parts()) {
            actual.add("part " + part.partId() + " " + part.firstNumber() + "-" + part.lastNumber());
            for (PreviewPaperPort.Block block : part.blocks()) {
                actual.add("block " + block.blockId());
                for (PreviewPaperPort.Item item : block.items()) {
                    if (item.kind() == PreviewPaperPort.Item.Kind.SET) {
                        actual.add("set " + item.set().questionId() + " v" + item.set().versionNo());
                        for (PreviewPaperPort.Question child : item.set().children()) actual.add(question(child));
                    } else actual.add(question(item.question()));
                }
            }
        }
        assertThat(expected).hasSizeGreaterThan(10);
        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(paper.examId()).isEqualTo(examId);
        assertThat(paper.seed()).isEqualTo(SEED);
        assertThat(paper.versionNo()).isNull();
        assertThat(paper.parts().stream().flatMap(part -> part.blocks().stream()).flatMap(block -> block.items().stream())
                .filter(item -> item.kind() == PreviewPaperPort.Item.Kind.QUESTION)
                .map(item -> item.question().optionOrder()))
                .as("shuffled single-choice questions carry their displayed option order")
                .allMatch(order -> order != null && order.size() == 6);
    }

    @Test
    void loadIsRepeatable() throws Exception {
        UUID examId = orderedExam();
        UUID previewId = previewId(generate(examId));
        PreviewPaperPort.PreviewPaper first = port.load(examId, previewId);
        assertThat(port.load(examId, previewId)).isEqualTo(first);
        assertThat(port.load(examId, previewId)).isEqualTo(first);
    }

    // --- AC-21, AC-22 --------------------------------------------------------------------------------------

    @Test
    void expiredPreviewIsGone() throws Exception {
        UUID examId = orderedExam();
        UUID previewId = previewId(generate(examId));
        assertThat(port.load(examId, previewId)).isNotNull();
        redis.delete(KEY + previewId);
        assertThatThrownBy(() -> port.load(examId, previewId)).isInstanceOf(PreviewGoneException.class);
        assertThatThrownBy(() -> port.load(examId, UUID.randomUUID())).isInstanceOf(PreviewGoneException.class);
    }

    @Test
    void previewOfAnotherExamIsGone() throws Exception {
        UUID examA = orderedExam();
        UUID examB = orderedExam();
        UUID previewOfA = previewId(generate(examA));
        assertThatThrownBy(() -> port.load(examB, previewOfA)).isInstanceOf(PreviewGoneException.class);
        assertThatThrownBy(() -> port.extendForTrial(examB, previewOfA)).isInstanceOf(PreviewGoneException.class);
        assertThat(redis.getExpire(KEY + previewOfA, TimeUnit.SECONDS)).as("the other exam did not extend it")
                .isLessThanOrEqualTo(30 * 60);
        assertThat(port.load(examA, previewOfA)).isNotNull();
    }

    // --- AC-25 ---------------------------------------------------------------------------------------------

    @Test
    void trialExtensionNeverShortens() throws Exception {
        UUID examId = orderedExam(); // SHARED, 60 minutes
        UUID previewId = previewId(generate(examId));
        Instant before = Instant.now();
        Instant first = port.extendForTrial(examId, previewId);
        assertSeconds(KEY + previewId, 90 * 60);
        assertThat(first).isBetween(before.plus(Duration.ofMinutes(90)).minusSeconds(5),
                Instant.now().plus(Duration.ofMinutes(90)));
        Instant second = port.extendForTrial(examId, previewId);
        assertThat(second).isAfterOrEqualTo(first);
        assertSeconds(KEY + previewId, 90 * 60);

        redis.expire(KEY + previewId, Duration.ofMinutes(300));
        Instant longer = port.extendForTrial(examId, previewId);
        assertSeconds(KEY + previewId, 300 * 60);
        assertThat(longer).isAfter(Instant.now().plus(Duration.ofMinutes(299)));
        assertThat(port.load(examId, previewId)).as("an extended preview reads as before").isNotNull();
    }

    @Test
    void extendExpiredPreviewIsGone() throws Exception {
        UUID examId = orderedExam();
        UUID previewId = previewId(generate(examId));
        redis.delete(KEY + previewId);
        assertThatThrownBy(() -> port.extendForTrial(examId, previewId)).isInstanceOf(PreviewGoneException.class);
        assertThat(redis.hasKey(KEY + previewId)).as("a gone preview is never recreated").isFalse();

        UUID dying = previewId(generate(examId));
        redis.expire(KEY + dying, Duration.ofMillis(200));
        Thread.sleep(400);
        assertThatThrownBy(() -> port.extendForTrial(examId, dying)).isInstanceOf(PreviewGoneException.class);
        assertThat(redis.hasKey(KEY + dying)).isFalse();
    }

    @Test
    void untouchedPreviewKeepsThirtyMinutes() throws Exception {
        UUID examId = orderedExam();
        UUID previewId = previewId(generate(examId));
        assertSeconds(KEY + previewId, 30 * 60);
        port.load(examId, previewId);
        assertSeconds(KEY + previewId, 30 * 60);
    }

    // --- AC-26 ---------------------------------------------------------------------------------------------

    @Test
    void sharedTrialUsesExamDuration() throws Exception {
        // V-ACT FULL style: the exam holds 150 minutes, the part duration is blank.
        UUID examId = exam("SHARED", 150, subject -> parts(subject, null, null));
        JsonNode result = generate(examId);
        UUID previewId = previewId(result);
        assertThat(port.load(examId, previewId).durationMinutes()).isEqualTo(150);
        port.extendForTrial(examId, previewId);
        assertSeconds(KEY + previewId, 180 * 60);
    }

    @Test
    void perPartTrialSumsGeneratedPath() throws Exception {
        UUID examId = exam("PER_PART", null, subject -> parts(subject, 40, 50));
        UUID previewId = previewId(generate(examId));
        PreviewPaperPort.PreviewPaper paper = port.load(examId, previewId);
        assertThat(paper.timing()).isEqualTo("PER_PART");
        assertThat(paper.durationMinutes()).isEqualTo(90);
        assertThat(paper.parts()).extracting(PreviewPaperPort.Part::durationMinutes).containsExactly(40, 50);
        port.extendForTrial(examId, previewId);
        assertSeconds(KEY + previewId, 120 * 60);
    }

    @Test
    void draftEditDoesNotChangeTrial() throws Exception {
        UUID examId = exam("SHARED", 150, subject -> parts(subject, null, null));
        UUID previewId = previewId(generate(examId));
        PreviewPaperPort.PreviewPaper before = port.load(examId, previewId);
        jdbc.update("update exam.exam set timing = 'PER_PART', duration_minutes = 10 where id = ?", examId);
        jdbc.update("update exam.exam_structure set parts = '{\"parts\":[]}'::jsonb where exam_id = ?", examId);
        assertThat(port.load(examId, previewId)).isEqualTo(before);
        port.extendForTrial(examId, previewId);
        assertSeconds(KEY + previewId, 180 * 60);
    }

    // --- AC-27 ---------------------------------------------------------------------------------------------

    @Test
    void loadCarriesKindAttachmentsAndSegments() throws Exception {
        UUID hsca = created(create("HSCA", "PART", "P1", "ANH", 40));
        PreviewPaperPort.PreviewPaper listening = port.load(hsca, previewId(generate(hsca)));
        assertThat(listening.kindCode()).isEqualTo("HSCA");
        List<PreviewPaperPort.QuestionSet> sets = listening.parts().stream()
                .flatMap(part -> part.blocks().stream()).flatMap(block -> block.items().stream())
                .filter(item -> item.kind() == PreviewPaperPort.Item.Kind.SET).map(PreviewPaperPort.Item::set).toList();
        assertThat(sets).as("a listening paper holds question sets").isNotEmpty();
        assertThat(sets).anyMatch(set -> set.attachments().stream().anyMatch(file -> file.mime().startsWith("audio/")));
        assertThat(sets).allMatch(set -> set.children().stream().allMatch(child -> child.attachments().isEmpty()));
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(mapper.valueToTree(listening));

        UUID vsat = created(create("VSAT", "SUBJECT", null, "ANH", 60));
        PreviewPaperPort.PreviewPaper english = port.load(vsat, previewId(generate(vsat)));
        assertThat(english.kindCode()).isEqualTo("VSAT");
        assertThat(english.parts()).hasSize(1);
        assertThat(english.parts().getFirst().displaySegments()).hasSize(4)
                .allSatisfy(segment -> {
                    assertThat(segment.name()).isNotBlank();
                    assertThat(segment.fromPosition()).isPositive();
                });
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(mapper.valueToTree(english));
    }

    @Test
    void loadIgnoresLaterDraftEdits() throws Exception {
        UUID vsat = created(create("VSAT", "SUBJECT", null, "ANH", 60));
        UUID previewId = previewId(generate(vsat));
        PreviewPaperPort.PreviewPaper before = port.load(vsat, previewId);
        assertThat(before.parts().getFirst().displaySegments()).isNotEmpty();
        jdbc.update("update exam.exam set name = 'edited', duration_minutes = 5, timing = 'PER_PART' where id = ?", vsat);
        jdbc.update("update exam.exam_structure set parts = '{\"parts\":[]}'::jsonb where exam_id = ?", vsat);
        assertThat(port.load(vsat, previewId)).isEqualTo(before);
    }

    // --- AC-28 ---------------------------------------------------------------------------------------------

    @Test
    void legacySnapshotIsExpired() throws Exception {
        UUID examId = orderedExam();
        for (List<String> removed : List.of(List.of("kindCode"), List.of("timing"), List.of("durationMinutes"),
                List.of("kindCode", "timing", "durationMinutes"))) {
            UUID previewId = previewId(generate(examId));
            ObjectNode stored = (ObjectNode) mapper.readTree(redis.opsForValue().get(KEY + previewId));
            assertThat(stored.has("kindCode")).as("a new snapshot always writes kindCode").isTrue();
            stored.remove(removed);
            redis.opsForValue().set(KEY + previewId, stored.toString(), Duration.ofMinutes(30));
            assertThatThrownBy(() -> port.load(examId, previewId)).as("load without " + removed)
                    .isInstanceOf(PreviewGoneException.class);
            assertThatThrownBy(() -> port.extendForTrial(examId, previewId)).as("extend without " + removed)
                    .isInstanceOf(PreviewGoneException.class);
            assertThat(redis.getExpire(KEY + previewId, TimeUnit.SECONDS)).as("a legacy snapshot is not extended")
                    .isLessThanOrEqualTo(30 * 60);
        }
    }

    @Test
    void trialExtensionIsAtomicWithExpiry() throws Exception {
        UUID examId = orderedExam(); // wants 90 minutes
        UUID previewId = previewId(generate(examId));
        redis.expire(KEY + previewId, Duration.ofMinutes(300));
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Instant>> calls = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                Callable<Instant> call = () -> {
                    start.await();
                    return port.extendForTrial(examId, previewId);
                };
                calls.add(pool.submit(call));
            }
            start.countDown();
            for (Future<Instant> call : calls) {
                assertThat(call.get(30, TimeUnit.SECONDS)).as("every caller sees the longer expiry already granted")
                        .isAfter(Instant.now().plus(Duration.ofMinutes(299)));
            }
        } finally {
            pool.shutdownNow();
        }
        assertSeconds(KEY + previewId, 300 * 60);

        // Two simultaneous first clicks on a fresh preview end with one expiry, no shorter than either caller was told.
        UUID fresh = previewId(generate(examId));
        pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Instant>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Instant> call = () -> {
                    start.await();
                    return port.extendForTrial(examId, fresh);
                };
                calls.add(pool.submit(call));
            }
            start.countDown();
            Instant told = Instant.MIN;
            for (Future<Instant> call : calls) {
                Instant at = call.get(30, TimeUnit.SECONDS);
                if (at.isAfter(told)) told = at;
            }
            long left = redis.getExpire(KEY + fresh, TimeUnit.MILLISECONDS);
            assertThat(Instant.now().plusMillis(left)).isAfterOrEqualTo(told.minusMillis(1500));
            assertSeconds(KEY + fresh, 90 * 60);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- support -------------------------------------------------------------------------------------------

    private void assertSeconds(String key, long expected) {
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(expected - 15, expected);
    }

    private static String question(JsonNode question) {
        List<String> order = new ArrayList<>();
        question.path("optionOrder").forEach(id -> order.add(id.asText()));
        return question.path("number").asInt() + " " + question.path("questionId").asText() + " v"
                + question.path("version").asInt() + " " + question.path("points").decimalValue().stripTrailingZeros()
                .toPlainString() + " " + order;
    }

    private static String question(PreviewPaperPort.Question question) {
        return question.number() + " " + question.questionId() + " v" + question.versionNo() + " "
                + question.points().stripTrailingZeros().toPlainString() + " "
                + (question.optionOrder() == null ? List.of() : question.optionOrder());
    }

    private UUID previewId(JsonNode result) {
        return UUID.fromString(result.path("previewId").asText());
    }

    private JsonNode generate(UUID examId) throws Exception {
        JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", examId)
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":" + SEED + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("status").asText()).as(result.path("issues").toString()).isEqualTo("GENERATED");
        return result;
    }

    private org.springframework.test.web.servlet.ResultActions create(String kind, String scope, String partCode,
            String subjectCode, int minutes) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", kind).put("scope", scope)
                .put("name", "EX-2 preview port " + kind)
                .put("code", "EX2P-" + UUID.randomUUID().toString().substring(0, 8))
                .put("subjectCode", subjectCode).put("durationMinutes", minutes);
        if (partCode != null) body.put("partCode", partCode);
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private UUID created(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return UUID.fromString(mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).path("id").asText());
    }

    private Subject subject() {
        UUID id = UUID.randomUUID();
        String code = "PORT_" + id.toString().replace("-", "");
        jdbc.update("""
                insert into catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                values (?, ?, ?, ?, (select coalesce(max(value),0)+1 from catalog.catalog_item where group_id=?),
                    'Port subject', 'Port subject', 0, 1, false, 1, ?)
                """, id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return new Subject(id, code);
    }

    private UUID singleChoice(Subject subject) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,'SINGLE_CHOICE','READY',?,1,1)", id, "P-" + id, subject.id());
        String body = """
                {"type":"SINGLE_CHOICE","shuffleOptions":true,"options":[
                  {"id":"a","content":"A","correct":true},{"id":"b","content":"B"},{"id":"c","content":"C"},
                  {"id":"d","content":"D"},{"id":"e","content":"E"},{"id":"f","content":"F"}]}
                """;
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                id, "{\"content\":\"q\",\"points\":1,\"body\":" + body + "}");
        return id;
    }

    private void questionSet(Subject subject) {
        UUID parent = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,'QUESTION_SET','READY',?,1,2)", parent, "SET-" + parent, subject.id());
        int position = 0;
        for (UUID child : List.of(first, second)) {
            jdbc.update("insert into questionbank.question(id, code, type, status, parent_id, position, points) "
                    + "values (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "CHILD-" + child, parent, position++);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                    child, "{\"content\":\"child\",\"points\":1,\"body\":{\"type\":\"SINGLE_CHOICE\"}}");
        }
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                parent, "{\"content\":\"set\",\"points\":2,\"body\":{\"type\":\"QUESTION_SET\",\"passage\":\"passage\","
                        + "\"children\":[{\"id\":\"" + first + "\"},{\"id\":\"" + second + "\"}]}}");
    }

    /** Two parts, ten questions each: eight single-choice with six options and one set of two. */
    private String parts(Subject subject, Integer firstMinutes, Integer secondMinutes) {
        return """
                [{"id":"p1","order":1,"name":"One",%s"questionCount":10,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s1","subjectCode":"%s","questionCount":10,
                    "blocks":[{"id":"single-1","name":"Singles","questionType":"SINGLE_CHOICE","questionCount":8},
                              {"id":"set-1","name":"Set","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2}]}]},
                 {"id":"p2","order":2,"name":"Two",%s"questionCount":10,"participation":{"mode":"ALL"},
                  "sections":[{"id":"s2","subjectCode":"%s","questionCount":10,
                    "blocks":[{"id":"single-2","name":"Singles","questionType":"SINGLE_CHOICE","questionCount":8},
                              {"id":"set-2","name":"Set","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2}]}]}]
                """.formatted(minutes(firstMinutes), subject.code(), minutes(secondMinutes), subject.code());
    }

    private static String minutes(Integer value) {
        return value == null ? "" : "\"durationMinutes\":" + value + ",";
    }

    private UUID orderedExam() {
        return exam("SHARED", 60, subject -> parts(subject, null, null));
    }

    private UUID exam(String timing, Integer durationMinutes, java.util.function.Function<Subject, String> structure) {
        Subject subject = subject();
        for (int i = 0; i < 20; i++) singleChoice(subject);
        for (int i = 0; i < 2; i++) questionSet(subject);
        String parts = structure.apply(subject);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes) "
                + "values (?, ?, ?, 'Preview port', 'FULL', ?, ?)", id, id.toString(), id.toString(), timing,
                durationMinutes);
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, "{\"parts\":" + parts + "}");
        jdbc.update("update exam.exam set question_count = 20 where id = ?", id);
        return id;
    }
}
