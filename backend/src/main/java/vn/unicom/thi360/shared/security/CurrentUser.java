package vn.unicom.thi360.shared.security;

import java.util.UUID;

public record CurrentUser(
        UUID id,
        String username,
        Realm realm,
        Access access,
        PermissionSet permissions
) {
}
