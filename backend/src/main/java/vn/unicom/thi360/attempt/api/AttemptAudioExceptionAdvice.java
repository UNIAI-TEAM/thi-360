package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.unicom.thi360.attempt.application.AudioPlayInProgressException;
import vn.unicom.thi360.attempt.application.AudioSeekRejectedException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.web.LocaleConfig;
import vn.unicom.thi360.shared.web.TraceIdFilter;

/**
 * Controller-scoped exception mapper for {@link AttemptAudioController} returning extra payload
 * fields ({@code playId} on {@code AUDIO_PLAY_IN_PROGRESS}; {@code positionMs} on {@code
 * AUDIO_SEEK_REJECTED}) without modifying {@code GlobalExceptionHandler}.
 */
@RestControllerAdvice(assignableTypes = AttemptAudioController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttemptAudioExceptionAdvice {

    private final MessageSource messageSource;

    public AttemptAudioExceptionAdvice(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(AudioPlayInProgressException.class)
    public ResponseEntity<AudioPlayInProgressResponse> handlePlayInProgress(
            AudioPlayInProgressException ex, HttpServletRequest request) {
        Locale locale = LocaleConfig.resolveLocale(request);
        String code = "AUDIO_PLAY_IN_PROGRESS";
        String message = messageSource.getMessage(code, null, code, locale);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        new AudioPlayInProgressResponse(
                                code, message, List.of(), resolveTraceId(request), ex.getPlayId()));
    }

    @ExceptionHandler(AudioSeekRejectedException.class)
    public ResponseEntity<AudioSeekRejectedResponse> handleSeekRejected(
            AudioSeekRejectedException ex, HttpServletRequest request) {
        Locale locale = LocaleConfig.resolveLocale(request);
        String code = "AUDIO_SEEK_REJECTED";
        String message = messageSource.getMessage(code, null, code, locale);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        new AudioSeekRejectedResponse(
                                code,
                                message,
                                List.of(),
                                resolveTraceId(request),
                                ex.getPositionMs()));
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

    @Schema(description = "Lỗi xung đột khi audio đang có lần nghe dở hoặc đã đóng/hết lượt (BS §17.2)")
    public record AudioPlayInProgressResponse(
            @Schema(example = "AUDIO_PLAY_IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
                    String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FieldError> fieldErrors,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String traceId,
            @Schema(description = "Id của lần nghe đang phát nếu có", nullable = true)
                    UUID playId) {}

    @Schema(description = "Lỗi xung đột khi vị trí tua vượt ngưỡng chống tua hoặc không hợp lệ (BS §17.2)")
    public record AudioSeekRejectedResponse(
            @Schema(example = "AUDIO_SEEK_REJECTED", requiredMode = Schema.RequiredMode.REQUIRED)
                    String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FieldError> fieldErrors,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String traceId,
            @Schema(description = "Vị trí hợp lệ đã lưu trên máy chủ (ms)", nullable = true)
                    Long positionMs) {}
}
