package vn.unicom.thi360.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.identity.domain.IdentityProvider;
import vn.unicom.thi360.identity.domain.UserIdentity;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderSubject(IdentityProvider provider, String providerSubject);
}
