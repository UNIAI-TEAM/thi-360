package vn.unicom.thi360.identity.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.identity.domain.EmailCode;
import vn.unicom.thi360.identity.domain.EmailCodePurpose;

public interface EmailCodeRepository extends JpaRepository<EmailCode, UUID> {

    @Query("""
            SELECT c FROM EmailCode c
            WHERE c.userId = :userId AND c.purpose = :purpose AND c.usedAt IS NULL
            """)
    Optional<EmailCode> findLive(@Param("userId") UUID userId, @Param("purpose") EmailCodePurpose purpose);

    /** Thời điểm gửi các mã trong cửa sổ, mới nhất trước (kể cả mã đã dùng hoặc đã bị thay thế). */
    @Query("""
            SELECT c.createdAt FROM EmailCode c
            WHERE c.userId = :userId AND c.purpose = :purpose AND c.createdAt > :since
            ORDER BY c.createdAt DESC
            """)
    List<Instant> findCreatedSince(
            @Param("userId") UUID userId,
            @Param("purpose") EmailCodePurpose purpose,
            @Param("since") Instant since);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM EmailCode c WHERE c.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
