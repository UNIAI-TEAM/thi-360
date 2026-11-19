package vn.unicom.thi360.shared.mail;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailMessageRepository extends JpaRepository<EmailMessage, UUID> {

    /** Lấy thư đến hạn; {@code SKIP LOCKED} để hai worker không lấy cùng một dòng. */
    @Query(
            value = """
                    SELECT * FROM shared.email_message
                    WHERE sent_at IS NULL
                      AND failed_at IS NULL
                      AND next_attempt_at <= :now
                    ORDER BY next_attempt_at ASC, created_at ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<EmailMessage> findDueForUpdate(@Param("limit") int limit, @Param("now") Instant now);

    @Modifying
    @Query(
            value = "DELETE FROM shared.email_message WHERE sent_at IS NOT NULL AND sent_at < :cutoff",
            nativeQuery = true)
    int deleteSentBefore(@Param("cutoff") Instant cutoff);
}
