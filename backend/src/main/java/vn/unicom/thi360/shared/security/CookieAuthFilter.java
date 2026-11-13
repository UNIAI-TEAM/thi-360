package vn.unicom.thi360.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class CookieAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_AUTH_PROMOTED_ATTR = CookieAuthFilter.class.getName() + ".promoted";

    private static final Set<String> SKIP_EXACT_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/ping",
            "/api/v1/appearance",
            "/error"
    );

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = extractPath(request);
        return SKIP_EXACT_PATHS.contains(path)
                || path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (StringUtils.hasText(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractCookie(request, AuthCookieService.ACCESS_TOKEN_COOKIE);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        request.setAttribute(COOKIE_AUTH_PROMOTED_ATTR, Boolean.TRUE);
        String bearerValue = "Bearer " + token;
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                    return bearerValue;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                    return Collections.enumeration(List.of(bearerValue));
                }
                return super.getHeaders(name);
            }
        };
        filterChain.doFilter(wrapped, response);
    }

    private static String extractCookie(HttpServletRequest request, String name) {
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
