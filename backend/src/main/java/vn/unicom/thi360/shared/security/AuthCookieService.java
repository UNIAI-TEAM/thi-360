package vn.unicom.thi360.shared.security;

import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "thi360_at";
    public static final String REFRESH_TOKEN_COOKIE = "thi360_rt";
    public static final String CTX_COOKIE = "thi360_ctx";
    public static final String XSRF_COOKIE = "thi360_xsrf";
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";

    private static final Duration DEFAULT_ACCESS_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_REFRESH_TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final CookieProperties cookieProperties;
    private final CtxProperties ctxProperties;

    public AuthCookieService(CookieProperties cookieProperties, CtxProperties ctxProperties) {
        this.cookieProperties = Objects.requireNonNull(cookieProperties, "cookieProperties must not be null");
        this.ctxProperties = Objects.requireNonNull(ctxProperties, "ctxProperties must not be null");
    }

    public void setAuthCookies(
            HttpServletResponse response,
            String accessToken,
            long accessExpiresInSeconds,
            String refreshToken,
            long refreshExpiresInSeconds,
            String ctxValue) {
        Duration accessMaxAge = accessExpiresInSeconds > 0
                ? Duration.ofSeconds(accessExpiresInSeconds)
                : DEFAULT_ACCESS_TTL;
        Duration refreshMaxAge = refreshExpiresInSeconds > 0
                ? Duration.ofSeconds(refreshExpiresInSeconds)
                : DEFAULT_REFRESH_TTL;

        addCookie(response, buildCookie(ACCESS_TOKEN_COOKIE, accessToken, accessMaxAge, true));
        addCookie(response, buildCookie(REFRESH_TOKEN_COOKIE, refreshToken, refreshMaxAge, true));
        addCookie(response, buildCookie(CTX_COOKIE, ctxValue, ctxProperties.ttl(), true));
        addCookie(response, buildCookie(XSRF_COOKIE, generateXsrfToken(), refreshMaxAge, false));
    }

    public void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, buildCookie(ACCESS_TOKEN_COOKIE, "", Duration.ZERO, true));
        addCookie(response, buildCookie(REFRESH_TOKEN_COOKIE, "", Duration.ZERO, true));
        addCookie(response, buildCookie(CTX_COOKIE, "", Duration.ZERO, true));
        addCookie(response, buildCookie(XSRF_COOKIE, "", Duration.ZERO, false));
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge, boolean httpOnly) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value == null ? "" : value)
                .path("/")
                .httpOnly(httpOnly)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .maxAge(maxAge);
        if (cookieProperties.domain() != null && !cookieProperties.domain().isBlank()) {
            builder.domain(cookieProperties.domain().trim());
        }
        return builder.build();
    }

    private static void addCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String generateXsrfToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
