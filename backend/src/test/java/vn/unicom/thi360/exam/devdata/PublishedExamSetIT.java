package vn.unicom.thi360.exam.devdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.questionbank.application.DevQuestionAssetSeeder;
import vn.unicom.thi360.questionbank.devdata.PublishedQuestionPoolBuilder;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.support.PublishedExamSetSupport;
import vn.unicom.thi360.support.PublishedExamSetSupport.Built;

/**
 * The published exam set (DS) in a database of its own: every exam published through the real services, each with a
 * valid check and a serving version. The set is built once per JVM by {@link PublishedExamSetSupport}.
 */
class PublishedExamSetIT extends AbstractIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PublishedExamPort port;
    @Autowired QuestionSourcePort source;
    @Autowired ObjectStorage storage;

    private PublishedExamSetDefinition definition;
    private Built built;

    @BeforeEach
    void build() {
        built = PublishedExamSetSupport.ensureBuilt(context);
        definition = PublishedExamSetDefinition.load(mapper);
    }

    @Test
    void everyExamIsPublishedWithValidCheck() throws Exception {
        for (var exam : definition.exams()) {
            UUID id = examId(exam.code());
            String expected = exam.code().equals(definition.discontinuedExam()) ? "DISCONTINUED" : "PUBLISHED";
            assertThat(jdbc.queryForObject("SELECT status FROM exam.exam WHERE id = ?", String.class, id))
                    .as("status of %s", exam.code()).isEqualTo(expected);
            JsonNode check = mapper.readTree(jdbc.queryForObject(
                    "SELECT result::text FROM exam.exam_check_run WHERE exam_id = ?", String.class, id));
            assertThat(check.path("status").asText()).as("check of %s", exam.code()).isEqualTo("VALID");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version v JOIN exam.exam_check_run c "
                    + "ON c.id = v.check_run_id WHERE v.exam_id = ? AND v.state = 'SERVING'", Integer.class, id))
                    .as("serving version of %s", exam.code()).isPositive();
            assertThat(port.findServing(id)).as("port reads %s", exam.code()).isPresent();
        }
    }

    @Test
    void rerunCreatesNothing() {
        long exams = count("SELECT count(*) FROM exam.exam");
        long versions = count("SELECT count(*) FROM exam.exam_version");
        long questions = count("SELECT count(*) FROM questionbank.question");
        var questionReport = context.getBean(PublishedQuestionPoolBuilder.class).build();
        var examReport = context.getBean(PublishedExamSetBuilder.class).build();
        assertThat(questionReport.created()).isZero();
        assertThat(examReport.built()).isZero();
        assertThat(examReport.existing()).isEqualTo(definition.exams().size());
        assertThat(count("SELECT count(*) FROM exam.exam")).isEqualTo(exams);
        assertThat(count("SELECT count(*) FROM exam.exam_version")).isEqualTo(versions);
        assertThat(count("SELECT count(*) FROM questionbank.question")).isEqualTo(questions);
    }

    @Test
    void resumesAfterPartialRun() {
        // A run that stopped leaves a complete exam, a draft, or nothing: take one exam back to a draft and remove another.
        UUID draft = examId("DS-SPT-INFORMATICS-MINI");
        UUID gone = examId("DS-THPTQG-INFORMATICS-MINI");
        jdbc.update("DELETE FROM exam.exam_version WHERE exam_id IN (?, ?)", draft, gone);
        jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id IN (?, ?) AND version_no IS NOT NULL", draft, gone);
        jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", gone);
        jdbc.update("DELETE FROM exam.exam WHERE id = ?", gone);
        jdbc.update("UPDATE exam.exam SET status = 'DRAFT', serving_version = NULL, has_unpublished_changes = true "
                + "WHERE id = ?", draft);
        long exams = count("SELECT count(*) FROM exam.exam WHERE code LIKE 'DS-%'");

        PublishedExamSetBuilder.Report report;
        try {
            report = context.getBean(PublishedExamSetBuilder.class).build();
        } finally {
            // Leave the shared database complete for the other cases whatever happened.
            context.getBean(PublishedExamSetBuilder.class).build();
        }

        assertThat(report.built()).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM exam.exam WHERE code LIKE 'DS-%'")).isEqualTo(exams + 1);
        for (String code : List.of("DS-SPT-INFORMATICS-MINI", "DS-THPTQG-INFORMATICS-MINI")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam WHERE code = ? AND status = 'PUBLISHED'",
                    Integer.class, code)).as(code).isEqualTo(1);
            assertThat(port.findServing(examId(code))).as(code).isPresent();
        }
        assertThat(count("SELECT count(*) FROM (SELECT code FROM exam.exam GROUP BY code HAVING count(*) > 1) d")).isZero();
    }

    @Test
    void buildsWithinSixtySeconds() {
        assertThat(built.totalMillis()).as("questions %d ms, exams %d ms", built.questionMillis(), built.examMillis())
                .isLessThan(60_000L);
    }

    @Test
    void fullExamsServeEveryPath() throws Exception {
        ServingExam hsa = serving("DS-HSA-FULL");
        assertThat(hsa.servingPaths()).extracting(path -> path.choices().getFirst().code())
                .containsExactlyInAnyOrder("SCIENCE", "ANH");
        PublishedExamPort.Option science = hsa.version().parts().get(2).options().stream()
                .filter(option -> "SCIENCE".equals(option.code())).findFirst().orElseThrow();
        assertThat(science.countsByPick()).containsExactly(17, 17, 16);
        assertThat(science.sections()).extracting(PublishedExamPort.Section::subjectCode)
                .containsExactlyInAnyOrder("LY", "HOA", "SINH", "SU", "DIA");
        assertThat(science.sections()).allSatisfy(section -> assertThat(section.questionCount()).isEqualTo(17));
        assertThat(hsa.servingPaths()).allSatisfy(path -> assertThat(path.questionCount()).isEqualTo(150));

        ServingExam qda = serving("DS-QDA-FULL");
        Set<String> codes = qda.servingPaths().stream().flatMap(path -> path.combinations().stream())
                .map(PublishedExamPort.Combination::code).collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrder("QD01", "QD02", "QD03", "QD04", "QD05");
    }

    @Test
    void bcaFullHasManualGrading() throws Exception {
        UUID id = examId("DS-BCA-FULL");
        assertThat(jdbc.queryForObject("SELECT result::text FROM exam.exam_check_run WHERE exam_id = ?", String.class, id))
                .contains("MANUAL_GRADING_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT parts::text FROM exam.exam_version WHERE exam_id = ? ORDER BY version_no "
                + "DESC LIMIT 1", String.class, id)).contains("\"questionType\": \"ESSAY\"");
    }

    @Test
    void partialScopesFollowTheirTemplates() {
        record Expect(String exam, String template, int questions, String points) { }
        for (Expect expect : List.of(new Expect("DS-VACT-PART-LANGUAGE", "VACT_PART_LANGUAGE", 60, "600"),
                new Expect("DS-VACT-SUBJECT-TOAN", "VACT_SUBJECT_TOAN", 30, "300"),
                new Expect("DS-THPTQG-MATH-P1", "THPTQG_MATH_2025", 12, "3"))) {
            ServingExam serving = serving(expect.exam());
            assertThat(serving.version().scope()).as(expect.exam()).isEqualTo(
                    expect.exam().contains("PART") || expect.exam().endsWith("P1") ? "PART" : "SUBJECT");
            assertThat(jdbc.queryForObject("SELECT template_code FROM exam.exam WHERE code = ?", String.class, expect.exam()))
                    .isEqualTo(expect.template());
            var path = serving.servingPaths().getFirst();
            assertThat(path.questionCount()).as("questions of %s", expect.exam()).isEqualTo(expect.questions());
            assertThat(path.maxPoints()).as("points of %s", expect.exam()).isEqualByComparingTo(new BigDecimal(expect.points()));
        }
    }

    @Test
    void reviewExamIsPublished() {
        ServingExam review = serving(definition.reviewExam());
        assertThat(review.version().buildMode()).isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject("SELECT build_mode FROM exam.exam WHERE code = ?", String.class,
                definition.reviewExam())).isEqualTo("REVIEW");
        assertThat(review.status()).isEqualTo("PUBLISHED");
    }

    @Test
    void informaticsServesBothBranches() {
        for (String code : List.of("DS-SPT-INFORMATICS", "DS-THPTQG-INFORMATICS")) {
            assertThat(serving(code).servingPaths()).as(code).extracting(path -> path.choices().getFirst().code())
                    .containsExactlyInAnyOrder("CS", "ICT");
        }
    }

    /**
     * E24 sale states plus E26 on the actual serving listings: the price, attempts and result view the EX-1 publish
     * froze are what the constants file declares (R1-8).
     */
    @Test
    void saleStatesMatchTheConstants() throws Exception {
        ServingExam discontinued = serving(definition.discontinuedExam());
        assertThat(discontinued.status()).isEqualTo("DISCONTINUED");
        ServingExam voucher = serving(definition.voucherExam());
        assertThat(voucher.status()).isEqualTo("PUBLISHED");
        assertThat(voucher.version().listing().listPriceVnd()).isGreaterThan(0L);
        JsonNode benefits;
        try (var in = new org.springframework.core.io.ClassPathResource(PublishedExamSetDefinition.RESOURCE)
                .getInputStream()) {
            benefits = mapper.readTree(in).path("listingBenefits");
        }
        assertThat(benefits.path("aiExplanation").isBoolean()).isTrue();
        assertThat(benefits.path("strengthWeakness").isBoolean()).isTrue();
        assertThat(benefits.path("practice").isBoolean()).isTrue();
        for (var exam : definition.exams()) {
            var listing = serving(exam.code()).version().listing();
            assertThat(listing).as("serving listing of %s", exam.code()).isNotNull();
            assertThat(listing.listPriceVnd()).as("price of %s", exam.code()).isEqualTo(exam.listing().priceVnd());
            assertThat(listing.attemptsGranted()).as("attempts of %s", exam.code())
                    .isEqualTo(exam.listing().attemptsGranted());
            assertThat(listing.resultView()).as("result view of %s", exam.code())
                    .isEqualTo(exam.listing().resultView());
            assertThat(listing.benefitAiExplanation()).as("AI benefit of %s", exam.code())
                    .isEqualTo(benefits.path("aiExplanation").asBoolean());
            assertThat(listing.benefitStrengthWeakness()).as("analysis benefit of %s", exam.code())
                    .isEqualTo(benefits.path("strengthWeakness").asBoolean());
            assertThat(listing.benefitPractice()).as("practice benefit of %s", exam.code())
                    .isEqualTo(benefits.path("practice").asBoolean());
            if (listing.listPriceVnd() != null && listing.listPriceVnd() > 0) {
                assertThat(listing.listPriceVnd()).as("price of %s (E26)", exam.code())
                        .isGreaterThanOrEqualTo(10_000L);
            }
        }
    }

    @Test
    void miniCopiesExistExceptFixedCounts() {
        for (var exam : definition.exams()) {
            if (!"MAIN".equals(exam.purpose())) {
                continue;
            }
            boolean locked = jdbc.queryForObject("SELECT fixed_counts OR fixed_block_counts FROM exam.exam_template "
                    + "WHERE code = ?", Boolean.class, exam.template());
            if (locked) {
                assertThat(exam.miniCode()).as("locked template of %s has no mini", exam.code()).isNull();
                assertThat(count("SELECT count(*) FROM exam.exam WHERE code = '" + exam.code() + "-MINI'")).isZero();
                continue;
            }
            assertThat(exam.miniCode()).as("mini of %s", exam.code()).isEqualTo(exam.code() + "-MINI");
            ServingExam mini = serving(exam.miniCode());
            ServingExam main = serving(exam.code());
            assertThat(mini.status()).isEqualTo("PUBLISHED");
            assertThat(mini.servingPaths().getFirst().questionCount()).as("questions of %s", exam.miniCode())
                    .isLessThan(main.servingPaths().getFirst().questionCount());
            assertThat(mini.version().scope()).isEqualTo(main.version().scope());
        }
        assertThat(count("SELECT count(*) FROM exam.exam WHERE code = 'DS-VACT-FULL-MINI'")).isZero();
    }

    @Test
    void constantsMatchBuiltData() {
        Set<String> codes = new HashSet<>();
        for (var exam : definition.exams()) {
            codes.add(exam.code());
            assertThat(examId(exam.code())).isNotNull();
            assertThat(exam.productCode()).as("product code of %s is the exam code (TM E3)", exam.code())
                    .isEqualTo(exam.code());
        }
        assertThat(jdbc.queryForList("SELECT code FROM exam.exam WHERE code LIKE 'DS-%'", String.class))
                .as("every exam the builder made is in the constants file").containsExactlyInAnyOrderElementsOf(codes);
        for (var account : definition.accounts()) {
            assertThat(count("SELECT count(*) FROM identity.user_account WHERE username = '" + account.username() + "'"))
                    .as(account.username()).isEqualTo(1);
        }
        for (String special : List.of(definition.discontinuedExam(), definition.voucherExam(),
                definition.scoreOnlyExam(), definition.reviewExam())) {
            assertThat(codes).contains(special);
        }
    }

    @Test
    void scoreOnlyExamIsPublished() {
        ServingExam serving = serving(definition.scoreOnlyExam());
        assertThat(serving.status()).isEqualTo("PUBLISHED");
        assertThat(serving.version().listing().resultView()).isEqualTo("SCORE");
    }

    @Test
    void hscaVsatArePublished() {
        record Expect(String exam, String kind) { }
        for (Expect expect : List.of(new Expect("DS-HSCA-TOAN", "HSCA"), new Expect("DS-HSCA-ANH", "HSCA"),
                new Expect("DS-HSCA-HOA", "HSCA"), new Expect("DS-VSAT-TOAN", "VSAT"), new Expect("DS-VSAT-VAN", "VSAT"),
                new Expect("DS-VSAT-ANH", "VSAT"))) {
            ServingExam serving = serving(expect.exam());
            assertThat(serving.status()).as(expect.exam()).isEqualTo("PUBLISHED");
            assertThat(serving.version().kindCode()).as(expect.exam()).isEqualTo(expect.kind());
        }
        var vsatEnglish = serving("DS-VSAT-ANH").version().parts().getFirst().displaySegments();
        assertThat(vsatEnglish).extracting(PublishedExamPort.Segment::code).hasSize(4);
        Set<String> audio = serving("DS-HSCA-ANH").version().parts().stream()
                .flatMap(part -> part.sections().stream()).map(PublishedExamPort.Section::audioRequirement)
                .collect(Collectors.toSet());
        assertThat(audio).contains("REQUIRED", "FORBIDDEN");
    }

    @Test
    void listeningAudioIsStoredInE2eProfile() {
        Profile profile = DevQuestionAssetSeeder.class.getAnnotation(Profile.class);
        assertThat(profile.value()).contains("e2e");
        context.getBean(DevQuestionAssetSeeder.class).seed();
        List<String> keys = jdbc.queryForList("SELECT object_key FROM questionbank.question_asset "
                + "WHERE mime LIKE 'audio/%'", String.class);
        assertThat(keys).isNotEmpty();
        assertThat(keys).allSatisfy(key -> assertThat(storage.exists(key)).as(key).isTrue());
        // Every question with a listening file (the 2B sample and the ones of the set) has it in the store.
        assertThat(count("SELECT count(DISTINCT r.question_id) FROM questionbank.question_asset_ref r JOIN "
                + "questionbank.question_asset a ON a.id = r.asset_id WHERE a.mime LIKE 'audio/%'"))
                .isGreaterThanOrEqualTo(50);
    }

    @Test
    void imageQuestionHasStoredAssets() throws Exception {
        JsonNode image = mapper.readTree(new org.springframework.core.io.ClassPathResource(
                PublishedExamSetDefinition.RESOURCE).getInputStream()).path("imageQuestion");
        assertThat(serving(image.path("examCode").asText()).status()).isEqualTo("PUBLISHED");
        UUID question = jdbc.queryForObject("SELECT id FROM questionbank.question WHERE source_code = ?", UUID.class,
                image.path("questionCode").asText());
        JsonNode snapshot = mapper.readTree(jdbc.queryForObject("SELECT v.snapshot::text FROM questionbank.question q "
                + "JOIN questionbank.question_version v ON v.question_id = q.id AND v.version_no = q.current_version_no "
                + "WHERE q.id = ?", String.class, question));
        String prompt = snapshot.path("content").asText();
        String solution = snapshot.path("solution").asText();
        String option = snapshot.path("body").path("options").toString();
        for (String html : List.of(prompt, solution, option)) {
            assertThat(html).contains("data-asset-id");
        }
        Set<String> files = new HashSet<>();
        for (JsonNode asset : image.path("assets")) {
            files.add(asset.path("fileName").asText());
        }
        for (String file : files) {
            var row = jdbc.queryForMap("SELECT id, object_key FROM questionbank.question_asset WHERE file_name = ? "
                    + "AND kind = 'IMAGE'", file);
            assertThat(storage.exists((String) row.get("object_key"))).as(file).isTrue();
            String id = row.get("id").toString();
            assertThat(prompt + solution + option).as("question uses %s", file).contains(id);
        }
        // The question is a plain one-point Toan single choice: an eligible source of the Toan group of DS-HSA-SUBJECT-TOAN.
        UUID toan = jdbc.queryForObject("SELECT id FROM catalog.catalog_item WHERE code = 'TOAN' AND group_id = "
                + "'00000000-0000-7000-8100-000000000002'", UUID.class);
        var filter = new QuestionSourcePort.Filter(toan, List.of(), List.of(), QuestionType.SINGLE_CHOICE, null, null,
                List.of(), List.of(), BigDecimal.ONE, null, List.of(), List.of());
        assertThat(source.matching(filter, List.of(question))).containsExactly(question);
    }

    @Test
    void hscaChemistryAnswersAreListedInTheConstants() throws Exception {
        JsonNode chemistry = mapper.readTree(new org.springframework.core.io.ClassPathResource(
                PublishedExamSetDefinition.RESOURCE).getInputStream()).path("hscaChemistry");
        assertThat(serving(chemistry.path("examCode").asText()).status()).isEqualTo("PUBLISHED");
        int index = 0;
        for (JsonNode answer : chemistry.path("answers")) {
            String code = String.format("DS-Q-%s-%03d", chemistry.path("familyCode").asText(), ++index);
            JsonNode snapshot = mapper.readTree(jdbc.queryForObject("SELECT v.snapshot::text FROM questionbank.question q "
                    + "JOIN questionbank.question_version v ON v.question_id = q.id AND v.version_no = q.current_version_no "
                    + "WHERE q.source_code = ?", String.class, code));
            assertThat(snapshot.path("body").path("answers").get(0).asText()).as(code).isEqualTo(answer.asText());
            assertThat(snapshot.path("body").path("answerFormat").asText()).as(code)
                    .isEqualTo(chemistry.path("answerFormat").asText());
        }
        assertThat(index).isPositive();
    }

    private ServingExam serving(String code) {
        Optional<ServingExam> serving = port.findServing(examId(code));
        assertThat(serving).as("serving state of %s", code).isPresent();
        return serving.get();
    }

    private UUID examId(String code) {
        return jdbc.queryForObject("SELECT id FROM exam.exam WHERE code = ?", UUID.class, code);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
