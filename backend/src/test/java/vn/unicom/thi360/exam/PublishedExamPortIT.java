package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/** EX-1 AC-21 to AC-26 and AC-38: the read gate over the published snapshot. */
class PublishedExamPortIT extends AbstractExamPublishIT {
    private static final String PLAIN = "[{\"id\":\"part\",\"options\":[{\"id\":\"choice\",\"countsByPick\":[17,17,16],\"sections\":[]}]}]";
    @Autowired PublishedExamPort port;
    @Autowired ObjectStorage storage;

    /** Inserts a version row of a hand-written snapshot; the config and parts are exactly what the port must show. */
    private void version(UUID exam, int versionNo, String state, String status, String config, String parts, String... servingPaths) {
        var paths = jdbc.execute((java.sql.Connection connection) -> connection.createArrayOf("text", servingPaths));
        jdbc.update("INSERT INTO exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, "
                + "check_run_id, state, status, published_at, published_by, discontinued_at, discontinued_by) "
                + "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), exam, versionNo, config, parts, paths, paths, UUID.randomUUID(), state, status,
                Timestamp.from(Instant.parse("2026-11-01T00:00:00Z")), UUID.randomUUID(),
                "DISCONTINUED".equals(status) ? Timestamp.from(Instant.parse("2026-11-10T00:00:00Z")) : null,
                "DISCONTINUED".equals(status) ? UUID.randomUUID() : null);
    }

    private String config(UUID kind, String name, long price) {
        return "{\"code\":\"E-X\",\"name\":\"" + name + "\",\"description\":\"Mo ta\",\"kindItemId\":\"" + kind
                + "\",\"scope\":\"FULL\",\"timing\":\"PER_PART\",\"durationMinutes\":null,\"maxScore\":1200,"
                + "\"referenceMax\":null,\"practiceScoring\":\"LINEAR\",\"buildMode\":\"REVIEW\","
                + "\"listing\":{\"summary\":\"Tom tat\",\"objectives\":\"Muc tieu\",\"audience\":\"Doi tuong\",\"listPriceVnd\":" + price
                + ",\"attemptsGranted\":3,\"benefitAiExplanation\":true,\"benefitStrengthWeakness\":false,\"benefitPractice\":true,"
                + "\"benefitNote\":\"Ghi chu\",\"resultView\":\"SCORE_AND_ANSWERS\",\"name\":\"" + name + "\",\"description\":\"Mo ta\",\"coverAssetId\":null},"
                + "\"combinations\":[{\"partId\":\"codes\",\"code\":\"QD01\",\"name\":\"QD01\",\"branch\":null,\"versionNo\":1,"
                + "\"subjects\":[{\"code\":\"TOAN\",\"name\":\"Toan\",\"position\":0},{\"code\":\"LY\",\"name\":\"Ly\",\"position\":1}]}],"
                + "\"omittedGroups\":[{\"name\":\"Tu luan\",\"points\":2.5}]}";
    }

    private static final String RICH_PARTS = """
            {"parts":[{"id":"p1","order":1,"name":"Phan 1","durationMinutes":60,"questionCount":30,"questionCountOnPaper":32,
              "questionCountRequired":30,"participation":{"mode":"ALL"},"maxPoints":600,
              "displaySegments":[{"code":"S1","name":"Doan 1","fromPosition":1,"toPosition":10,"commandCount":10,"maxPoints":100}],
              "sections":[{"id":"s1","subjectCode":"ANH","questionCount":30,"audioRequirement":"REQUIRED",
                "knowledgeScope":{"nodeIds":["x"]},"matrix":{"unit":"PERCENT"},
                "blocks":[{"id":"b1","questionType":"SINGLE_CHOICE","questionCount":30,"slots":[{"id":"sl"}],"requiredPointsPerQuestion":1}]}],
              "options":[{"id":"o1","kind":"SUBJECT","code":"ANH","name":"Tieng Anh","chooseSubjects":1,"countsByPick":[17,16],
                "sections":[{"id":"s2","subjectCode":"ANH","questionCount":17}]}],
              "paths":[{"pathId":"default","questionCount":30,"maxPoints":600,"durationMinutes":60},
                       {"pathId":"o1","questionCount":28,"maxPoints":null,"durationMinutes":null}]}]}
            """;

    private static final String BLOCK_PARTS = """
            {"parts":[{"id":"p1","order":1,"name":"Phan 1","durationMinutes":60,"questionCount":30,"questionCountOnPaper":30,
              "questionCountRequired":30,"participation":{"mode":"ALL"},"maxPoints":600,
              "sections":[{"id":"s1","subjectCode":"TOAN","questionCount":30,
                "blocks":[{"id":"b1","questionType":"SINGLE_CHOICE","questionCount":30}]}],
              "paths":[{"pathId":"default","questionCount":30,"maxPoints":600,"durationMinutes":60}]},
             {"id":"p2","order":2,"name":"Phan 2","durationMinutes":30,"questionCount":20,"questionCountOnPaper":20,
              "questionCountRequired":20,"participation":{"mode":"ALL"},"maxPoints":400,
              "sections":[{"id":"s2","subjectCode":"VAN","questionCount":20,
                "blocks":[{"id":"b2","questionType":"SHORT_ANSWER","questionCount":12},
                          {"id":"b3","questionType":"QUESTION_SET","questionCount":2,"childAnswerType":"SINGLE_CHOICE"},
                          {"id":"b4","questionType":"ESSAY","questionCount":6,"omitted":true}]}],
              "paths":[{"pathId":"default","questionCount":20,"maxPoints":400,"durationMinutes":30}]}]}
            """;

