package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Đối tượng gán hàng loạt")
public enum BulkAuthzTarget {
    ROLE,
    PERMISSION
}
