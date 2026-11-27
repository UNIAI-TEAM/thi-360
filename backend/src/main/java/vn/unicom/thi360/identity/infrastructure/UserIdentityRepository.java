package vn.unicom.thi360.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.UserIdentity;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderSubject(IdentityProvider provider, String providerSubject);

    List<UserIdentity> findAllByUserIdOrderByLinkedAtAscIdAsc(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UserIdentity i WHERE i.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
