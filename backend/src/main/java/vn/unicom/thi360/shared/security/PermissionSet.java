package vn.unicom.thi360.shared.security;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class PermissionSet {

    private static final PermissionSet EMPTY = new PermissionSet(new BitSet(), Set.of(), false);
    private static final PermissionSet ALL = new PermissionSet(new BitSet(), Set.of(), true);

    private final BitSet bits;
    private final Set<String> codes;
    private final boolean allGranted;

    private PermissionSet(BitSet bits, Set<String> codes, boolean allGranted) {
        this.bits = (BitSet) bits.clone();
        this.codes = Collections.unmodifiableSet(new LinkedHashSet<>(codes));
        this.allGranted = allGranted;
    }

    public static PermissionSet empty() {
        return EMPTY;
    }

    public static PermissionSet all() {
        return ALL;
    }

    public static PermissionSet ofBitsAndCodes(BitSet bits, Collection<String> codes) {
        Objects.requireNonNull(bits, "bits must not be null");
        Set<String> copy = codes == null ? Set.of() : new LinkedHashSet<>(codes);
        return new PermissionSet(bits, copy, false);
    }

    public static PermissionSet ofCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return EMPTY;
        }
        return new PermissionSet(new BitSet(), new LinkedHashSet<>(codes), false);
    }

    public boolean hasBit(int bitIndex) {
        return allGranted || (bitIndex >= 0 && bits.get(bitIndex));
    }

    public boolean hasCode(String code) {
        return allGranted || (code != null && codes.contains(code));
    }

    public boolean isAllGranted() {
        return allGranted;
    }

    public Set<String> codes() {
        return codes;
    }

    public byte[] toByteArray() {
        return bits.toByteArray();
    }
}
