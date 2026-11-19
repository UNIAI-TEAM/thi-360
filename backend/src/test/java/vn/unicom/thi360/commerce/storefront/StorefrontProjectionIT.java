package vn.unicom.thi360.commerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSort;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.ListQuery;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Combination;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;

/** CM-1 AC-7 to AC-12 and AC-22: the handlers of ExamPublished and ExamDiscontinued over a fake exam port. */
class StorefrontProjectionIT extends AbstractStorefrontIT {

    @Autowired StorefrontProductRepository repository;

    @Test
    void replayedEventChangesNothing() {
        TestExam exam = exam();
        serve(exam);

        UUID event = emit(ExamEventTopics.PUBLISHED, exam.examId, 1);
        String first = rowJson(exam.examId);

        // The same event delivered again (the outbox is at-least-once): the very same row is redelivered.
        jdbc.update("UPDATE shared.outbox_event SET processed_at = NULL, attempts = 0, next_attempt_at = now() WHERE id = ?",
                event);
        drain();
        assertThat(rowJson(exam.examId)).as("the row, updated_at included, is untouched").isEqualTo(first);

        // A second event with the same body, too.
        emit(ExamEventTopics.PUBLISHED, exam.examId, 1);
        assertThat(rowJson(exam.examId)).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.storefront_product WHERE exam_id = ?", Long.class,
                exam.examId)).isEqualTo(1L);
    }

    @Test
    void olderEventDoesNotMoveVersionBack() {
        TestExam v2 = exam().asVersion(2, "Ten phien ban 2", 250_000L);
        serve(v2);
        emit(ExamEventTopics.PUBLISHED, v2.examId, 2);
        assertThat(row(v2.examId)).containsEntry("version_no", 2).containsEntry("name", "Ten phien ban 2");

        // A late event of version 1 finds the port answering from an older read: the row must not go back.
        TestExam staleRead = v2.asVersion(1, "Ten phien ban 1", 100_000L);
        serve(staleRead);
        emit(ExamEventTopics.PUBLISHED, v2.examId, 1);

        Map<String, Object> row = row(v2.examId);
        assertThat(row.get("version_no")).isEqualTo(2);
        assertThat(row.get("name")).isEqualTo("Ten phien ban 2");
        assertThat(row.get("list_price_vnd")).isEqualTo(250_000L);
    }

    @Test
    void discontinuedEventHidesProduct() {
        TestExam exam = exam();
        String partCode = exam.defaultPartCode();
        serve(exam);
        long onSaleBefore = onSaleOfKind("HSA");
        emit(ExamEventTopics.PUBLISHED, exam.examId, 1);

        assertThat(listed(exam.code)).as("on sale: listed").isEqualTo(1);
        assertThat(onSaleOfKind("HSA")).isEqualTo(onSaleBefore + 1);
        assertThat(repository.countByPart()).anyMatch(p -> p.kindCode().equals("HSA") && p.code().equals(partCode));

        serve(exam.asVersion(1, exam.name, exam.listPrice).discontinued());
        emit(ExamEventTopics.DISCONTINUED, exam.examId, 1);

        assertThat(row(exam.examId)).containsEntry("sale_state", "DISCONTINUED");
        assertThat(listed(exam.code)).as("discontinued: not listed").isZero();
        assertThat(onSaleOfKind("HSA")).as("and not counted by the filters").isEqualTo(onSaleBefore);
        assertThat(repository.countByPart()).noneMatch(p -> p.code().equals(partCode));
    }

    @Test
    void listingComesFromLatestServingVersion() throws Exception {
        // Version 3 is the latest that serves a path; path "older" still serves on version 2 (E24, BCA, Informatics).
        TestExam exam = exam().withParts(
                List.of(TestExam.part("p1", 1, "Phan 1", "TOAN", 50, 75, 50)),
                List.of(TestExam.path("default", 3, 50, 50, 75, "p1", "Phan 1"),
                        TestExam.path("older", 2, 48, 48, 70, "p1", "Phan 1")));
        exam.versionNo = 3;
        exam.name = "Ten phien ban 3";
        exam.listPrice = 300_000L;
        exam.attempts = 7;
        serve(exam);

        emit(ExamEventTopics.PUBLISHED, exam.examId, 3);

        Map<String, Object> row = row(exam.examId);
        assertThat(row.get("version_no")).isEqualTo(3);
        assertThat(row.get("name")).isEqualTo("Ten phien ban 3");
        assertThat(row.get("list_price_vnd")).isEqualTo(300_000L);
        assertThat(row.get("attempts_granted")).isEqualTo(7);
        JsonNode card = json(row.get("card").toString());
        assertThat(card.path("versionNo").asInt()).isEqualTo(3);
        assertThat(card.path("name").asText()).isEqualTo("Ten phien ban 3");
        assertThat(card.path("attemptsGranted").asInt()).isEqualTo(7);
        assertThat(card.path("listPrice").asLong()).isEqualTo(300_000L);
        assertThat(json(row.get("detail").toString()).path("versionNo").asInt()).isEqualTo(3);
    }

    @Test
    void pathsDifferReturnsRange() throws Exception {
        TestExam exam = exam().withParts(
                List.of(TestExam.part("p1", 1, "Phan 1", "TOAN", 50, 75, 50)),
                List.of(TestExam.path("a", 1, 48, 48, 90, "p1", "Phan 1"),
                        TestExam.path("b", 1, 50, 50, 95, "p1", "Phan 1"),
                        // A path whose snapshot could not total itself says nothing about the range.
                        TestExam.path("c", 1, null, null, null, "p1", "Phan 1")));
        serve(exam);

        emit(ExamEventTopics.PUBLISHED, exam.examId, 1);

        Map<String, Object> row = row(exam.examId);
        assertThat(row.get("question_count")).isEqualTo(48);
        assertThat(row.get("question_count_max")).isEqualTo(50);
        assertThat(row.get("duration_minutes")).isEqualTo(90);
        assertThat(row.get("duration_minutes_max")).isEqualTo(95);
        assertThat((java.math.BigDecimal) row.get("max_points")).isEqualByComparingTo("48");
        assertThat((java.math.BigDecimal) row.get("max_points_max")).isEqualByComparingTo("50");
        JsonNode card = json(row.get("card").toString());
        assertThat(card.path("questionCount").path("min").asInt()).isEqualTo(48);
        assertThat(card.path("questionCount").path("max").asInt()).isEqualTo(50);
        assertThat(card.path("durationMinutes").path("min").asInt()).isEqualTo(90);
        assertThat(card.path("durationMinutes").path("max").asInt()).isEqualTo(95);

        // Paths that agree give a single value, and the part shows the same range in the detail.
        TestExam same = exam();
        serve(same);
        emit(ExamEventTopics.PUBLISHED, same.examId, 1);
        JsonNode sameCard = json(row(same.examId).get("card").toString());
        assertThat(sameCard.path("questionCount").path("min").asInt()).isEqualTo(50);
        assertThat(sameCard.path("questionCount").path("max").asInt()).isEqualTo(50);
        JsonNode detailPart = json(row.get("detail").toString()).path("parts").get(0);
        assertThat(detailPart.path("questionCount").path("min").asInt()).isEqualTo(48);
        assertThat(detailPart.path("questionCount").path("max").asInt()).isEqualTo(50);
    }

    @Test
    void failingEventDoesNotBlockQueue() {
        TestExam failing = exam();
        TestExam healthy = exam();
        serve(failing);
        serve(healthy);
        port.failFor(failing.examId);

        UUID failingEvent = publishEvent(ExamEventTopics.PUBLISHED, failing.examId, 1);
        UUID healthyEvent = publishEvent(ExamEventTopics.PUBLISHED, healthy.examId, 1);
        drain();

        // The event behind the failing one is processed in the same run.
        assertThat(hasRow(healthy.examId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT processed_at IS NOT NULL FROM shared.outbox_event WHERE id = ?",
                Boolean.class, healthyEvent)).isTrue();
        // The failing one has its attempt and its retry time recorded (the relay's own transaction was not poisoned).
        Map<String, Object> failed = jdbc.queryForMap(
                "SELECT attempts, last_error, processed_at, failed_at, next_attempt_at FROM shared.outbox_event WHERE id = ?",
                failingEvent);
        assertThat(failed.get("attempts")).isEqualTo(1);
        assertThat((String) failed.get("last_error")).contains("exam module unavailable");
        assertThat(failed.get("processed_at")).isNull();
        assertThat(failed.get("failed_at")).as("a retry is scheduled, it is not dead").isNull();
        assertThat(((Timestamp) failed.get("next_attempt_at")).toInstant()).isAfter(Instant.now());
        assertThat(hasRow(failing.examId)).isFalse();

        // Once the exam module is back and the retry is due, the same event goes through.
        port.recover(failing.examId);
        jdbc.update("UPDATE shared.outbox_event SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                failingEvent);
        drain();
        assertThat(hasRow(failing.examId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT processed_at IS NOT NULL FROM shared.outbox_event WHERE id = ?",
                Boolean.class, failingEvent)).isTrue();
    }

    @Test
    void reviewExamIsSoldWithPortLabel() throws Exception {
        TestExam review = exam().scope("SUBJECT", null, subjectItem("TOAN")).review();
        TestExam standard = exam();
        serve(review);
        serve(standard);

        emit(ExamEventTopics.PUBLISHED, review.examId, 1);
        emit(ExamEventTopics.PUBLISHED, standard.examId, 1);

        assertThat(listed(review.code)).as("a published review exam is a product like a subject exam").isEqualTo(1);
        Map<String, Object> row = row(review.examId);
        assertThat(row).containsEntry("build_mode", "REVIEW").containsEntry("scope", "SUBJECT")
                .containsEntry("sale_state", "ON_SALE");
        assertThat(json(row.get("card").toString()).path("buildMode").asText()).isEqualTo("REVIEW");
        assertThat(json(row.get("detail").toString()).path("buildMode").asText()).isEqualTo("REVIEW");
        assertThat(row(standard.examId)).containsEntry("build_mode", "STANDARD");
    }

    @Test
    void subjectsAndPartsAreTakenFromServingPaths() {
        // HSA part 3: science (3 of 5 subjects) or English; QDA-like combinations come from the serving paths.
        Part part3 = TestExam.choicePart("p3", 3, "Khoa hoc hoac Tieng Anh", 50, 60, List.of(
                TestExam.option("o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc", 3, List.of(17, 17, 16),
                        List.of(TestExam.section("LY", 17), TestExam.section("HOA", 17), TestExam.section("SINH", 16),
                                TestExam.section("SU", 17), TestExam.section("DIA", 17))),
                TestExam.option("o-eng", "SUBJECT", "ANH", "Tieng Anh", null, null, List.of(TestExam.section("ANH", 50))),
                TestExam.option("o-unserved", "SUBJECT", "VAN", "Khong phuc vu", null, null,
                        List.of(TestExam.section("VAN", 50)))));
        ServingPath science = TestExam.optionPath("o-sci", 1, 100, 100, 135, "p3", "Khoa hoc hoac Tieng Anh",
                new PathChoice("p3", "o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc"));
        ServingPath english = TestExam.optionPath("o-eng", 1, 100, 100, 135, "p3", "Khoa hoc hoac Tieng Anh",
                new PathChoice("p3", "o-eng", "SUBJECT", "ANH", "Tieng Anh"));
        Combination combination = TestExam.combination("p3", "QD01", "Ma QD01", "KHTN", "TOAN", "LY");
        ServingPath combined = new ServingPath("p3:QD01", 1, List.of(new PathChoice("p3", "p3:QD01", "COMBINATION",
                "QD01", "Ma QD01")), 100, java.math.BigDecimal.valueOf(100), 135, List.of(), List.of(combination));
        TestExam exam = exam().withParts(
                List.of(TestExam.part("p1", 1, "Toan hoc", "TOAN", 50, 75, 50), part3),
                List.of(science, english, combined));
        serve(exam);

        emit(ExamEventTopics.PUBLISHED, exam.examId, 1);

        List<UUID> subjects = List.of((UUID[]) jdbcArray(row(exam.examId).get("subject_item_ids")));
        assertThat(subjects).contains(subjectItem("TOAN"), subjectItem("LY"), subjectItem("HOA"), subjectItem("SINH"),
                subjectItem("SU"), subjectItem("DIA"), subjectItem("ANH"));
        assertThat(subjects).as("an option no serving path stands for adds nothing").doesNotContain(subjectItem("VAN"));
        assertThat(List.of((String[]) jdbcArray(row(exam.examId).get("part_codes")))).containsExactly("P1", "P3");
    }

    // ------------------------------------------------------------------ helpers

    private Object[] jdbcArray(Object array) {
        try {
            return (Object[]) ((java.sql.Array) array).getArray();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** On-sale rows whose normalised code or name contains the product code (it is unique per test). */
    private long listed(String code) {
        String pattern = "%" + code.toLowerCase() + "%";
        return repository.list(new ListQuery(pattern, null, null, null, null, StorefrontSort.NEWEST, 0, 48)).total();
    }

    private long onSaleOfKind(String kindCode) {
        return repository.countByKind().stream().filter(c -> c.key().equals(kindCode)).mapToLong(c -> c.count())
                .findFirst().orElse(0L);
    }
}
