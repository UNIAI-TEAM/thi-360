package vn.unicom.thi360.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@Component
public class SecurityErrorWriter {

    private static final Locale DEFAULT_LOCALE = Locale.of("vi");

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public SecurityErrorWriter(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource must not be null");
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String fallbackCode)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String message = resolveMessage(code, fallbackCode);
        String traceId = resolveTraceId(request);
        ApiError body = ApiError.of(code, message, List.of(), traceId);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String resolveMessage(String code, String fallbackCode) {
        String msg = messageSource.getMessage(code, null, null, DEFAULT_LOCALE);
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        if (fallbackCode != null) {
            String fallback = messageSource.getMessage(fallbackCode, null, null, DEFAULT_LOCALE);
            if (fallback != null && !fallback.isBlank()) {
                return fallback;
            }
        }
        return code;
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if ((traceId == null || traceId.isBlank()) && request != null) {
            Object attr = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            if (attr instanceof String s && !s.isBlank()) {
                traceId = s;
            }
        }
        return traceId != null ? traceId : "";
    }
}
