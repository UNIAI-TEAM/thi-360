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
}
