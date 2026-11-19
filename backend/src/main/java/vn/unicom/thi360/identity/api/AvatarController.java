package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.identity.application.AvatarImageProcessor;
import vn.unicom.thi360.identity.application.AvatarRejectedException;
import vn.unicom.thi360.identity.application.AvatarRejectedException.Reason;
import vn.unicom.thi360.identity.application.AvatarService;
import vn.unicom.thi360.identity.application.ProfileService;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

/**
 * Ảnh đại diện (TM 5.5, E20). {@code PUT}, {@code DELETE} chỉ tác động lên chính tài khoản đang đăng nhập;
 * {@code GET /avatars/{assetId}} dành cho mọi tài khoản đã đăng nhập. Không gắn {@code @RequirePermission}.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Avatar", description = "Ảnh đại diện của tài khoản")
public class AvatarController {

    private final AvatarService avatarService;
    private final ProfileService profileService;
    private final CurrentUserProvider currentUserProvider;

    public AvatarController(
            AvatarService avatarService, ProfileService profileService, CurrentUserProvider currentUserProvider) {
        this.avatarService = avatarService;
        this.profileService = profileService;
        this.currentUserProvider = currentUserProvider;
    }

    @PutMapping(
            value = "/me/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Đặt ảnh đại diện (jpg, png, webp, ≤5 MB, ≤4.096 px; lưu PNG 256px)")
    public ProfileResponse replace(@RequestParam(name = "file", required = false) MultipartFile file) {
        var userId = ProfileOwner.requireAccountId(currentUserProvider);
        if (file == null || file.isEmpty()) {
            throw new AvatarRejectedException(Reason.INVALID_IMAGE, "file part is missing or empty");
        }
        if (file.getSize() > AvatarImageProcessor.MAX_BYTES) {
            throw new AvatarRejectedException(Reason.TOO_LARGE, "file exceeds the avatar size limit");
        }
        try {
            avatarService.replace(userId, file.getBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return profileService.get(userId);
    }

    @DeleteMapping("/me/avatar")
    @Operation(summary = "Xóa ảnh đại diện (không có ảnh thì không làm gì)")
    public ResponseEntity<Void> remove() {
        avatarService.remove(ProfileOwner.requireAccountId(currentUserProvider));
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/avatars/{assetId}", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Phục vụ ảnh đại diện (PNG 256px) cho tài khoản đã đăng nhập")
    @ApiResponse(
            responseCode = "200",
            content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> serve(@PathVariable("assetId") UUID assetId) {
        byte[] png = avatarService.read(assetId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(png);
    }
}
