package vn.unicom.thi360.shared.security;

import java.util.UUID;

public interface SessionAuthzResolver {

    String AUTHZ_KEY_PREFIX = "authz:";

    AuthzSnapshot resolve(UUID userId, Realm realm);

    default String cacheKey(UUID userId) {
        return AUTHZ_KEY_PREFIX + userId;
    }

    default AuthzSnapshot resolveFromCached(UUID userId, Realm realm, String rawCachedSnapshot) {
        return resolve(userId, realm);
    }

    record AuthzSnapshot(Access access, PermissionSet permissions, long version) {
    }
}
