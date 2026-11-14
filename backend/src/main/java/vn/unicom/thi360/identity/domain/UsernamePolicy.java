package vn.unicom.thi360.identity.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.web.AppMessages;

@Component
public class UsernamePolicy {

    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 30;
    public static final Pattern NORMALIZED_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._@-]{3,29}$");

    public static final String CODE_REQUIRED = "USERNAME_REQUIRED";
    public static final String CODE_INVALID = "USERNAME_INVALID";
    public static final String CODE_RESERVED = "USERNAME_RESERVED";

    private static final List<String> DEFAULT_RESERVED = List.of(
            "admin", "administrator", "root", "system", "support", "platform", "thi360", "null", "undefined");

    private final Set<String> reservedUsernames;
    private final AppMessages messages;

    public UsernamePolicy(IdentityProperties properties) {
        this(properties, AppMessages.defaultInstance());
    }

    @Autowired
    public UsernamePolicy(IdentityProperties properties, AppMessages messages) {
        this(
                properties == null || properties.reservedUsernames() == null
                        ? DEFAULT_RESERVED
                        : properties.reservedUsernames(),
                messages);
    }

    public UsernamePolicy(Collection<String> reservedUsernames) {
        this(reservedUsernames, AppMessages.defaultInstance());
    }

    public UsernamePolicy(Collection<String> reservedUsernames, AppMessages messages) {
        Set<String> normalizedReserved = new HashSet<>();
        if (reservedUsernames != null) {
            for (String candidate : reservedUsernames) {
                String normalized = Normalizer.normalize(candidate);
                if (normalized != null) {
                    normalizedReserved.add(normalized);
                }
            }
        }
        this.reservedUsernames = Set.copyOf(normalizedReserved);
        this.messages = messages != null ? messages : AppMessages.defaultInstance();
    }

    public CheckResult check(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            return CheckResult.invalid(
                    null, null, CODE_REQUIRED, messages.get("validation.username.required"));
        }
        String trimmed = rawUsername.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                return CheckResult.invalid(
                        trimmed, null, CODE_INVALID, messages.get("validation.username.whitespace"));
            }
        }
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            return CheckResult.invalid(
                    trimmed, null, CODE_INVALID, messages.get("validation.username.length"));
        }
        String normalized = Normalizer.normalize(trimmed);
        if (normalized == null || !NORMALIZED_PATTERN.matcher(normalized).matches()) {
            return CheckResult.invalid(
                    trimmed,
                    normalized,
                    CODE_INVALID,
                    messages.get("validation.username.pattern"));
        }
        if (reservedUsernames.contains(normalized)) {
            return CheckResult.invalid(
                    trimmed,
                    normalized,
                    CODE_RESERVED,
                    messages.get("validation.username.reserved"));
        }
        return CheckResult.valid(trimmed, normalized);
    }

    public String validate(String fieldName, String rawUsername) {
        CheckResult result = check(rawUsername);
        if (!result.valid()) {
            throw new ValidationException(
                    "VALIDATION_FAILED",
                    null,
                    List.of(new FieldError(fieldName, result.reasonCode(), result.message())));
        }
        return result.trimmed();
    }

    public record CheckResult(
            boolean valid,
            String trimmed,
            String normalized,
            String reasonCode,
            String message
    ) {
        static CheckResult valid(String trimmed, String normalized) {
            return new CheckResult(true, trimmed, normalized, null, null);
        }

        static CheckResult invalid(String trimmed, String normalized, String reasonCode, String message) {
            return new CheckResult(false, trimmed, normalized, reasonCode, message);
        }
    }
}
