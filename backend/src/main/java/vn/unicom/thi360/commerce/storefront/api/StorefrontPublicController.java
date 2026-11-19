package vn.unicom.thi360.commerce.storefront.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import vn.unicom.thi360.commerce.storefront.application.StorefrontQueryService;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontFormat;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.CoverImage;
import vn.unicom.thi360.shared.api.PageResponse;

/**
 * The public catalog (TM 10.2): no login, 120 requests a minute per IP (the policy of {@code RateLimitFilter}), cached for
 * 60 seconds with an {@code ETag}. A request whose {@code If-None-Match} matches gets 304.
 */
@Tag(name = "Storefront (public)", description = "Danh mục bài thi cho khách, không cần đăng nhập")
@RestController
@RequestMapping("/api/v1/public/storefront")
public class StorefrontPublicController {

    private static final CacheControl CACHE = CacheControl.maxAge(java.time.Duration.ofSeconds(60)).cachePublic();

    private final StorefrontQueryService storefront;
    private final ObjectMapper mapper;

    public StorefrontPublicController(StorefrontQueryService storefront, ObjectMapper mapper) {
        this.storefront = storefront;
        this.mapper = mapper;
    }

    @Operation(operationId = "getStorefrontFilters",
            summary = "Bộ lọc của danh mục: kỳ thi, hình thức, môn, phần, kèm số sản phẩm đang bán")
    @GetMapping("/filters")
    public ResponseEntity<StorefrontFilters> filters(WebRequest request, HttpServletResponse response) {
        return cached(request, response, storefront.filters());
    }

    @Operation(operationId = "listStorefrontProducts",
            summary = "Danh sách thẻ sản phẩm đang bán",
            description = "page tính từ 0. size mặc định 12; lớn hơn 48 thì lấy 48. q tìm theo tên không dấu và theo mã.")
    @GetMapping("/products")
    public ResponseEntity<PageResponse<ProductCard>> products(
            @Parameter(description = "Tìm theo tên (không dấu) hoặc mã") @RequestParam(required = false) String q,
            @Parameter(description = "Mã kỳ thi, ví dụ HSA") @RequestParam(required = false) String kind,
            @Parameter(description = "Hình thức") @RequestParam(required = false) StorefrontFormat format,
            @Parameter(description = "Mã môn, ví dụ TOAN") @RequestParam(required = false) String subject,
            @Parameter(description = "Mã phần; đi kèm kind vì mã phần chỉ có nghĩa trong một kỳ thi")
            @RequestParam(required = false) String part,
            @Parameter(description = "NEWEST (mặc định), PRICE_ASC, PRICE_DESC")
            @RequestParam(required = false) StorefrontSort sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            WebRequest request, HttpServletResponse response) {
        return cached(request, response, storefront.products(q, kind, format, subject, part, sort, page, size));
    }

    @Operation(operationId = "getStorefrontProduct", summary = "Chi tiết sản phẩm theo mã")
    @ApiResponse(responseCode = "200", description = "Chi tiết sản phẩm",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDetail.class)))
    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND: không có, hoặc chưa từng công bố")
    @ApiResponse(responseCode = "410", description = "PRODUCT_DISCONTINUED: đã ngừng bán, kèm product {code, name}",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductGoneResponse.class)))
    @GetMapping("/products/{code}")
    public ResponseEntity<ProductDetail> product(@PathVariable String code, WebRequest request,
            HttpServletResponse response) {
        return cached(request, response, storefront.detail(code));
    }

    @Operation(operationId = "getStorefrontCover",
            summary = "Ảnh bìa; chỉ ảnh đang là bìa của một dòng bảng chiếu, ảnh khác nhận 404")
    @ApiResponse(responseCode = "200", description = "Image bytes",
            content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/covers/{assetId}")
    public ResponseEntity<byte[]> cover(@PathVariable UUID assetId, WebRequest request, HttpServletResponse response) {
        CoverImage image = storefront.cover(assetId);
        String etag = etag(image.content());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE.getHeaderValue());
        if (request.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CACHE)
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }

    /** 200 with the body, {@code Cache-Control: public, max-age=60} and an ETag of the body; 304 when it matches. */
    private <T> ResponseEntity<T> cached(WebRequest request, HttpServletResponse response, T body) {
        String etag;
        try {
            etag = etag(mapper.writeValueAsBytes(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise storefront response", e);
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE.getHeaderValue());
        if (request.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CACHE).body(body);
    }

    private static String etag(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return "\"" + HexFormat.of().formatHex(digest, 0, 16) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
