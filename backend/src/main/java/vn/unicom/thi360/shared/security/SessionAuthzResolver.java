package vn.unicom.thi360.shared.security;

import java.util.UUID;

public interface SessionAuthzResolver {

    AuthzSnapshot resolve(UUID userId, Realm realm);

    record AuthzSnapshot(Access access, PermissionSet permissions, long version) {
    }
}
