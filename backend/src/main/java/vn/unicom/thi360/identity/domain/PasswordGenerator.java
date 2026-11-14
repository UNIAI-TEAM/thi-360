package vn.unicom.thi360.identity.domain;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Generates 12-character passwords conforming to B12 and {@link PasswordPolicy},
 * excluding visually ambiguous characters ({@code 0 O o 1 l I}) and drawing special characters
 * from {@code !@#$%*?-_}.
 */
@Component
public class PasswordGenerator {

    public static final int LENGTH = 12;

    static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    static final String DIGIT = "23456789";
    static final String SPECIAL = "!@#$%*?-_";
    private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return generate(null);
    }

    public String generate(String username) {
        String normalizedUsername = username == null ? null : username.trim();
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = generateCandidate();
            if (normalizedUsername == null
                    || normalizedUsername.isEmpty()
                    || !candidate.equalsIgnoreCase(normalizedUsername)) {
                return candidate;
            }
        }
        return generateCandidate();
    }

    private String generateCandidate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGIT));
        chars.add(pick(SPECIAL));
        while (chars.size() < LENGTH) {
            chars.add(pick(ALL));
        }
        for (int i = chars.size() - 1; i > 0; i--) {
            Collections.swap(chars, i, random.nextInt(i + 1));
        }
        StringBuilder sb = new StringBuilder(LENGTH);
        for (Character ch : chars) {
            sb.append(ch);
        }
        return sb.toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
