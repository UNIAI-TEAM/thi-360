package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.application.AccountSelfDeletionService;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

@RestController
@RequestMapping(value = "/api/v1/me/account", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Me", description = "Tài khoản đang đăng nhập")
public class MeAccountDeletionController {

    private final AccountSelfDeletionService accountSelfDeletionService;
    private final CurrentUserProvider currentUserProvider;

    public MeAccountDeletionController(
            AccountSelfDeletionService accountSelfDeletionService,
            CurrentUserProvider currentUserProvider) {
        this.accountSelfDeletionService = accountSelfDeletionService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/deletion-check")
    @Operation(operationId = "checkMyAccountDeletion", summary = "Kiểm tra điều kiện tự xóa tài khoản")
    @ApiResponse(responseCode = "200", description = "Kết quả kiểm tra điều kiện tự xóa")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Tài khoản không được phép tự xóa (ACCOUNT_DELETION_NOT_ALLOWED)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AccountDeletionCheckResponse check() {
        return accountSelfDeletionService.check(currentUserProvider.requireCurrentUser());
    }

    @PostMapping(value = "/deletion", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "deleteMyAccount", summary = "Học viên tự xóa tài khoản")
    @ApiResponse(responseCode = "204", description = "Tài khoản đã được xóa")
    @ApiResponse(responseCode = "400", description = "WRONG_OLD_PASSWORD hoặc VALIDATION_FAILED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Tài khoản không được phép tự xóa (ACCOUNT_DELETION_NOT_ALLOWED)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Còn điều kiện chặn tự xóa (ACCOUNT_DELETION_BLOCKED)",
            content = @Content(schema = @Schema(implementation = AccountDeletionBlockedResponse.class)))
    @ApiResponse(responseCode = "429", description = "Tài khoản đang bị tạm khóa đăng nhập (LOGIN_THROTTLED)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> delete(@Valid @RequestBody AccountDeletionRequest request) {
        accountSelfDeletionService.delete(currentUserProvider.requireCurrentUser(), request.currentPassword());
        return ResponseEntity.noContent().build();
    }
}
