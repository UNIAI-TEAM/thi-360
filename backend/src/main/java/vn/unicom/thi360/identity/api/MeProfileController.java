package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.ProfileService;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

/**
 * Hồ sơ của chính tài khoản đang đăng nhập (TM 5.5). Không có tham số id: mỗi tài khoản chỉ thấy và sửa hồ sơ của
 * mình, nên không gắn {@code @RequirePermission} (như {@code /me}).
 */
@RestController
@RequestMapping(value = "/api/v1/me/profile", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Profile", description = "Hồ sơ của tài khoản đang đăng nhập")
public class MeProfileController {

    private final ProfileService profileService;
    private final CurrentUserProvider currentUserProvider;

    public MeProfileController(ProfileService profileService, CurrentUserProvider currentUserProvider) {
        this.profileService = profileService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(operationId = "getMyProfile", summary = "Đọc hồ sơ của tài khoản đang đăng nhập")
    public ProfileResponse get() {
        return profileService.get(ProfileOwner.requireAccountId(currentUserProvider));
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateMyProfile", summary = "Sửa họ tên, số điện thoại của tài khoản đang đăng nhập")
    public ProfileResponse update(@RequestBody ProfileUpdateRequest request) {
        return profileService.update(ProfileOwner.requireAccountId(currentUserProvider), request);
    }
}
