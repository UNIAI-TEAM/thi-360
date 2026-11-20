package vn.unicom.thi360.commerce.promotion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.pricing.PriceQuote;
import vn.unicom.thi360.commerce.promotion.application.PromotionAdminService;
import vn.unicom.thi360.commerce.promotion.domain.PromotionKind;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.domain.PromotionTimeState;
import vn.unicom.thi360.commerce.promotion.domain.RedemptionState;
import vn.unicom.thi360.commerce.storefront.api.ProductGoneResponse;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * Admin endpoints for managing automatic promotions and vouchers (TM 7.3, BS §10).
 */
@Tag(name = "Promotions", description = "Quản trị khuyến mãi tự động và voucher")
@RestController
@RequestMapping(value = "/api/v1/admin/promotions", produces = MediaType.APPLICATION_JSON_VALUE)
public class PromotionAdminController {

    private final PromotionAdminService service;

    public PromotionAdminController(PromotionAdminService service) {
        this.service = service;
    }

    @Operation(operationId = "listPromotions", summary = "Danh sách khuyến mãi và voucher")
    @RequirePermission(Permissions.PROMOTION_VIEW)
    @GetMapping
    public PageResponse<PromotionSummary> list(
            @RequestParam(required = false) PromotionKind kind,
            @RequestParam(required = false) PromotionStatus status,
            @RequestParam(required = false) PromotionTimeState state,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.listPromotions(kind, status, state, q, page, size);
    }

    @Operation(operationId = "getPromotion", summary = "Chi tiết khuyến mãi, lượt dùng và lịch sử thay đổi")
    @RequirePermission(Permissions.PROMOTION_VIEW)
    @GetMapping("/{id}")
    public PromotionDetail get(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer redemptionPage,
            @RequestParam(required = false) Integer redemptionSize,
            @RequestParam(required = false) RedemptionState redemptionState,
            @RequestParam(required = false) String redemptionQ) {
        return service.getPromotion(id, redemptionPage, redemptionSize, redemptionState, redemptionQ);
    }

    @Operation(operationId = "createPromotion", summary = "Tạo khuyến mãi tự động hoặc voucher (idempotent)")
    @RequirePermission(Permissions.PROMOTION_MODIFY)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionDetail create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePromotionRequest request) {
        return service.createPromotion(idempotencyKey, request);
    }

    @Operation(operationId = "updatePromotion", summary = "Cập nhật khuyến mãi hoặc voucher")
    @RequirePermission(Permissions.PROMOTION_MODIFY)
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PromotionDetail update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePromotionRequest request) {
        return service.updatePromotion(id, request);
    }

    @Operation(operationId = "stopPromotion", summary = "Tạm dừng khuyến mãi hoặc voucher")
    @RequirePermission(Permissions.PROMOTION_MODIFY)
    @PostMapping(value = "/{id}/stop", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PromotionDetail stop(
            @PathVariable UUID id,
            @Valid @RequestBody PromotionRevisionRequest request) {
        return service.stopPromotion(id, request.expectedRevision());
    }

    @Operation(operationId = "resumePromotion", summary = "Mở lại khuyến mãi hoặc voucher")
    @RequirePermission(Permissions.PROMOTION_MODIFY)
    @PostMapping(value = "/{id}/resume", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PromotionDetail resume(
            @PathVariable UUID id,
            @Valid @RequestBody PromotionRevisionRequest request) {
        return service.resumePromotion(id, request.expectedRevision());
    }

    @Operation(operationId = "deletePromotion", summary = "Xóa mềm khuyến mãi hoặc voucher chưa có lượt dùng")
    @RequirePermission(Permissions.PROMOTION_MODIFY)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deletePromotion(id);
    }

    @Operation(operationId = "getSuggestedPromotionCode", summary = "Gợi ý mã voucher ngẫu nhiên 8 ký tự")
    @RequirePermission(Permissions.PROMOTION_MODIFY)
    @GetMapping("/suggested-code")
    public SuggestedCodeResponse suggestedCode() {
        return service.suggestCode();
    }

    @Operation(operationId = "previewPromotionPrice", summary = "Thử giá khuyến mãi trên một bài thi")
    @ApiResponse(responseCode = "200", description = "Kết quả thử giá",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PriceQuote.class)))
    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND hoặc PROMOTION_NOT_FOUND")
    @ApiResponse(responseCode = "410", description = "PRODUCT_DISCONTINUED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductGoneResponse.class)))
    @RequirePermission(Permissions.PROMOTION_VIEW)
    @PostMapping(value = "/price-preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PriceQuote pricePreview(@Valid @RequestBody PromotionPricePreviewRequest request) {
        return service.pricePreview(request);
    }
}
