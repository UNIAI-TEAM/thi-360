package vn.unicom.thi360.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.unicom.thi360.identity.application.AccountDeletionBlockedException;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@RestControllerAdvice(assignableTypes = MeAccountDeletionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MeAccountDeletionExceptionHandler {

    private final MessageSource messageSource;

    public MeAccountDeletionExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(AccountDeletionBlockedException.class)
    public ResponseEntity<AccountDeletionBlockedResponse> handleBlocked(
            AccountDeletionBlockedException ex, HttpServletRequest request) {
        Locale locale = LocaleConfig.resolveLocale(request);
        String code = AccountDeletionBlockedException.CODE;
        String message = messageSource.getMessage(code, null, code, locale);
        AccountDeletionBlockedResponse body = new AccountDeletionBlockedResponse(
                code, message, List.of(), resolveTraceId(request), ex.getBlockers());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
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
