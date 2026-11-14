package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Hành động gán hàng loạt")
public enum BulkAuthzAction {
    GRANT,
    REVOKE
}
