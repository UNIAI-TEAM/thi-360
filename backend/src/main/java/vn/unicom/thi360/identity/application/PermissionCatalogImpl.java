package vn.unicom.thi360.identity.application;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.spi.Permission;
import vn.unicom.thi360.shared.security.PermissionCatalog;
import vn.unicom.thi360.shared.security.PermissionSet;

@Component
public class PermissionCatalogImpl implements PermissionCatalog {

    private final Map<String, Integer> codeToBit;
    private final Map<Integer, String> bitToCode;
    private final Set<String> allCodes;

    public PermissionCatalogImpl() {
        Map<String, Integer> c2b = new HashMap<>();
        Map<Integer, String> b2c = new HashMap<>();
        Set<String> codes = new LinkedHashSet<>();
        for (Permission p : Permission.values()) {
            c2b.put(p.code(), p.bitIndex());
            b2c.put(p.bitIndex(), p.code());
            codes.add(p.code());
        }
        this.codeToBit = Collections.unmodifiableMap(c2b);
        this.bitToCode = Collections.unmodifiableMap(b2c);
        this.allCodes = Collections.unmodifiableSet(codes);
    }

    @Override
    public OptionalInt findBit(String code) {
        if (code == null || code.isBlank()) {
            return OptionalInt.empty();
        }
        Integer bit = codeToBit.get(code.trim());
        return bit == null ? OptionalInt.empty() : OptionalInt.of(bit);
    }

    @Override
    public int bitOf(String code) {
        return findBit(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown permission code: " + code));
    }

    @Override
    public Optional<String> codeOfBit(int bitIndex) {
        return Optional.ofNullable(bitToCode.get(bitIndex));
    }

    @Override
    public Set<String> allCodes() {
        return allCodes;
    }

    @Override
    public PermissionSet decode(BitSet bits) {
        if (bits == null || bits.isEmpty()) {
            return PermissionSet.empty();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (int bit = bits.nextSetBit(0); bit >= 0; bit = bits.nextSetBit(bit + 1)) {
            String code = bitToCode.get(bit);
            if (code != null) {
                codes.add(code);
            }
        }
        return PermissionSet.ofBitsAndCodes(bits, codes);
    }

    @Override
    public PermissionSet decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return PermissionSet.empty();
        }
        return decode(BitSet.valueOf(bytes));
    }

    @Override
    public PermissionSet encodeCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return PermissionSet.empty();
        }
        BitSet bits = new BitSet();
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : codes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String code = raw.trim();
            int bit = bitOf(code);
            bits.set(bit);
            normalized.add(code);
        }
        if (normalized.isEmpty()) {
            return PermissionSet.empty();
        }
        return PermissionSet.ofBitsAndCodes(bits, normalized);
    }
}
