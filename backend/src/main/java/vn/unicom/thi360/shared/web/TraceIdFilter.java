package vn.unicom.thi360.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.unicom.thi360.shared.ids.Ids;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String TRACE_ID_ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    private static final Logger accessLog = LoggerFactory.getLogger("thi360.access");
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(REQUEST_ID_HEADER));
        long startedNanos = System.nanoTime();

        MDC.put(MDC_TRACE_ID, traceId);
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(REQUEST_ID_HEADER, traceId);

        int statusOnException = HttpServletResponse.SC_OK;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error ex) {
            statusOnException = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            throw ex;
        } finally {
            try {
                String path = extractPath(request);
                if (!isActuatorPath(path)) {
                    int status = response.getStatus();
                    if (statusOnException == HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                            && status < HttpServletResponse.SC_BAD_REQUEST) {
                        status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                    }
                    long durationMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
                    String method = request.getMethod();
                    accessLog.atInfo()
                            .addKeyValue("method", method)
                            .addKeyValue("path", path)
                            .addKeyValue("status", status)
                            .addKeyValue("durationMs", durationMs)
                            .log("{} {}", method, path);
                }
            } finally {
                MDC.remove(MDC_TRACE_ID);
            }
        }
    }

    static String resolveTraceId(String incomingHeader) {
        if (incomingHeader != null) {
            String trimmed = incomingHeader.trim();
            if (!trimmed.isEmpty()
                    && trimmed.length() <= MAX_REQUEST_ID_LENGTH
                    && SAFE_REQUEST_ID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return Ids.newId().toString();
    }

    private static String extractPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        int queryIdx = uri.indexOf('?');
        return queryIdx >= 0 ? uri.substring(0, queryIdx) : uri;
    }

    private static boolean isActuatorPath(String path) {
        return "/actuator".equals(path) || path.startsWith("/actuator/");
    }
}
