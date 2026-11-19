package vn.unicom.thi360.commerce.entitlement.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementRepository;
import vn.unicom.thi360.identity.spi.AccountDeletionGuard;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * An account that has an {@code entitlement} row cannot be deleted (TM 8.5: it holds paid or granted attempts).
 * CM-3a adds "has an order" to the commerce guard. The check is one {@code EXISTS} on {@code user_id}, which the
 * leading column of the unique index {@code uq_entitlement_scope} serves, so a bulk delete that asks for every
 * selected account stays an index lookup per account.
 */
@Component
public class EntitlementDeletionGuard implements AccountDeletionGuard {

    static final String SOURCE = "COMMERCE";
    static final String CODE = "USER_HAS_TRANSACTIONS";

    private final EntitlementRepository entitlementRepository;
    private final AppMessages messages;

    public EntitlementDeletionGuard(EntitlementRepository entitlementRepository, AppMessages messages) {
        this.entitlementRepository = entitlementRepository;
        this.messages = messages;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Blocker> check(UUID userId) {
        if (!entitlementRepository.existsByUserId(userId)) {
            return List.of();
        }
        return List.of(new Blocker(SOURCE, CODE, messages.get(CODE)));
    }
}
