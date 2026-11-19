package vn.unicom.thi360.shared.mail;

import io.swagger.v3.oas.annotations.Hidden;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.testapi.TestApiController;
import vn.unicom.thi360.shared.testapi.TestApiPaths;

/**
 * Cho E2E đọc thư đã xếp hàng, kể cả mã OTP trong thân (plan B6, ML). Chỉ có ở profile {@code e2e}
 * ({@link TestApiController}), không có trong OpenAPI, gọi không cần đăng nhập. Đọc thẳng từ
 * {@code shared.email_message} nên thấy thư ngay khi xếp hàng, không phụ thuộc worker SMTP.
 */
@TestApiController
@Hidden
@RequestMapping(value = TestApiPaths.PREFIX + "/mail", produces = MediaType.APPLICATION_JSON_VALUE)
class MailTestController {

    private final EmailMessageStore store;

    MailTestController(EmailMessageStore store) {
        this.store = store;
    }

    /**
     * Thư mới nhất gửi tới {@code to} thuộc loại {@code kind}.
     *
     * @throws ValidationException thiếu {@code to} hoặc {@code kind} không thuộc {@link MailKind}
     * @throws NotFoundException   chưa có thư nào khớp
     */
    @GetMapping
    public MailResponse latest(@RequestParam(required = false) String to, @RequestParam(required = false) String kind) {
        if (to == null || to.isBlank()) {
            throw invalid("to", "REQUIRED", "to is required");
        }
        MailKind mailKind = parseKind(kind);
        return store.findLatest(to.strip(), mailKind)
                .map(MailResponse::from)
                .orElseThrow(() -> new NotFoundException("No " + mailKind + " mail for the recipient"));
    }

    private static MailKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw invalid("kind", "REQUIRED", "kind is required");
        }
        try {
            return MailKind.valueOf(kind.strip());
        } catch (IllegalArgumentException e) {
            throw invalid("kind", "INVALID_KIND", "kind must be one of " + List.of(MailKind.values()));
        }
    }

    private static ValidationException invalid(String field, String code, String message) {
        return new ValidationException(message, List.of(new FieldError(field, code, message)));
    }

    public record MailResponse(
            UUID id,
            String kind,
            String to,
            String locale,
            String subject,
            String html,
            String text,
            Instant createdAt,
            Instant sentAt) {

        static MailResponse from(EmailMessageStore.StoredMail mail) {
            return new MailResponse(
                    mail.id(),
                    mail.kind(),
                    mail.to(),
                    mail.locale(),
                    mail.subject(),
                    mail.html(),
                    mail.text(),
                    mail.createdAt(),
                    mail.sentAt());
        }
    }
}
