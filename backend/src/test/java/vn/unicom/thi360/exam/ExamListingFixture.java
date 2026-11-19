package vn.unicom.thi360.exam;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Gives an exam the sale listing that publishing now requires (EX-1): a working-copy row with a price above the
 * minimum, a number of attempts, and a description. Tests that publish exams built by SQL or from templates call
 * {@link #fill} in their preparation; tests of the listing rules build their own rows.
 */
public final class ExamListingFixture {
    public static final long PRICE_VND = 50_000L;
    public static final int ATTEMPTS = 3;

    private ExamListingFixture() { }

    /** Upserts a complete listing and fills a blank description, leaving every other exam column untouched. */
    public static void fill(JdbcTemplate jdbc, UUID examId) {
        fill(jdbc, examId, PRICE_VND);
    }

    public static void fill(JdbcTemplate jdbc, UUID examId, long priceVnd) {
        jdbc.update("INSERT INTO exam.exam_listing (exam_id, summary, list_price_vnd, attempts_granted, result_view) "
                + "VALUES (?, 'Fixture summary', ?, ?, 'SCORE_AND_ANSWERS') "
                + "ON CONFLICT (exam_id) DO UPDATE SET summary = EXCLUDED.summary, "
                + "list_price_vnd = EXCLUDED.list_price_vnd, attempts_granted = EXCLUDED.attempts_granted",
                examId, priceVnd, ATTEMPTS);
        jdbc.update("UPDATE exam.exam SET description = 'Fixture description' "
                + "WHERE id = ? AND (description IS NULL OR btrim(description) = '')", examId);
    }
}
