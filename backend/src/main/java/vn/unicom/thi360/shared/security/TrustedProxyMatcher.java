package vn.unicom.thi360.shared.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TrustedProxyMatcher {

    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");
    private static final Pattern IPV4_SIMPLE = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private final List<CidrRule> rules;
    private final List<Pattern> fallbackRegexRules;
    private final String tomcatRegex;

    public TrustedProxyMatcher(List<String> entries) {
        List<CidrRule> parsedCidrs = new ArrayList<>();
        List<Pattern> parsedRegexes = new ArrayList<>();
        List<String> regexParts = new ArrayList<>();

        if (entries != null) {
            for (String raw : entries) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String entry = raw.trim();
                CidrRule cidr = tryParseCidr(entry);
                if (cidr != null) {
                    parsedCidrs.add(cidr);
                    regexParts.add(cidr.toTomcatRegex());
                } else {
                    parsedRegexes.add(Pattern.compile(entry));
                    regexParts.add(entry);
                }
            }
        }

        this.rules = List.copyOf(parsedCidrs);
        this.fallbackRegexRules = List.copyOf(parsedRegexes);
        this.tomcatRegex = regexParts.isEmpty() ? "" : String.join("|", regexParts);
    }

    public boolean isTrusted(String ipLiteral) {
        InetAddress addr = parseIpLiteral(ipLiteral);
        if (addr == null) {
            return false;
        }
        return isTrusted(addr);
    }

    public boolean isTrusted(InetAddress addr) {
        if (addr == null) {
            return false;
        }
        for (CidrRule rule : rules) {
            if (rule.matches(addr)) {
                return true;
            }
        }
        if (!fallbackRegexRules.isEmpty()) {
            String hostAddress = addr.getHostAddress();
            for (Pattern pattern : fallbackRegexRules) {
                if (pattern.matcher(hostAddress).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    public String toTomcatRegex() {
        return tomcatRegex;
    }

    public static InetAddress parseIpLiteral(String raw) {
        if (raw == null) {
            return null;
        }
        String candidate = raw.trim();
        if (candidate.isEmpty() || candidate.length() > 64) {
            return null;
        }
        if (candidate.startsWith("[") && candidate.endsWith("]") && candidate.length() > 2) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        int zoneIdx = candidate.indexOf('%');
        if (zoneIdx > 0) {
            candidate = candidate.substring(0, zoneIdx);
        }

        if (IPV4_SIMPLE.matcher(candidate).matches()) {
            byte[] bytes = parseIpv4Bytes(candidate);
            if (bytes == null) {
                return null;
            }
            try {
                return InetAddress.getByAddress(bytes);
            } catch (UnknownHostException ex) {
                return null;
            }
        }

        if (candidate.contains(":") && IPV6_CHARS.matcher(candidate).matches()) {
            try {
                InetAddress addr = InetAddress.getByName(candidate);
                if (addr instanceof Inet6Address || addr instanceof Inet4Address) {
                    return addr;
                }
            } catch (UnknownHostException ignored) {
                return null;
            }
        }
        return null;
    }

    private static byte[] parseIpv4Bytes(String literal) {
        String[] parts = literal.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            int val;
            try {
                val = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (val < 0 || val > 255) {
                return null;
            }
            bytes[i] = (byte) val;
        }
        return bytes;
    }

    private static CidrRule tryParseCidr(String entry) {
        int slash = entry.indexOf('/');
        String ipPart = slash >= 0 ? entry.substring(0, slash).trim() : entry;
        InetAddress addr = parseIpLiteral(ipPart);
        if (addr == null) {
            return null;
        }
        byte[] addressBytes = addr.getAddress();
        int maxPrefix = addressBytes.length * 8;
        int prefixLength = maxPrefix;
        if (slash >= 0) {
            try {
                prefixLength = Integer.parseInt(entry.substring(slash + 1).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
            if (prefixLength < 0 || prefixLength > maxPrefix) {
                return null;
            }
        }
        return new CidrRule(addr, prefixLength);
    }

    private record CidrRule(InetAddress network, int prefixLength) {

        boolean matches(InetAddress candidate) {
            byte[] netBytes = network.getAddress();
            byte[] candBytes = candidate.getAddress();
            if (netBytes.length != candBytes.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (netBytes[i] != candBytes[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits > 0) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                return (netBytes[fullBytes] & mask) == (candBytes[fullBytes] & mask);
            }
            return true;
        }

        String toTomcatRegex() {
            byte[] bytes = network.getAddress();
            if (bytes.length == 4) {
                String[] octetRegexes = new String[4];
                for (int i = 0; i < 4; i++) {
                    int bitsForOctet = Math.max(0, Math.min(8, prefixLength - i * 8));
                    int mask = bitsForOctet == 0 ? 0 : ((0xFF << (8 - bitsForOctet)) & 0xFF);
                    int base = (bytes[i] & 0xFF) & mask;
                    int end = base | (~mask & 0xFF);
                    if (base == end) {
                        octetRegexes[i] = Integer.toString(base);
                    } else if (base == 0 && end == 255) {
                        octetRegexes[i] = "\\d{1,3}";
                    } else {
                        List<String> values = new ArrayList<>(end - base + 1);
                        for (int v = base; v <= end; v++) {
                            values.add(Integer.toString(v));
                        }
                        octetRegexes[i] = "(?:" + String.join("|", values) + ")";
                    }
                }
                return Arrays.stream(octetRegexes).collect(Collectors.joining("\\."));
            }
            if (network.isLoopbackAddress() && prefixLength == 128) {
                return "(?:0:0:0:0:0:0:0:1|::1)";
            }
            return Pattern.quote(network.getHostAddress());
        }
    }
}
