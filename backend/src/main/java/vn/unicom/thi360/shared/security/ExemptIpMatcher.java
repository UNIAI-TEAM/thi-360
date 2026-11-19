package vn.unicom.thi360.shared.security;

import java.net.InetAddress;
import java.util.List;

/**
 * Matches client IPs against {@code thi360.security.rate-limit.exempt-ips}: exact addresses or CIDR
 * ranges, IPv4 or IPv6. Unlike the trusted-proxy list there is no regex fallback: an entry that is
 * neither an address nor a CIDR makes construction fail, so a typo stops the application at startup
 * instead of silently exempting nobody (or everybody).
 */
public final class ExemptIpMatcher {

    private static final ExemptIpMatcher NONE = new ExemptIpMatcher(List.of());

    private final TrustedProxyMatcher matcher;
    private final boolean empty;

    public ExemptIpMatcher(List<String> entries) {
        List<String> valid = entries == null ? List.of() : entries.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                .peek(ExemptIpMatcher::requireIpOrCidr)
                .toList();
        this.empty = valid.isEmpty();
        this.matcher = new TrustedProxyMatcher(valid);
    }

    public static ExemptIpMatcher none() {
        return NONE;
    }

    /** Fails with {@link IllegalArgumentException} naming the property and the bad entry. */
    public static void requireIpOrCidr(String entry) {
        String bad = "Invalid entry '" + entry + "' in thi360.security.rate-limit.exempt-ips: "
                + "expected an IP address or a CIDR range";
        int slash = entry.indexOf('/');
        InetAddress address = TrustedProxyMatcher.parseIpLiteral(slash >= 0 ? entry.substring(0, slash) : entry);
        if (address == null) {
            throw new IllegalArgumentException(bad);
        }
        if (slash >= 0) {
            int prefix;
            try {
                prefix = Integer.parseInt(entry.substring(slash + 1).trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(bad, ex);
            }
            if (prefix < 0 || prefix > address.getAddress().length * 8) {
                throw new IllegalArgumentException(bad);
            }
        }
    }

    public boolean matches(String ip) {
        return !empty && matcher.isTrusted(ip);
    }
}