    private static final String MULTI_PATH_PARTS = """
            {"parts":[{"id":"p1","order":1,"name":"Phan 1","durationMinutes":60,"questionCount":30,"questionCountOnPaper":30,
              "questionCountRequired":30,"participation":{"mode":"CHOICE","choose":1},"maxPoints":600,
              "options":[
                {"id":"o1","kind":"SUBJECT","code":"TOAN","name":"Toan","chooseSubjects":1,
                 "sections":[{"id":"s1","subjectCode":"TOAN","questionCount":30,
                   "blocks":[{"id":"b1","questionType":"SINGLE_CHOICE","questionCount":30}]}]},
                {"id":"o2","kind":"SUBJECT","code":"VAN","name":"Van","chooseSubjects":1,
                 "sections":[{"id":"s2","subjectCode":"VAN","questionCount":30,
                   "blocks":[{"id":"b2","questionType":"ESSAY","questionCount":4},
                             {"id":"b3","questionType":"SHORT_ANSWER","questionCount":26}]}]}],
              "paths":[{"pathId":"o1","questionCount":30,"maxPoints":600,"durationMinutes":60},
                       {"pathId":"o2","questionCount":30,"maxPoints":600,"durationMinutes":60}]}]}
            """;

    private static final String DERIVED_PARTS = """
            {"parts":[{"id":"p1","order":1,"name":"Phan 1","durationMinutes":60,"questionCount":20,"questionCountOnPaper":20,
              "questionCountRequired":20,"participation":{"mode":"ALL"},"maxPoints":600,
              "sections":[{"id":"s1","subjectCode":"VAN","questionCount":20,
                "blocks":[{"id":"b1","questionType":"SINGLE_CHOICE"},
                          {"id":"b2","questionType":"QUESTION_SET","setCount":2,"setSize":4,"childAnswerType":"SINGLE_CHOICE"},
                          {"id":"b3","questionType":"SHORT_ANSWER","questionCount":2},
                          {"id":"b4","questionType":"ESSAY","questionCount":1,"omitted":true},
                          {"id":"b5","questionType":"ESSAY","questionCount":1}]},
                {"id":"s1x","subjectCode":"TOAN",
                 "blocks":[{"id":"b6","questionType":"SHORT_ANSWER"},{"id":"b7","questionType":"SINGLE_CHOICE","questionCount":5}]}],
              "paths":[{"pathId":"default","questionCount":20,"maxPoints":600,"durationMinutes":60}]}]}
            """;

    /** HSA P3 shape: science (3 of 5 subjects) or English, each option with sections of its own; part 1 stays fixed. */
    private static final String OPTION_PARTS = """
            {"parts":[{"id":"p1","order":1,"name":"Phan 1","durationMinutes":75,"questionCount":50,"questionCountOnPaper":50,
              "questionCountRequired":50,"participation":{"mode":"ALL"},"maxPoints":50,
              "sections":[{"id":"fx","subjectCode":"TOAN","questionCount":50,
                "blocks":[{"id":"fb1","questionType":"SINGLE_CHOICE","questionCount":35},
                          {"id":"fb2","questionType":"SHORT_ANSWER","questionCount":15}]}],
              "paths":[{"pathId":"default","questionCount":50,"maxPoints":50,"durationMinutes":75}]},
             {"id":"p3","order":3,"name":"Khoa hoc hoac Tieng Anh","durationMinutes":60,"questionCount":50,
              "questionCountOnPaper":50,"questionCountRequired":50,"participation":{"mode":"CHOICE","choose":1},"maxPoints":50,
              "options":[
                {"id":"o-sci","kind":"SUBJECT_CHOICE","code":"SCIENCE","name":"Khoa hoc","chooseSubjects":3,"countsByPick":[17,17,16],
                 "sections":[{"id":"sci-ly","subjectCode":"LY","questionCount":17,
                              "blocks":[{"id":"l1","questionType":"SINGLE_CHOICE"},
                                        {"id":"l2","questionType":"QUESTION_SET","setCount":1,"setSize":3},
                                        {"id":"l3","questionType":"SHORT_ANSWER","questionCount":1}]},
                             {"id":"sci-su","subjectCode":"SU","questionCount":17,
                              "blocks":[{"id":"s1","questionType":"SINGLE_CHOICE","questionCount":14},
                                        {"id":"s2","questionType":"QUESTION_SET","setCount":1,"setSize":3}]}]},
                {"id":"o-eng","kind":"SUBJECT","code":"ANH","name":"Tieng Anh",
                 "sections":[{"id":"eng","subjectCode":"ANH","questionCount":50,
                              "blocks":[{"id":"e1","questionType":"SINGLE_CHOICE","questionCount":40},
                                        {"id":"e2","questionType":"QUESTION_SET","setCount":2,"setSize":5}]}]}],
              "paths":[{"pathId":"o-sci","questionCount":50,"maxPoints":50,"durationMinutes":60},
                       {"pathId":"o-eng","questionCount":50,"maxPoints":50,"durationMinutes":60}]}]}
            """;

