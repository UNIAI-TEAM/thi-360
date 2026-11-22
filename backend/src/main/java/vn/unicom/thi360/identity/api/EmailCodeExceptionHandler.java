package vn.unicom.thi360.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.unicom.thi360.identity.application.EmailCodeInvalidException;
import vn.unicom.thi360.identity.application.EmailCodeRateLimitedException;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Hợp đồng lỗi của API mã OTP email mà FS-2 dùng (plan ID-1, UNI-2773), cho {@link MeEmailController} và các đường dẫn
 * OTP đăng ký trên {@link AuthController}; không sửa {@code ApiError} và {@code GlobalExceptionHandler} (plan B2):
 * <ul>
 *   <li>429 {@code RATE_LIMITED}: header {@code Retry-After} và trường {@code retryAfter} (giây);</li>
 *   <li>422 {@code EMAIL_CODE_INVALID}: trường {@code remainingAttempts};</li>
 *   <li>503: Keycloak lỗi lúc đồng bộ email ở {@code /me/email/change/confirm} (chỉ áp cho {@link MeEmailController},
 *       còn {@link AuthController} giữ nguyên 502 từ {@code GlobalExceptionHandler}).</li>
 * </ul>
 */
@RestControllerAdvice(assignableTypes = {MeEmailController.class, AuthController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EmailCodeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeExceptionHandler.class);

    private final MessageSource messageSource;

    public EmailCodeExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(EmailCodeRateLimitedException.class)
    public ResponseEntity<EmailCodeErrorResponse> handleRateLimited(
            EmailCodeRateLimitedException ex, HttpServletRequest request) {
        long retryAfter = ex.getRetryAfterSeconds();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter))
                .body(buildErrorBody(messageSource, "RATE_LIMITED", request, retryAfter, null));
    }

    @ExceptionHandler(EmailCodeInvalidException.class)
    public ResponseEntity<EmailCodeErrorResponse> handleInvalid(
            EmailCodeInvalidException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildErrorBody(messageSource, "EMAIL_CODE_INVALID", request, null, ex.getRemainingAttempts()));
    }

    @RestControllerAdvice(assignableTypes = {MeEmailController.class})
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class MeEmailProviderExceptionHandler {

        private final MessageSource messageSource;

        public MeEmailProviderExceptionHandler(MessageSource messageSource) {
            this.messageSource = messageSource;
        }

        @ExceptionHandler(ExternalServiceException.class)
        public ResponseEntity<EmailCodeErrorResponse> handleIdentityProviderFailure(
                ExternalServiceException ex, HttpServletRequest request) {
            log.atError()
                    .setCause(ex)
                    .addKeyValue("code", ex.getCode())
                    .log("Identity provider failure while handling an email code request");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildErrorBody(messageSource, ex.getCode(), request, null, null));
        }
    }

    static EmailCodeErrorResponse buildErrorBody(
            MessageSource messageSource,
            String code,
            HttpServletRequest request,
            Long retryAfter,
            Integer remaining) {
        Locale locale = LocaleConfig.resolveLocale(request);
        String message = messageSource.getMessage(code, null, code, locale);
        return new EmailCodeErrorResponse(code, message, List.of(), resolveTraceId(request), retryAfter, remaining);
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if ((traceId == null || traceId.isBlank()) && request != null) {
            Object attr = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            if (attr instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return traceId != null ? traceId : "";
    }
}
