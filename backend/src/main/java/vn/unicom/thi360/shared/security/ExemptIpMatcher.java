package vn.unicom.thi360.shared.security;

import java.net.InetAddress;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Matches client IPs against {@code thi360.security.rate-limit.exempt-ips}: exact addresses or CIDR
 * ranges, IPv4 or IPv6. Unlike the trusted-proxy list there is no regex fallback and no request-style
 * normalisation (no zone suffix, no brackets, no blank elements): an entry that is not exactly an
 * address or a CIDR makes construction fail, so a typo stops the application at startup instead of
 * silently exempting nobody (or everybody). An unset or empty list is valid and exempts nobody.
 */
public final class ExemptIpMatcher {

    private static final Pattern ADDRESS_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");
    private static final Pattern PREFIX_DIGITS = Pattern.compile("^\\d{1,3}$");

    private final TrustedProxyMatcher matcher;
    private final boolean empty;

    public ExemptIpMatcher(List<String> entries) {
        List<String> valid = entries == null ? List.of() : List.copyOf(entries);
        valid.forEach(ExemptIpMatcher::requireIpOrCidr);
        this.empty = valid.isEmpty();
        this.matcher = new TrustedProxyMatcher(valid);
    }

    /** Fails with {@link IllegalArgumentException} naming the property and the bad entry. */
    public static void requireIpOrCidr(String entry) {
        String bad = "Invalid entry '" + entry + "' in thi360.security.rate-limit.exempt-ips: "
                + "expected an IP address or a CIDR range";
        if (entry == null) {
            throw new IllegalArgumentException(bad);
        }
        int slash = entry.indexOf('/');
        String addressPart = slash >= 0 ? entry.substring(0, slash) : entry;
        if (!ADDRESS_CHARS.matcher(addressPart).matches()) {
            throw new IllegalArgumentException(bad);
        }
        InetAddress address = TrustedProxyMatcher.parseIpLiteral(addressPart);
        if (address == null) {
            throw new IllegalArgumentException(bad);
        }
        if (slash >= 0) {
            String prefixPart = entry.substring(slash + 1);
            if (!PREFIX_DIGITS.matcher(prefixPart).matches()
                    || Integer.parseInt(prefixPart) > address.getAddress().length * 8) {
                throw new IllegalArgumentException(bad);
            }
        }
    }

    public boolean matches(String ip) {
        return !empty && matcher.isTrusted(ip);
    }
}