    /** QDA shape: a pool of subject sections; the combination code picks some of them. */
    private static final String POOL_PARTS = """
            {"parts":[{"id":"pool","order":3,"name":"Khoa hoc hoac Tieng Anh","durationMinutes":60,"questionCount":50,
              "questionCountOnPaper":50,"questionCountRequired":50,"participation":{"mode":"ALL"},"maxPoints":50,
              "subjectPool":[{"id":"pool-ly","subjectCode":"LY","questionCount":25,
                               "blocks":[{"id":"y1","questionType":"SINGLE_CHOICE","questionCount":22},
                                         {"id":"y2","questionType":"SHORT_ANSWER","questionCount":3}]},
                             {"id":"pool-anh","subjectCode":"ANH","questionCount":50,
                               "blocks":[{"id":"y3","questionType":"SINGLE_CHOICE","questionCount":50}]}],
              "paths":[{"pathId":"default","questionCount":50,"maxPoints":50,"durationMinutes":60}]}]}
            """;

    private static org.assertj.core.groups.Tuple summary(PublishedExamPort.BlockSummary b) {
        return tuple(b.questionType(), b.questionCount(), b.childAnswerType(), b.optionId(), b.sectionId(), b.omitted());
    }

    /** AD-77: the frozen blocks come out as answer types and counts, never the block internals. */
    @Test
    void partsCarryBlockSummaries() throws Exception {
        UUID kindId = kind("HSA");
        UUID exam = newExam("HSA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED' WHERE id = ?", exam);
        version(exam, 1, "SERVING", "PUBLISHED", config(kindId, "Bai on", 100000), BLOCK_PARTS, "default");
        var parts = port.loadVersion(exam, 1).orElseThrow().parts();
        assertThat(parts.getFirst().blocks()).extracting(PublishedExamPortIT::summary)
                .containsExactly(tuple("SINGLE_CHOICE", 30, null, null, "s1", false));
        assertThat(parts.get(1).blocks()).extracting(PublishedExamPortIT::summary).containsExactly(
                tuple("SHORT_ANSWER", 12, null, null, "s2", false),
                tuple("QUESTION_SET", 2, "SINGLE_CHOICE", null, "s2", false),
                tuple("ESSAY", 6, null, null, "s2", true));
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(mapper.valueToTree(port.loadVersion(exam, 1).orElseThrow()));
    }

    /**
     * R1-3: the count comes from the frozen allocation. A fixed set without {@code questionCount} is
     * {@code setCount * setSize}; the one remainder block takes the section target minus the fixed blocks, an omitted
     * block is flagged and never counts as fixed; a block whose count cannot be derived is not invented.
     */
    @Test
    void blockCountsAreDerivedFromTheFrozenAllocation() throws Exception {
        UUID kindId = kind("HSA");
        UUID exam = newExam("HSA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED' WHERE id = ?", exam);
        version(exam, 1, "SERVING", "PUBLISHED", config(kindId, "Bai on", 100000), DERIVED_PARTS, "default");
        var blocks = port.loadVersion(exam, 1).orElseThrow().parts().getFirst().blocks();
        assertThat(blocks).extracting(PublishedExamPortIT::summary).containsExactly(
                // remainder: 20 - (2 * 4 set + 2 short + 1 visible essay) = 9; the omitted essay is not fixed
                tuple("SINGLE_CHOICE", 9, null, null, "s1", false),
                tuple("QUESTION_SET", 8, "SINGLE_CHOICE", null, "s1", false),
                tuple("SHORT_ANSWER", 2, null, null, "s1", false),
                tuple("ESSAY", 1, null, null, "s1", true),
                tuple("ESSAY", 1, null, null, "s1", false),
                // a section with no target: its remainder block has no count to derive and is left out
                tuple("SINGLE_CHOICE", 5, null, null, "s1x", false));
    }

    /** AD-78: an option exam (HSA P3) keeps each option's blocks apart, with the option and section they belong to. */
    @Test
    void optionBlocksCarryTheirOptionAndSection() throws Exception {
        UUID kindId = kind("HSA");
        UUID exam = newExam("HSA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED' WHERE id = ?", exam);
        version(exam, 1, "SERVING", "PUBLISHED", config(kindId, "Bai on", 100000), OPTION_PARTS, "default", "o-sci");
        var parts = port.findServing(exam).orElseThrow().version().parts();
        assertThat(parts.getFirst().blocks()).extracting(PublishedExamPortIT::summary).containsExactly(
                tuple("SINGLE_CHOICE", 35, null, null, "fx", false),
                tuple("SHORT_ANSWER", 15, null, null, "fx", false));
        assertThat(parts.get(1).blocks()).extracting(PublishedExamPortIT::summary).containsExactly(
                tuple("SINGLE_CHOICE", 13, null, "o-sci", "sci-ly", false),
                tuple("QUESTION_SET", 3, null, "o-sci", "sci-ly", false),
                tuple("SHORT_ANSWER", 1, null, "o-sci", "sci-ly", false),
                tuple("SINGLE_CHOICE", 14, null, "o-sci", "sci-su", false),
                tuple("QUESTION_SET", 3, null, "o-sci", "sci-su", false),
                tuple("SINGLE_CHOICE", 40, null, "o-eng", "eng", false),
                tuple("QUESTION_SET", 10, null, "o-eng", "eng", false));
    }

