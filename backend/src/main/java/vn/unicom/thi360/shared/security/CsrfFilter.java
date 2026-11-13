package vn.unicom.thi360.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class CsrfFilter extends OncePerRequestFilter {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final SecurityErrorWriter errorWriter;

    public CsrfFilter(SecurityErrorWriter errorWriter) {
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter must not be null");
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String method = request.getMethod().toUpperCase();
        if (SAFE_METHODS.contains(method)) {
            return true;
        }
        if ("POST".equals(method) && LOGIN_PATH.equals(extractPath(request))) {
            return true;
        }
        if (hasExplicitBearer(request)) {
            return true;
        }
        return !hasAuthCookie(request);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String cookieToken = cookieValue(request, AuthCookieService.XSRF_COOKIE);
        String headerToken = request.getHeader(AuthCookieService.XSRF_HEADER);

        if (!StringUtils.hasText(cookieToken)
                || !StringUtils.hasText(headerToken)
                || !MessageDigest.isEqual(
                        cookieToken.getBytes(StandardCharsets.UTF_8),
                        headerToken.getBytes(StandardCharsets.UTF_8))) {
            securityLog.atWarn()
                    .addKeyValue("event", "csrf.invalid")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", extractPath(request))
                    .log("CSRF token missing or mismatched");
            errorWriter.write(request, response, HttpStatus.FORBIDDEN, "CSRF_INVALID", "FORBIDDEN");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean hasExplicitBearer(HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute(CookieAuthFilter.COOKIE_AUTH_PROMOTED_ATTR))) {
            return false;
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        return StringUtils.hasText(auth)
                && auth.regionMatches(true, 0, "Bearer ", 0, 7)
                && auth.length() > 7;
    }

    private static boolean hasAuthCookie(HttpServletRequest request) {
        return StringUtils.hasText(cookieValue(request, AuthCookieService.ACCESS_TOKEN_COOKIE))
                || StringUtils.hasText(cookieValue(request, AuthCookieService.REFRESH_TOKEN_COOKIE));
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
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
