package vn.unicom.thi360.identity.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.identity.domain.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Mã tạo sau mốc {@code since}, mới nhất trước: dữ liệu cho giới hạn E16 theo tài khoản (cooldown và hạn mức
     * trong cửa sổ trượt); mã đã vô hiệu cũng tính.
     */
    @Query("""
            SELECT t FROM PasswordResetToken t
            WHERE t.userId = :userId
              AND t.createdAt > :since
            ORDER BY t.createdAt DESC
            """)
    List<PasswordResetToken> findRecentByUser(@Param("userId") UUID userId, @Param("since") Instant since);

    /** Vô hiệu mọi mã chưa dùng của tài khoản (yêu cầu mới hợp lệ — E16). Trả số dòng bị vô hiệu. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :now
            WHERE t.userId = :userId
              AND t.usedAt IS NULL
            """)
    int invalidateAllUnusedOfUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Chiếm mã để đặt lại: chỉ thành công khi mã còn hạn và chưa dùng (một lần dùng, đồng thời
     * an toàn — E16, TM 5.4). Trả 1 khi chiếm được, 0 khi mã đã dùng hoặc hết hạn.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :now
            WHERE t.id = :tokenId
              AND t.usedAt IS NULL
              AND t.expiresAt > :now
            """)
    int claimUsable(@Param("tokenId") UUID tokenId, @Param("now") Instant now);

    /** Bỏ chiếm mã khi bước ngoài sau đó (đặt mật khẩu ở Keycloak) thất bại — TM 5.4 AC-21. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = NULL
            WHERE t.id = :tokenId
              AND t.usedAt = :claimedAt
            """)
    int releaseClaim(@Param("tokenId") UUID tokenId, @Param("claimedAt") Instant claimedAt);
}
