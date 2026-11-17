package vn.unicom.thi360;

import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * Endpoint chỉ phục vụ test (BE-1, UNI-2607): ba biến thể của {@code @RequirePermission}
 * (chỉ {@code value}, chỉ {@code anyOf}, cả hai) và một endpoint multipart để kiểm giới hạn
 * tải lên ở tầng servlet.
 */
@TestConfiguration
public class TestBe1Endpoints {

    @RestController
    @RequestMapping("/api/v1/admin/__test")
    public static class PermissionVariantsController {

        @GetMapping("/value-only")
        @RequirePermission(Permissions.QUESTION_MODIFY)
        public Map<String, String> valueOnly() {
            return Map.of("ok", "value-only");
        }

        @GetMapping("/anyof-only")
        @RequirePermission(anyOf = {Permissions.QUESTION_VIEW, Permissions.EXAM_VIEW})
        public Map<String, String> anyOfOnly() {
            return Map.of("ok", "anyof-only");
        }

        @GetMapping("/value-and-anyof")
        @RequirePermission(
                value = Permissions.USER_VIEW,
                anyOf = {Permissions.QUESTION_VIEW, Permissions.EXAM_VIEW})
        public Map<String, String> valueAndAnyOf() {
            return Map.of("ok", "value-and-anyof");
        }
    }

    @RestController
    @RequestMapping("/api/v1/admin/__test")
    public static class UploadProbeController {

        /** Trả 200 khi multipart được phân tích và điều phối tới controller. */
        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @RequirePermission(anyOf = {Permissions.QUESTION_VIEW, Permissions.EXAM_VIEW})
        public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
            return ResponseEntity.ok(Map.of(
                    "received", true,
                    "size", file.getSize()));
        }
    }

    /**
     * Lệnh ghi bắt buộc {@code Idempotency-Key}: chạy qua {@code IdempotentCommand} để test
     * replay / khóa lệch / thiếu khóa (AC-7, UNI-2607).
     */
    @RestController
    @RequestMapping("/api/v1/admin/__test")
    public static class IdempotentProbeController {

        private final vn.unicom.thi360.shared.idempotency.IdempotentCommand idempotentCommand;
        private final java.util.concurrent.atomic.AtomicInteger runCount =
                new java.util.concurrent.atomic.AtomicInteger();

        public IdempotentProbeController(
                vn.unicom.thi360.shared.idempotency.IdempotentCommand idempotentCommand) {
            this.idempotentCommand = idempotentCommand;
        }

        @PostMapping("/idempotent")
        @RequirePermission(Permissions.QUESTION_MODIFY)
        public Map<String, Object> idempotent(
                @RequestHeader(value = "Idempotency-Key", required = false) String key,
                @RequestBody Map<String, Object> body) {
            return idempotentCommand.execute(
                    key,
                    "test.idempotent",
                    body,
                    new TypeReference<Map<String, Object>>() {},
                    () -> Map.of(
                            "run", runCount.incrementAndGet(),
                            "echo", body.getOrDefault("name", "")));
        }
    }
}