    /** AD-78: a pool exam carries the candidate sections' blocks with their section, never as fixed content. */
    @Test
    void poolBlocksCarryTheirSection() throws Exception {
        UUID kindId = kind("QDA");
        UUID exam = newExam("QDA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED' WHERE id = ?", exam);
        version(exam, 1, "SERVING", "PUBLISHED", config(kindId, "Bai on", 100000), POOL_PARTS, "default");
        var part = port.loadVersion(exam, 1).orElseThrow().parts().getFirst();
        assertThat(part.sections()).isEmpty();
        assertThat(part.subjectPool()).extracting(PublishedExamPort.Section::id).containsExactly("pool-ly", "pool-anh");
        assertThat(part.blocks()).extracting(PublishedExamPortIT::summary).containsExactly(
                tuple("SINGLE_CHOICE", 22, null, null, "pool-ly", false),
                tuple("SHORT_ANSWER", 3, null, null, "pool-ly", false),
                tuple("SINGLE_CHOICE", 50, null, null, "pool-anh", false));
    }

    /** AD-77: a part with options summarizes the blocks of every option, not just the serving ones. */
    @Test
    void partsCarryBlockSummariesOfEveryOption() throws Exception {
        UUID kindId = kind("BCA");
        UUID exam = newExam("BCA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED' WHERE id = ?", exam);
        version(exam, 1, "SERVING", "PUBLISHED", config(kindId, "Bai on", 100000), MULTI_PATH_PARTS, "o1");
        var part = port.loadVersion(exam, 1).orElseThrow().parts().getFirst();
        assertThat(part.blocks()).extracting(PublishedExamPortIT::summary).containsExactly(
                tuple("SINGLE_CHOICE", 30, null, "o1", "s1", false),
                tuple("ESSAY", 4, null, "o2", "s2", false),
                tuple("SHORT_ANSWER", 26, null, "o2", "s2", false));
    }

    /** AD-77: a version published before the snapshot carried blocks gets an empty list, not invented data. */
    @Test
    void partsWithoutFrozenBlocksGetAnEmptyList() throws Exception {
        UUID kindId = kind("HSA");
        UUID exam = newExam("HSA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED' WHERE id = ?", exam);
        version(exam, 1, "SERVING", "PUBLISHED", config(kindId, "Bai on", 100000), CODE_PARTS.formatted(60, 30, 600, "QD01", 30, 600, 60), "default");
        assertThat(port.loadVersion(exam, 1).orElseThrow().parts().getFirst().blocks()).isEmpty();
    }

    @Test
    void findServingCarriesSaleAndPathData() throws Exception {
        UUID kindId = kind("HSCA");
        UUID exam = newExam("HSCA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED', serving_version = 1 WHERE id = ?", exam);
        version(exam, 1, "SERVING", "PUBLISHED", config(kindId, "Bai on", 150000), RICH_PARTS, "default", "o1");
        var serving = port.findServing(exam).orElseThrow();
        assertThat(serving.status()).isEqualTo("PUBLISHED");
        var version = serving.version();
        assertThat(version.versionNo()).isEqualTo(1);
        assertThat(version.kindCode()).isEqualTo("HSCA");
        assertThat(version.name()).isEqualTo("Bai on");
        assertThat(version.buildMode()).as("a review exam labels itself").isEqualTo("REVIEW");
        assertThat(version.timing()).isEqualTo("PER_PART");
        assertThat(version.maxScore()).isEqualByComparingTo("1200");
        assertThat(version.practiceScoring()).isEqualTo("LINEAR");
        assertThat(version.referenceMax()).isNull();
        assertThat(version.durationMinutes()).isNull();
        var listing = version.listing();
        assertThat(listing.listPriceVnd()).isEqualTo(150000L);
        assertThat(listing.attemptsGranted()).isEqualTo(3);
        assertThat(listing.benefitAiExplanation()).isTrue();
        assertThat(listing.benefitStrengthWeakness()).isFalse();
        assertThat(listing.resultView()).isEqualTo("SCORE_AND_ANSWERS");
        assertThat(listing.summary()).isEqualTo("Tom tat");
        var part = version.parts().getFirst();
        assertThat(part.paths()).extracting(PublishedExamPort.PartPath::pathId).containsExactly("default", "o1");
        assertThat(part.paths().getFirst().questionCount()).isEqualTo(30);
        assertThat(part.paths().getFirst().maxPoints()).isEqualByComparingTo("600");
        assertThat(part.paths().getFirst().durationMinutes()).isEqualTo(60);
        assertThat(part.paths().get(1).maxPoints()).isNull();
        assertThat(part.durationMinutes()).isEqualTo(60);
        assertThat(part.questionCountOnPaper()).isEqualTo(32);
        assertThat(part.participationMode()).isEqualTo("ALL");
        assertThat(part.options().getFirst().countsByPick()).containsExactly(17, 16);
        assertThat(version.combinations().getFirst().subjects()).extracting(PublishedExamPort.CombinationSubject::code).containsExactly("TOAN", "LY");
        assertThat(version.omittedGroups().getFirst().name()).isEqualTo("Tu luan");
        assertThat(version.omittedGroups().getFirst().points()).isEqualByComparingTo(new BigDecimal("2.5"));
        assertThat(serving.servingPaths()).extracting(PublishedExamPort.ServingPath::pathId).containsExactly("default", "o1");
        assertThat(serving.servingPaths().get(1).choices().getFirst().code()).isEqualTo("ANH");
        assertThat(port.findServing(UUID.randomUUID())).isEmpty();
        UUID draft = newExam("HSA", PLAIN);
        assertThat(port.findServing(draft)).as("a draft is not served").isEmpty();
    }

