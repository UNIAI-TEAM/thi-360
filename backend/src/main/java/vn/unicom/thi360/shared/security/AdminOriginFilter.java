package vn.unicom.thi360.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminOriginFilter extends OncePerRequestFilter {

    public static final String SIMULATED_PRINCIPAL_ATTR = "vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal";

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> allowedOrigins;
    private final SecurityErrorWriter errorWriter;

    public AdminOriginFilter(SecurityProperties securityProperties, SecurityErrorWriter errorWriter) {
        Objects.requireNonNull(securityProperties, "securityProperties must not be null");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter must not be null");
        Set<String> normalized = new LinkedHashSet<>();
        if (securityProperties.adminOrigins() != null) {
            for (String origin : securityProperties.adminOrigins()) {
                String norm = normalizeOrigin(origin);
                if (norm != null) {
                    normalized.add(norm);
                }
            }
        }
        this.allowedOrigins = Set.copyOf(normalized);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return true;
        }
        String path = extractPath(request);
        return !"/api/v1/admin".equals(path) && !path.startsWith("/api/v1/admin/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String originHeader = request.getHeader(HttpHeaders.ORIGIN);
        if (!StringUtils.hasText(originHeader)) {
            if (request.getAttribute(SIMULATED_PRINCIPAL_ATTR) != null) {
                filterChain.doFilter(request, response);
                return;
            }
            rejectInvalidOrigin(request, response, null);
            return;
        }

        String normalizedOrigin = normalizeOrigin(originHeader);
        if (normalizedOrigin == null || !allowedOrigins.contains(normalizedOrigin)) {
            rejectInvalidOrigin(request, response, originHeader);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rejectInvalidOrigin(
            HttpServletRequest request,
            HttpServletResponse response,
            String origin)
            throws IOException {
        securityLog.atWarn()
                .addKeyValue("event", "admin_origin.rejected")
                .addKeyValue("origin", origin != null ? origin : "missing")
                .addKeyValue("path", extractPath(request))
                .log("Admin write request rejected due to invalid or missing Origin");
        errorWriter.write(request, response, HttpStatus.FORBIDDEN, "ORIGIN_INVALID", "FORBIDDEN");
    }

    static String normalizeOrigin(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
                return null;
            }
            int port = uri.getPort();
            if (port == -1) {
                return lowerScheme + "://" + host.toLowerCase(Locale.ROOT);
            }
            if (("http".equals(lowerScheme) && port == 80) || ("https".equals(lowerScheme) && port == 443)) {
                return lowerScheme + "://" + host.toLowerCase(Locale.ROOT);
            }
            return lowerScheme + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String extractPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
