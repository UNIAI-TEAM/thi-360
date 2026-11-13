package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Loại nguồn cấp quyền")
public enum PermissionSourceType {
    ROLE,
    DIRECT
}