    @Test
    void loadVersionReadsImmutableSnapshot() throws Exception {
        UUID kindId = kind("HSA");
        UUID exam = newExam("HSA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED', serving_version = 2 WHERE id = ?", exam);
        version(exam, 1, "RETIRED", "PUBLISHED", config(kindId, "Ten v1", 100000), RICH_PARTS, new String[0]);
        version(exam, 2, "SERVING", "PUBLISHED", config(kindId, "Ten v2", 200000), RICH_PARTS, "default");
        jdbc.update("UPDATE exam.exam SET name = 'Ten hien tai' WHERE id = ?", exam);
        entityManager.clear();
        assertThat(port.loadVersion(exam, 1).orElseThrow().name()).isEqualTo("Ten v1");
        assertThat(port.loadVersion(exam, 1).orElseThrow().listing().listPriceVnd()).isEqualTo(100000L);
        assertThat(port.loadVersion(exam, 1).orElseThrow().state()).isEqualTo("RETIRED");
        assertThat(port.loadVersion(exam, 2).orElseThrow().listing().listPriceVnd()).isEqualTo(200000L);
        assertThat(port.loadVersion(exam, 3)).isEmpty();
        assertThat(port.loadVersion(UUID.randomUUID(), 1)).isEmpty();
        assertThat(port.findServing(exam).orElseThrow().version().versionNo()).as("the latest serving version").isEqualTo(2);
    }

    @Test
    void listsEveryPublishedExam() throws Exception {
        UUID kindId = kind("HSA");
        UUID onSale = newExam("HSA", PLAIN);
        UUID discontinued = newExam("HSA", PLAIN);
        UUID draft = newExam("HSA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED' WHERE id = ?", onSale);
        jdbc.update("UPDATE exam.exam SET status = 'DISCONTINUED' WHERE id = ?", discontinued);
        version(onSale, 1, "SERVING", "PUBLISHED", config(kindId, "A", 100000), RICH_PARTS, "default");
        version(discontinued, 1, "SERVING", "DISCONTINUED", config(kindId, "B", 100000), RICH_PARTS, "default");
        assertThat(port.listPublishedExamIds()).contains(onSale, discontinued).doesNotContain(draft);
    }

