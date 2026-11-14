package vn.unicom.thi360.identity.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.identity.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    @Query("""
            SELECT u FROM UserAccount u
            WHERE u.usernameNormalized = :usernameNormalized
              AND u.deletedAt IS NULL
            """)
    Optional<UserAccount> findActiveByUsernameNormalized(@Param("usernameNormalized") String usernameNormalized);

    @Query("""
            SELECT u FROM UserAccount u
            WHERE u.emailNormalized = :emailNormalized
              AND u.deletedAt IS NULL
            """)
    Optional<UserAccount> findActiveByEmailNormalized(@Param("emailNormalized") String emailNormalized);

    @Query("""
            SELECT u.usernameNormalized FROM UserAccount u
            WHERE u.deletedAt IS NULL
              AND u.usernameNormalized IN :usernames
            """)
    Set<String> findActiveUsernamesNormalizedIn(@Param("usernames") Collection<String> usernames);

    @Query("""
            SELECT u.emailNormalized FROM UserAccount u
            WHERE u.deletedAt IS NULL
              AND u.emailNormalized IS NOT NULL
              AND u.emailNormalized IN :emails
            """)
    Set<String> findActiveEmailsNormalizedIn(@Param("emails") Collection<String> emails);

    @Query("""
            SELECT u FROM UserAccount u
            WHERE u.deletedAt IS NOT NULL
              AND u.idpCleanupPending = true
              AND (u.usernameNormalized = :usernameNormalized
                   OR (:emailNormalized IS NOT NULL AND u.emailNormalized = :emailNormalized))
            ORDER BY u.deletedAt DESC
            """)
    List<UserAccount> findPendingIdpCleanupByUsernameOrEmail(
            @Param("usernameNormalized") String usernameNormalized,
            @Param("emailNormalized") String emailNormalized);
    List<UserAccount> findAllByIdIn(Collection<UUID> ids);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE identity.user_account
                    SET id = :keycloakId,
                        status = :activeStatus,
                        provisioning_role_ids = NULL,
                        updated_at = :now,
                        updated_by = :actorId,
                        version = version + 1
                    WHERE id = :provisionalId
                      AND status = :provisioningStatus
                    """,
            nativeQuery = true)
    int promoteProvisionedUser(
            @Param("provisionalId") UUID provisionalId,
            @Param("keycloakId") UUID keycloakId,
            @Param("provisioningStatus") short provisioningStatus,
            @Param("activeStatus") short activeStatus,
            @Param("now") java.time.Instant now,
            @Param("actorId") UUID actorId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE identity.user_account
                    SET idp_cleanup_pending = :pending,
                        updated_at = :now
                    WHERE id = :userId
                    """,
            nativeQuery = true)
    int updateIdpCleanupPending(
            @Param("userId") UUID userId,
            @Param("pending") boolean pending,
            @Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE identity.user_account
                    SET idp_enabled_sync_pending = :pending,
                        updated_at = :now
                    WHERE id = :userId
                    """,
            nativeQuery = true)
    int updateIdpEnabledSyncPending(
            @Param("userId") UUID userId,
            @Param("pending") boolean pending,
            @Param("now") java.time.Instant now);
}
