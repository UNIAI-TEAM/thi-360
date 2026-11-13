package vn.unicom.thi360.shared.appearance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.shared.appearance.application.AppearanceService;

@RestController
@RequestMapping(value = "/api/v1/appearance", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Appearance", description = "Cấu hình giao diện toàn hệ thống")
public class AppearanceController {

    private static final CacheControl APPEARANCE_CACHE_CONTROL =
            CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final AppearanceService appearanceService;

    public AppearanceController(AppearanceService appearanceService) {
        this.appearanceService = appearanceService;
    }

    @GetMapping
    @Operation(summary = "Lấy cấu hình giao diện toàn hệ thống (công khai)")
    public ResponseEntity<AppearanceResponse> getAppearance(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        AppearanceResponse appearance = appearanceService.getAppearance();
        String etagToken = String.valueOf(appearance.version());
        if (matchesIfNoneMatch(ifNoneMatch, etagToken)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(APPEARANCE_CACHE_CONTROL)
                    .eTag(etagToken)
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(APPEARANCE_CACHE_CONTROL)
                .eTag(etagToken)
                .body(appearance);
    }

    static boolean matchesIfNoneMatch(String header, String versionToken) {
        if (header == null || header.isBlank()) {
            return false;
        }
        String quoted = "\"" + versionToken + "\"";
        for (String rawCandidate : header.split(",")) {
            String candidate = rawCandidate.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if ("*".equals(candidate)) {
                return true;
            }
            if (candidate.startsWith("W/") || candidate.startsWith("w/")) {
                candidate = candidate.substring(2).trim();
            }
            if (quoted.equals(candidate) || versionToken.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
}
