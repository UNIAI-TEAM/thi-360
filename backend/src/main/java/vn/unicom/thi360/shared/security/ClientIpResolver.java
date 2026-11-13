package vn.unicom.thi360.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final TrustedProxyMatcher trustedProxyMatcher;

    public ClientIpResolver(SecurityProperties securityProperties) {
        this.trustedProxyMatcher = new TrustedProxyMatcher(securityProperties.trustedProxies());
    }

    public TrustedProxyMatcher trustedProxyMatcher() {
        return trustedProxyMatcher;
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        InetAddress remoteIp = TrustedProxyMatcher.parseIpLiteral(remoteAddr);
        if (remoteIp == null) {
            return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "unknown";
        }
        if (!trustedProxyMatcher.isTrusted(remoteIp)) {
            return remoteIp.getHostAddress();
        }

        List<String> forwardedChain = extractForwardedForChain(request);
        if (forwardedChain.isEmpty()) {
            return remoteIp.getHostAddress();
        }

        String lastTrustedOrValid = remoteIp.getHostAddress();
        for (int i = forwardedChain.size() - 1; i >= 0; i--) {
            InetAddress hop = TrustedProxyMatcher.parseIpLiteral(forwardedChain.get(i));
            if (hop == null) {
                break;
            }
            lastTrustedOrValid = hop.getHostAddress();
            if (!trustedProxyMatcher.isTrusted(hop)) {
                return hop.getHostAddress();
            }
        }
        return lastTrustedOrValid;
    }

    private static List<String> extractForwardedForChain(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders(X_FORWARDED_FOR);
        if (headers == null) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            if (header == null || header.isBlank()) {
                continue;
            }
            String[] parts = header.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    entries.add(trimmed);
                }
            }
        }
        return entries;
    }
}
