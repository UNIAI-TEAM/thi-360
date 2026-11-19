package vn.unicom.thi360.identity.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    /**
     * Vô hiệu mọi mã chưa hoàn tất của tài khoản, kể cả mã đang bị chiếm mà Keycloak chưa xác nhận (yêu cầu mới
     * hợp lệ — E16): mã bị vô hiệu không bao giờ được trả về chưa dùng. Gọi dưới khóa dòng tài khoản.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = COALESCE(t.usedAt, :now), t.invalidatedAt = :now
            WHERE t.userId = :userId
              AND t.invalidatedAt IS NULL
              AND t.passwordChangedAt IS NULL
            """)
    int invalidateAllLiveOfUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Hủy một mã (tài khoản bị khóa, hoặc email nhận liên kết đã đổi). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = COALESCE(t.usedAt, :now), t.invalidatedAt = :now
            WHERE t.id = :tokenId
              AND t.invalidatedAt IS NULL
              AND t.passwordChangedAt IS NULL
            """)
    int invalidate(@Param("tokenId") UUID tokenId, @Param("now") Instant now);

    /**
     * Chiếm mã để đặt lại: chỉ thành công khi mã còn hạn, chưa dùng và chưa bị vô hiệu (một lần dùng, đồng thời
     * an toàn — E16, TM 5.4). Trả 1 khi chiếm được, 0 khi mã đã dùng, bị vô hiệu hoặc hết hạn.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :now
            WHERE t.id = :tokenId
              AND t.usedAt IS NULL
              AND t.invalidatedAt IS NULL
              AND t.expiresAt > :now
            """)
    int claimUsable(@Param("tokenId") UUID tokenId, @Param("now") Instant now);

    /**
     * Trả mã về chưa dùng khi Keycloak từ chối rõ ràng việc đặt mật khẩu (TM 5.4 AC-21). Chỉ khi dòng vẫn là mã
     * hiện hành do chính lần chiếm này giữ: chưa bị thay/hủy và chưa đổi mật khẩu. Gọi dưới khóa dòng tài khoản.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = NULL
            WHERE t.id = :tokenId
              AND t.usedAt = :claimedAt
              AND t.invalidatedAt IS NULL
              AND t.passwordChangedAt IS NULL
            """)
    int releaseClaim(@Param("tokenId") UUID tokenId, @Param("claimedAt") Instant claimedAt);

    /** Ghi nhận Keycloak đã nhận mật khẩu mới: mã dùng xong vĩnh viễn, kể cả khi đã bị thay trong lúc gọi. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.passwordChangedAt = :now
            WHERE t.id = :tokenId
              AND t.passwordChangedAt IS NULL
            """)
    int markPasswordChanged(@Param("tokenId") UUID tokenId, @Param("now") Instant now);

    /** Ghi nhận phiên cũ đã bị thu hồi sau khi đổi mật khẩu. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.sessionsRevokedAt = :now
            WHERE t.id = :tokenId
              AND t.sessionsRevokedAt IS NULL
            """)
    int markSessionsRevoked(@Param("tokenId") UUID tokenId, @Param("now") Instant now);

    /** Các mã đã đổi mật khẩu mà phiên cũ chưa thu hồi xong, cũ nhất trước (job thử lại). */
    @Query("""
            SELECT t FROM PasswordResetToken t
            WHERE t.passwordChangedAt IS NOT NULL
              AND t.sessionsRevokedAt IS NULL
              AND t.passwordChangedAt > :since
            ORDER BY t.passwordChangedAt ASC
            """)
    List<PasswordResetToken> findPendingRevocations(@Param("since") Instant since, Pageable pageable);

    /**
     * Dấu ghi trước: commit (transaction riêng) trước khi gọi Keycloak. Chỉ khi dòng vẫn do lần chiếm này giữ và chưa
     * bị yêu cầu mới vô hiệu; trả 0 thì không được ghi mật khẩu.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.passwordWriteStartedAt = :now
            WHERE t.id = :tokenId
              AND t.usedAt = :claimedAt
              AND t.invalidatedAt IS NULL
              AND t.passwordWriteStartedAt IS NULL
              AND t.passwordChangedAt IS NULL
            """)
    int markWriteStarted(
            @Param("tokenId") UUID tokenId, @Param("claimedAt") Instant claimedAt, @Param("now") Instant now);

    /** Keycloak từ chối rõ ràng: chắc chắn chưa ghi, xóa dấu ghi trước (kể cả khi mã đã bị vô hiệu trong lúc gọi). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.passwordWriteStartedAt = NULL
            WHERE t.id = :tokenId
              AND t.passwordWriteStartedAt IS NOT NULL
              AND t.passwordChangedAt IS NULL
              AND t.finalizedAt IS NULL
            """)
    int clearWriteMark(@Param("tokenId") UUID tokenId);

    /** Đánh dấu đã ghi audit {@code PASSWORD_RESET}; trả 1 chỉ cho người ghi audit (không ghi hai lần). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.finalizedAt = :now
            WHERE t.id = :tokenId
              AND t.finalizedAt IS NULL
            """)
    int markFinalized(@Param("tokenId") UUID tokenId, @Param("now") Instant now);

    /** Ghi nhận một lần đối soát (thành công hay không) để dòng kẹt không làm đói dòng mới. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.reconcileAttemptedAt = :now WHERE t.id = :tokenId")
    int markReconcileAttempted(@Param("tokenId") UUID tokenId, @Param("now") Instant now);

    /**
     * Các lần ghi mật khẩu có dấu ghi trước mà chưa hoàn tất (chưa có audit), cũ hơn {@code startedBefore}: kết quả
     * Keycloak không biết (job đối soát). Không có mốc tuổi tối đa: một hành động bảo mật chưa xong không bị bỏ.
     */
    @Query("""
            SELECT t FROM PasswordResetToken t
            WHERE t.passwordWriteStartedAt IS NOT NULL
              AND t.finalizedAt IS NULL
              AND t.passwordWriteStartedAt < :startedBefore
            ORDER BY COALESCE(t.reconcileAttemptedAt, t.passwordWriteStartedAt) ASC
            """)
    List<PasswordResetToken> findUnfinalizedWrites(
            @Param("startedBefore") Instant startedBefore, Pageable pageable);
}