    @Test
    void discontinuedExamStaysReadable() throws Exception {
        UUID exam = newExam("HSA", PLAIN);
        saveCheck(exam, paths("default=VALID"));
        publish(exam, "default").andExpect(status().isOk());
        entityManager.clear();
        mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/discontinue").with(asUser(Permissions.EXAM_PUBLISH))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision(exam) + "}"))
                .andExpect(status().isOk());
        entityManager.clear();
        var serving = port.findServing(exam).orElseThrow();
        assertThat(serving.status()).isEqualTo("DISCONTINUED");
        assertThat(serving.version().versionNo()).isEqualTo(1);
        assertThat(serving.version().status()).isEqualTo("DISCONTINUED");
        assertThat(serving.version().listing().listPriceVnd()).isEqualTo(ExamListingFixture.PRICE_VND);
        assertThat(port.listPublishedExamIds()).contains(exam);
    }

    /** R1-7: the only "blocks" node anywhere in a port result is {@code parts[*].blocks}, and each element is a summary. */
    private static final java.util.Set<String> BLOCK_SUMMARY_KEYS =
            java.util.Set.of("questionType", "questionCount", "childAnswerType", "optionId", "sectionId", "omitted");

    private static int assertBlocksAreOnlyPartSummaries(JsonNode node, String path) {
        int summaries = 0;
        if (node.isObject()) {
            for (var entry : (Iterable<java.util.Map.Entry<String, JsonNode>>) node::fields) {
                if ("blocks".equals(entry.getKey())) {
                    assertThat(path).as("a blocks node outside version.parts[*] or parts[*]")
                            .matches("\\$(\\.version)?\\.parts\\[\\d+\\]");
                    assertThat(entry.getValue().isArray()).as("blocks at %s is a list", path).isTrue();
                    for (JsonNode block : entry.getValue()) {
                        assertThat(block.isObject()).isTrue();
                        java.util.Set<String> keys = new java.util.TreeSet<>();
                        block.fieldNames().forEachRemaining(keys::add);
                        assertThat(keys).as("keys of a block summary at %s", path).isEqualTo(BLOCK_SUMMARY_KEYS);
                        summaries++;
                    }
                }
                summaries += assertBlocksAreOnlyPartSummaries(entry.getValue(), path + "." + entry.getKey());
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) summaries += assertBlocksAreOnlyPartSummaries(node.get(i), path + "[" + i + "]");
        }
        return summaries;
    }

    @Test
    void portResultsContainNoForbiddenKeys() throws Exception {
        UUID kindId = kind("HSCA");
        UUID exam = newExam("HSCA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED' WHERE id = ?", exam);
        // The stored parts hold blocks, quotas, matrices and a witness-shaped key; none of it may come out.
        version(exam, 1, "SERVING", "PUBLISHED", config(kindId, "Bai on", 150000), RICH_PARTS, "default", "o1");
        JsonNode serving = mapper.valueToTree(port.findServing(exam).orElseThrow());
        JsonNode loaded = mapper.valueToTree(port.loadVersion(exam, 1).orElseThrow());
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(serving);
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(loaded);
        // The prior protection stays: no block internals anywhere, in either result.
        assertThat(serving.toString()).doesNotContain("knowledgeScope", "requiredPointsPerQuestion", "slots",
                "matrix", "topicQuotas", "tagCodes", "childConditions", "answerFormats");
        assertThat(loaded.toString()).doesNotContain("knowledgeScope", "requiredPointsPerQuestion", "slots",
                "matrix", "topicQuotas", "tagCodes", "childConditions", "answerFormats");
        // AD-77: the one allowed "blocks" is the summary list of parts[*]; nothing else with that name, nothing more in it.
        assertThat(assertBlocksAreOnlyPartSummaries(serving, "$")).isEqualTo(1);
        assertThat(assertBlocksAreOnlyPartSummaries(loaded, "$")).isEqualTo(1);
        for (JsonNode part : List.of(serving.path("version").path("parts").get(0), loaded.path("parts").get(0))) {
            JsonNode block = part.path("blocks").get(0);
            assertThat(part.path("blocks")).hasSize(1);
            assertThat(block.path("questionType").asText()).isEqualTo("SINGLE_CHOICE");
            assertThat(block.path("questionCount").asInt()).isEqualTo(30);
            assertThat(block.path("sectionId").asText()).isEqualTo("s1");
            assertThat(block.path("omitted").asBoolean()).isFalse();
            assertThat(block.path("optionId").isMissingNode() || block.path("optionId").isNull()).isTrue();
        }
        // A version that went through the real publish is clean too.
        UUID published = newExam("HSA", PLAIN);
        saveCheck(published, paths("default=VALID"));
        publish(published, "default").andExpect(status().isOk());
        entityManager.clear();
        for (JsonNode tree : List.<JsonNode>of(mapper.valueToTree(port.findServing(published).orElseThrow()),
                mapper.valueToTree(port.loadVersion(published, 1).orElseThrow()))) {
            ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(tree);
            assertBlocksAreOnlyPartSummaries(tree, "$");
        }
    }

    @Test
    void openCoverReturnsStoredImage() throws Exception {
        UUID exam = newExam("HSA", PLAIN);
        UUID asset = UUID.randomUUID();
        byte[] bytes = {9, 8, 7, 6};
        storage.put("exam-covers/" + asset, bytes, "image/webp");
        jdbc.update("INSERT INTO exam.exam_cover_asset (id, exam_id, object_key, content_type, size_bytes, width, height) "
                + "VALUES (?, ?, ?, 'image/webp', 4, 600, 500)", asset, exam, "exam-covers/" + asset);
        var cover = port.openCover(asset).orElseThrow();
        assertThat(cover.contentType()).isEqualTo("image/webp");
        assertThat(cover.content()).isEqualTo(bytes);
        assertThat(port.openCover(UUID.randomUUID())).isEmpty();
        UUID gone = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam_cover_asset (id, exam_id, object_key, content_type, size_bytes, width, height) "
                + "VALUES (?, ?, ?, 'image/png', 4, 600, 500)", gone, exam, "exam-covers/" + gone);
        assertThat(port.openCover(gone)).as("a row whose object is gone").isEmpty();
    }

    @Test
    void servingCarriesKindSegmentsAndAudioFromSnapshot() throws Exception {
        for (String kindCode : List.of("HSCA", "VSAT", "THPTQG")) {
            String parts = "[{\"id\":\"p1\",\"name\":\"Phan\",\"order\":1,\"displaySegments\":["
                    + "{\"code\":\"A\",\"name\":\"Doan A\",\"fromPosition\":1,\"toPosition\":5,\"commandCount\":5,\"maxPoints\":10},"
                    + "{\"code\":\"B\",\"name\":\"Doan B\",\"fromPosition\":6,\"toPosition\":9,\"commandCount\":4,\"maxPoints\":8}],"
                    + "\"sections\":[{\"id\":\"s1\",\"subjectCode\":\"ANH\",\"questionCount\":9,\"audioRequirement\":\"REQUIRED\"},"
                    + "{\"id\":\"s2\",\"subjectCode\":\"ANH\",\"questionCount\":3}],\"options\":[{\"id\":\"choice\",\"sections\":[]}]}]";
            UUID exam = newExam(kindCode, parts);
            saveCheck(exam, paths("default=VALID"));
            publish(exam, "default").andExpect(status().isOk());
            entityManager.clear();
            var before = port.findServing(exam).orElseThrow().version();
            assertThat(before.kindCode()).isEqualTo(kindCode);
            assertThat(before.parts().getFirst().displaySegments()).extracting(PublishedExamPort.Segment::code).containsExactly("A", "B");
            assertThat(before.parts().getFirst().displaySegments().get(1).fromPosition()).isEqualTo(6);
            assertThat(before.parts().getFirst().displaySegments().get(1).maxPoints()).isEqualByComparingTo("8");
            assertThat(before.parts().getFirst().sections()).extracting(PublishedExamPort.Section::audioRequirement)
                    .containsExactly("REQUIRED", "ANY");
            // The template (and the working structure) change after publish; the port keeps answering from the snapshot.
            jdbc.update("UPDATE exam.exam_structure SET parts = '{\"parts\":[]}'::jsonb WHERE exam_id = ?", exam);
            jdbc.update("UPDATE exam.exam_template SET structure = '{\"parts\":[]}'::jsonb WHERE kind_code = ?", kindCode);
            jdbc.update("UPDATE exam.exam SET name = 'Doi ten' WHERE id = ?", exam);
            entityManager.clear();
            var after = port.findServing(exam).orElseThrow().version();
            assertThat(after.parts()).isEqualTo(before.parts());
            assertThat(after.kindCode()).isEqualTo(kindCode);
            assertThat(after.name()).isEqualTo("E24 test");
        }
    }

    /** R1-10: the kind code is the one frozen at publish; the catalog is only the fallback for a version without it. */
    @Test
    void kindCodeIsReadFromTheSnapshot() throws Exception {
        UUID exam = newExam("HSA", PLAIN);
        saveCheck(exam, paths("default=VALID"));
        publish(exam, "default").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT config->>'kindCode' FROM exam.exam_version WHERE exam_id = ?", String.class, exam))
                .isEqualTo("HSA");
        // Only the snapshot decides: a frozen code that differs from the catalog item the version points at wins.
        jdbc.update("UPDATE exam.exam_version SET config = jsonb_set(config, '{kindCode}', '\"FROZEN\"') WHERE exam_id = ?", exam);
        entityManager.clear();
        assertThat(port.loadVersion(exam, 1).orElseThrow().kindCode()).isEqualTo("FROZEN");
        assertThat(port.findServing(exam).orElseThrow().version().kindCode()).isEqualTo("FROZEN");
        // A version published before kindCode was frozen falls back to the catalog.
        jdbc.update("UPDATE exam.exam_version SET config = config - 'kindCode' WHERE exam_id = ?", exam);
        entityManager.clear();
        assertThat(port.loadVersion(exam, 1).orElseThrow().kindCode()).isEqualTo("HSA");
    }

    /** R1-11: BCA can keep an older version serving another code: every serving path is reported with its own version. */
    @Test
    void servingPathsOfEveryServingVersionAreReported() throws Exception {
        UUID kindId = kind("BCA");
        UUID exam = newExam("BCA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED', serving_version = 2 WHERE id = ?", exam);
        String olderParts = RICH_PARTS.replace("{\"pathId\":\"default\",\"questionCount\":30,\"maxPoints\":600,\"durationMinutes\":60}",
                "{\"pathId\":\"default\",\"questionCount\":24,\"maxPoints\":480,\"durationMinutes\":50}");
        assertThat(olderParts).isNotEqualTo(RICH_PARTS);
        version(exam, 1, "SERVING", "PUBLISHED", config(kindId, "Ten v1", 100000), olderParts, "default");
        version(exam, 2, "SERVING", "PUBLISHED", config(kindId, "Ten v2", 200000), RICH_PARTS, "o1");
        entityManager.clear();
        var serving = port.findServing(exam).orElseThrow();
        assertThat(serving.version().versionNo()).isEqualTo(2);
        assertThat(serving.servingPaths()).extracting(PublishedExamPort.ServingPath::pathId, PublishedExamPort.ServingPath::versionNo)
                .containsExactly(org.assertj.core.api.Assertions.tuple("o1", 2), org.assertj.core.api.Assertions.tuple("default", 1));
        // The same totals are also in the older version itself (loadVersion).
        var older = port.loadVersion(exam, serving.servingPaths().get(1).versionNo()).orElseThrow();
        assertThat(older.parts().getFirst().paths().getFirst().questionCount()).isEqualTo(24);
        assertThat(older.parts().getFirst().paths().getFirst().maxPoints()).isEqualByComparingTo("480");
        assertThat(serving.version().parts().getFirst().paths().getFirst().questionCount()).isEqualTo(30);
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(mapper.valueToTree(serving));
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(mapper.valueToTree(older));
    }

    private static final String CODE_PARTS = """
            {"parts":[{"id":"codes","order":1,"name":"Phan ma de","durationMinutes":%d,"questionCount":%d,"participation":{"mode":"ALL"},
              "maxPoints":%d,"sections":[],"options":[],
              "paths":[{"pathId":"codes:%s","questionCount":%d,"maxPoints":%d,"durationMinutes":%d}]}]}
            """;

    /** R2-4 (AC-21): two disjoint combination codes on different serving versions; one findServing call has each path's totals and subjects. */
    @Test
    void findServingAloneCarriesEachPathsTotalsAndCombinationsFromItsOwnVersion() throws Exception {
        UUID kindId = kind("BCA");
        UUID exam = newExam("BCA", PLAIN);
        jdbc.update("UPDATE exam.exam SET status = 'PUBLISHED', serving_version = 2 WHERE id = ?", exam);
        String config1 = config(kindId, "Ten v1", 100000);
        String config2 = config(kindId, "Ten v2", 200000).replace("QD01", "QD02")
                .replace("\"TOAN\"", "\"HOA\"").replace("\"Toan\"", "\"Hoa\"").replace("\"LY\"", "\"SINH\"").replace("\"Ly\"", "\"Sinh\"");
        version(exam, 1, "SERVING", "PUBLISHED", config1, CODE_PARTS.formatted(50, 24, 480, "QD01", 24, 480, 50), "codes:QD01");
        version(exam, 2, "SERVING", "PUBLISHED", config2, CODE_PARTS.formatted(60, 30, 600, "QD02", 30, 600, 60), "codes:QD02");
        entityManager.clear();
        var serving = port.findServing(exam).orElseThrow();
        assertThat(serving.servingPaths()).hasSize(2);
        var second = serving.servingPaths().get(0);
        var first = serving.servingPaths().get(1);
        assertThat(second.pathId()).isEqualTo("codes:QD02");
        assertThat(second.versionNo()).isEqualTo(2);
        assertThat(second.questionCount()).isEqualTo(30);
        assertThat(second.maxPoints()).isEqualByComparingTo("600");
        assertThat(second.durationMinutes()).isEqualTo(60);
        assertThat(second.parts()).extracting(PublishedExamPort.ServingPathPart::partId, PublishedExamPort.ServingPathPart::questionCount)
                .containsExactly(org.assertj.core.api.Assertions.tuple("codes", 30));
        assertThat(second.combinations()).extracting(PublishedExamPort.Combination::code).containsExactly("QD02");
        assertThat(second.combinations().getFirst().subjects()).extracting(PublishedExamPort.CombinationSubject::code)
                .containsExactly("HOA", "SINH");
        assertThat(first.pathId()).isEqualTo("codes:QD01");
        assertThat(first.versionNo()).isEqualTo(1);
        assertThat(first.questionCount()).as("the older version's own totals, not the newest version's").isEqualTo(24);
        assertThat(first.maxPoints()).isEqualByComparingTo("480");
        assertThat(first.durationMinutes()).isEqualTo(50);
        assertThat(first.combinations()).extracting(PublishedExamPort.Combination::code).containsExactly("QD01");
        assertThat(first.combinations().getFirst().subjects()).extracting(PublishedExamPort.CombinationSubject::code)
                .containsExactly("TOAN", "LY");
        // The newest version alone carries only its own combination: the older one is not recoverable from it.
        assertThat(serving.version().combinations()).extracting(PublishedExamPort.Combination::code).containsExactly("QD02");
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(mapper.valueToTree(serving));
    }

    /** AD-121 #1: frozen instructions come from exam_version.config and do not move when the draft is later edited. */
    @Test
    void frozenInstructionsStayImmutableAfterDraftEdit() throws Exception {
        UUID exam = newExam("HSA", PLAIN);
        jdbc.update("UPDATE exam.exam SET instructions = ? WHERE id = ?", "<p>Huong dan co dinh</p>", exam);
        saveCheck(exam, paths("default=VALID"));
        publish(exam, "default").andExpect(status().isOk());
        entityManager.clear();

        jdbc.update("UPDATE exam.exam SET instructions = ? WHERE id = ?", "<p>Huong dan da sua nhap</p>", exam);
        entityManager.clear();

        var loaded = port.loadVersion(exam, 1).orElseThrow();
        var serving = port.findServing(exam).orElseThrow();
        assertThat(loaded.instructions()).isEqualTo("<p>Huong dan co dinh</p>");
        assertThat(serving.version().instructions()).isEqualTo("<p>Huong dan co dinh</p>");
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(mapper.valueToTree(serving));
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(mapper.valueToTree(loaded));
    }
}
