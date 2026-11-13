package vn.unicom.thi360.identity.application;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;

@Component
public class RedirectResolver {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");
    private static final String CHANGE_PASSWORD_PATH = "/doi-mat-khau";
    private static final String STUDENT_HOME_PATH = "/home";
    private static final Pattern DOUBLE_ENCODED_HEX = Pattern.compile("%25[0-9a-fA-F]{2}");
    private static final Pattern PERCENT_ENCODED_HEX = Pattern.compile("%[0-9a-fA-F]{2}");

    private final String webBaseUrl;
    private final String webOrigin;
    private final String adminBaseUrl;
    private final Set<String> allowedOrigins;

    @Autowired
    public RedirectResolver(RedirectProperties properties) {
        this(
                Objects.requireNonNull(properties, "properties must not be null").webUrl(),
                properties.adminUrl(),
                properties.redirectAllowedOrigins());
    }

    public RedirectResolver(String webUrl, String adminUrl, List<String> extraAllowedOrigins) {
        this.webBaseUrl = stripTrailingSlashes(Objects.requireNonNull(webUrl, "webUrl must not be null"));
        this.webOrigin = parseNormalizedOrigin(this.webBaseUrl);
        this.adminBaseUrl = stripTrailingSlashes(Objects.requireNonNull(adminUrl, "adminUrl must not be null"));

        Set<String> origins = new LinkedHashSet<>();
        addAllowedOrigin(origins, this.webBaseUrl);
        addAllowedOrigin(origins, this.adminBaseUrl);
        if (extraAllowedOrigins != null) {
            for (String extra : extraAllowedOrigins) {
                addAllowedOrigin(origins, extra);
            }
        }
        this.allowedOrigins = Set.copyOf(origins);
    }

    public String resolve(RedirectUser user, String callbackUrl) {
        Objects.requireNonNull(user, "user must not be null");
        return resolve(user.access(), user.mustChangePassword(), callbackUrl);
    }

    public String resolve(CurrentUser user, String callbackUrl) {
        Objects.requireNonNull(user, "user must not be null");
        return resolve(user.access(), false, callbackUrl);
    }

    public String resolve(Access access, boolean mustChangePassword, String callbackUrl) {
        Access effectiveAccess = access != null ? access : Access.EXAM;
        Optional<String> validCallback = validateCallbackUrl(callbackUrl);

        if (mustChangePassword) {
            String base = webBaseUrl + CHANGE_PASSWORD_PATH;
            Optional<String> continuation = resolvePasswordChangeContinuation(validCallback.orElse(null));
            if (continuation.isPresent()) {
                return base + "?callbackUrl=" + URLEncoder.encode(continuation.get(), StandardCharsets.UTF_8);
            }
            return base;
        }

        return validCallback.orElseGet(() -> resolveHomeUrl(effectiveAccess));
    }

    public String resolveHomeUrl(Access access) {
        if (access == Access.MANAGE) {
            return adminBaseUrl;
        }
        return webBaseUrl + STUDENT_HOME_PATH;
    }

