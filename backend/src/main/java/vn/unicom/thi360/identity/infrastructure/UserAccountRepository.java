package vn.unicom.thi360.identity.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
    List<UserAccount> findAllByIdIn(Collection<UUID> ids);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE identity.user_account
                    SET id = :keycloakId,
                        status = :activeStatus,
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
}
