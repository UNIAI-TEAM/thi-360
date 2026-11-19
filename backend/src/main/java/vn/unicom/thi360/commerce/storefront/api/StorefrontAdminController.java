package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.storefront.application.StorefrontRebuildService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Storefront", description = "Bảng chiếu sản phẩm (quản trị)")
@RestController
@RequestMapping("/api/v1/admin/storefront")
public class StorefrontAdminController {

    private final StorefrontRebuildService rebuild;

    public StorefrontAdminController(StorefrontRebuildService rebuild) {
        this.rebuild = rebuild;
    }

    @Operation(operationId = "rebuildStorefront",
            summary = "Dựng lại mọi dòng bảng chiếu từ các bài đã công bố hoặc ngừng bán",
            description = "Sửa chữa khi sự kiện công bố bị mất hoặc lỗi. Chạy lại không đổi kết quả; bài nháp không có dòng.")
    @RequirePermission(Permissions.EXAM_PUBLISH)
    @PostMapping("/rebuild")
    public StorefrontRebuildResponse rebuild() {
        return rebuild.rebuild();
    }
}
