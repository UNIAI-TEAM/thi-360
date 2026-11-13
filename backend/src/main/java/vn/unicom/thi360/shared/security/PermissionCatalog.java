package vn.unicom.thi360.shared.security;

import java.util.BitSet;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public interface PermissionCatalog {

    OptionalInt findBit(String code);

    int bitOf(String code);

    Optional<String> codeOfBit(int bitIndex);

    Set<String> allCodes();

    PermissionSet decode(BitSet bits);

    PermissionSet decode(byte[] bytes);

    PermissionSet encodeCodes(Collection<String> codes);
}
