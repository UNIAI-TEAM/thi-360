package vn.unicom.thi360.questionbank.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.api.ConflictException;

/**
 * Truy vấn thuần SQL của module câu hỏi (plan B: JdbcTemplate cho DAO thô): cấp mã
 * {@code CH-nnnnnn} từ sequence và revision nguồn theo môn ({@code question_source_revision},
 * spec 4.1 / D14).
 */
@Component
public class QuestionSupportDao {

    private final JdbcTemplate jdbcTemplate;

    public QuestionSupportDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Mã câu tiếp theo, dạng {@code CH-000123} (spec 4.1). */
    public String nextQuestionCode() {
        Long next = jdbcTemplate.queryForObject(
                "SELECT nextval('questionbank.question_code_seq')", Long.class);
        return "CH-" + String.format("%06d", next == null ? 0L : next);
    }

    /** Claim both serving and working codes before publishing either one. */
    public void reserveSourceCodes(UUID questionId, String servingCode, String workingCode) {
        String serving = normalized(servingCode);
        String working = normalized(workingCode);
        for (String code : java.util.stream.Stream.of(serving, working).filter(java.util.Objects::nonNull).distinct().sorted().toList()) {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO questionbank.question_source_code_claim (source_code_normalized, question_id)
                    VALUES (?, ?) ON CONFLICT (source_code_normalized)
                    DO UPDATE SET question_id = EXCLUDED.question_id
                    WHERE questionbank.question_source_code_claim.question_id = EXCLUDED.question_id
                    """, code, questionId);
            if (inserted == 0) {
                throw new ConflictException("QUESTION_SOURCE_CODE_DUPLICATE");
            }
        }
        jdbcTemplate.update("""
                DELETE FROM questionbank.question_source_code_claim
                WHERE question_id = ? AND source_code_normalized <> COALESCE(?, '')
                  AND source_code_normalized <> COALESCE(?, '')
                """, questionId, serving, working);
    }

    public void releaseSourceCodes(UUID questionId) {
        jdbcTemplate.update("DELETE FROM questionbank.question_source_code_claim WHERE question_id = ?", questionId);
    }

    private static String normalized(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Tăng revision nguồn của một môn (mọi thay đổi của tập câu Sẵn sàng, spec 4.1).
     * Chạy trong cùng transaction của lệnh ghi; gọi với {@code null} là no-op.
     */
    public void bumpSourceRevision(UUID subjectItemId) {
        if (subjectItemId == null) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO questionbank.question_source_revision (subject_item_id, revision)
                VALUES (?, 1)
                ON CONFLICT (subject_item_id)
                DO UPDATE SET revision = questionbank.question_source_revision.revision + 1
                """,
                subjectItemId);
    }

    /** Revision nguồn hiện tại của các môn (môn chưa từng đổi trả 0). */
    public Map<UUID, Long> sourceRevisions(Collection<UUID> subjectItemIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (subjectItemIds == null || subjectItemIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(", ", subjectItemIds.stream().map(id -> "?").toList());
        jdbcTemplate.query(
                "SELECT subject_item_id, revision FROM questionbank.question_source_revision"
                        + " WHERE subject_item_id IN (" + placeholders + ")",
                rs -> {
                    result.put(rs.getObject(1, UUID.class), rs.getLong(2));
                },
                subjectItemIds.toArray());
        for (UUID id : subjectItemIds) {
            result.putIfAbsent(id, 0L);
        }
        return result;
    }
}