    public Optional<String> validateCallbackUrl(String callbackUrl) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return Optional.empty();
        }
        if (!callbackUrl.equals(callbackUrl.trim())) {
            logRejected("surrounding_whitespace");
            return Optional.empty();
        }
        if (containsUnsafeCharacters(callbackUrl)) {
            logRejected("unsafe_characters");
            return Optional.empty();
        }

        URI uri;
        try {
            uri = URI.create(callbackUrl);
        } catch (IllegalArgumentException ex) {
            logRejected("invalid_uri");
            return Optional.empty();
        }
        if (!hasNoNestedEncoding(callbackUrl)) {
            logRejected("double_encoded");
            return Optional.empty();
        }

        if (uri.getScheme() != null) {
            return validateAbsoluteUri(callbackUrl, uri);
        }
        return validateRelativePath(callbackUrl, uri);
    }

    private Optional<String> validateAbsoluteUri(String raw, URI uri) {
        if (uri.isOpaque()) {
            logRejected("opaque_uri");
            return Optional.empty();
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            logRejected("disallowed_scheme");
            return Optional.empty();
        }
        if (uri.getRawUserInfo() != null) {
            logRejected("userinfo_present");
            return Optional.empty();
        }
        String rawAuthority = uri.getRawAuthority();
        if (rawAuthority == null
                || rawAuthority.contains("@")
                || rawAuthority.contains("%")
                || rawAuthority.contains("\\")) {
            logRejected("invalid_authority");
            return Optional.empty();
        }
        String origin = normalizeOrigin(uri);
        if (origin == null || !allowedOrigins.contains(origin)) {
            logRejected("origin_not_allowed");
            return Optional.empty();
        }
        if (!hasSafeDecodedPath(rawPathPart(raw))) {
            logRejected("unsafe_path");
            return Optional.empty();
        }
        return Optional.of(raw);
    }

    private Optional<String> validateRelativePath(String raw, URI uri) {
        if (uri.getRawAuthority() != null) {
            logRejected("authority_in_relative");
            return Optional.empty();
        }
        if (!raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/\\")) {
            logRejected("invalid_relative_prefix");
            return Optional.empty();
        }
        if (!hasSafeDecodedPath(rawPathPart(raw))) {
            logRejected("unsafe_relative_path");
            return Optional.empty();
        }
        return Optional.of(raw);
    }

    private boolean hasSafeDecodedPath(String pathPart) {
        if (pathPart == null || pathPart.isEmpty()) {
            return true;
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(pathPart.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (containsUnsafeCharacters(decoded)) {
            return false;
        }
        return !decoded.startsWith("//") && !decoded.startsWith("/\\");
    }

    private static boolean hasNoNestedEncoding(String raw) {
        if (DOUBLE_ENCODED_HEX.matcher(raw).find()) {
            return false;
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return !PERCENT_ENCODED_HEX.matcher(decoded).find();
    }

    private Optional<String> resolvePasswordChangeContinuation(String validCallback) {
        if (validCallback == null) {
            return Optional.empty();
        }
        if (!isChangePasswordTarget(validCallback)) {
            return Optional.of(validCallback);
        }
        Optional<String> nestedRaw = extractCallbackQueryParam(validCallback);
        if (nestedRaw.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> nestedValid = validateCallbackUrl(nestedRaw.get());
        if (nestedValid.isEmpty() || isChangePasswordTarget(nestedValid.get())) {
            return Optional.empty();
        }
        return nestedValid;
    }

    private boolean isChangePasswordTarget(String validCallback) {
        URI uri;
        try {
            uri = URI.create(validCallback);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (uri.getScheme() != null && !Objects.equals(normalizeOrigin(uri), webOrigin)) {
            return false;
        }
        return CHANGE_PASSWORD_PATH.equals(uri.getRawPath());
    }

    private static Optional<String> extractCallbackQueryParam(String validCallback) {
        String rawQuery = URI.create(validCallback).getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Optional.empty();
        }
        String found = null;
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawKey = eq >= 0 ? pair.substring(0, eq) : pair;
            String key;
            try {
                key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
            if ("callbackUrl".equals(key)) {
                if (eq < 0 || found != null) {
                    return Optional.empty();
                }
                String rawValue = pair.substring(eq + 1);
                try {
                    found = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) {
                    return Optional.empty();
                }
            }
        }
        return Optional.ofNullable(found);
    }

    private static String rawPathPart(String raw) {
        int queryIdx = raw.indexOf('?');
        int fragIdx = raw.indexOf('#');
        int cut = raw.length();
        if (queryIdx >= 0) {
            cut = Math.min(cut, queryIdx);
        }
        if (fragIdx >= 0) {
            cut = Math.min(cut, fragIdx);
        }
        return raw.substring(0, cut);
    }

    private static boolean containsUnsafeCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x1F || c == 0x7F || c == '\\' || Character.isWhitespace(c)) {
                return true;
            }
        }
        return false;
    }

    private static void addAllowedOrigin(Set<String> origins, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        try {
            String normalized = normalizeOrigin(URI.create(rawUrl.trim()));
            if (normalized != null) {
                origins.add(normalized);
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid config entry ignored.
        }
    }

    private static String parseNormalizedOrigin(String rawUrl) {
        try {
            return normalizeOrigin(URI.create(rawUrl));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String normalizeOrigin(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.isBlank()) {
            return null;
        }
        String lowerScheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
            return null;
        }
        int port = uri.getPort();
        if (port == -1
                || ("http".equals(lowerScheme) && port == 80)
                || ("https".equals(lowerScheme) && port == 443)) {
            return lowerScheme + "://" + host.toLowerCase(Locale.ROOT);
        }
        return lowerScheme + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
    }

    private static String stripTrailingSlashes(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static void logRejected(String reason) {
        securityLog.atWarn()
                .addKeyValue("event", "redirect.callback_rejected")
                .addKeyValue("reason", reason)
                .log("Ignored invalid callbackUrl");
    }

    public record RedirectUser(Access access, boolean mustChangePassword) {
    }
}
