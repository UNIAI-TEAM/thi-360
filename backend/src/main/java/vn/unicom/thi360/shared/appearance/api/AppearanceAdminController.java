package vn.unicom.thi360.shared.appearance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.shared.appearance.application.AppearanceService;
import vn.unicom.thi360.shared.security.RequirePermission;

@RestController
@RequestMapping(value = "/api/v1/admin/appearance", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin Appearance", description = "Quản trị cấu hình giao diện toàn hệ thống")
public class AppearanceAdminController {

    private static final String PERMISSION_APPEARANCE_MODIFY = "APPEARANCE.MODIFY";

    private final AppearanceService appearanceService;

    public AppearanceAdminController(AppearanceService appearanceService) {
        this.appearanceService = appearanceService;
    }

    @PutMapping
    @RequirePermission(PERMISSION_APPEARANCE_MODIFY)
    @Operation(summary = "Lưu và áp dụng cấu hình giao diện toàn hệ thống")
    public AppearanceResponse updateAppearance(@RequestBody UpdateAppearanceRequest request) {
        return appearanceService.updateAppearance(request);
    }

    @PostMapping("/reset")
    @RequirePermission(PERMISSION_APPEARANCE_MODIFY)
    @Operation(summary = "Khôi phục cấu hình giao diện mặc định")
    public AppearanceResponse resetAppearance(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true)
            @RequestBody(required = false) ResetAppearanceRequest request) {
        return appearanceService.resetAppearance(request);
    }
}
