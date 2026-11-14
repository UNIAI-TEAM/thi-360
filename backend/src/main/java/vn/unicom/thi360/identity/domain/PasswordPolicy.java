package vn.unicom.thi360.identity.domain;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.web.AppMessages;

@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 64;

    private final AppMessages messages;

    public PasswordPolicy() {
        this(AppMessages.defaultInstance());
    }

    @Autowired
    public PasswordPolicy(AppMessages messages) {
        this.messages = messages != null ? messages : AppMessages.defaultInstance();
    }

    public void validate(String fieldName, String password, String username) {
        List<FieldError> errors = check(fieldName, password, username);
        if (!errors.isEmpty()) {
            throw new ValidationException("PASSWORD_WEAK", null, errors);
        }
    }

    public boolean isValid(String password, String username) {
        return check("password", password, username).isEmpty();
    }

    public List<FieldError> check(String fieldName, String password, String username) {
        List<FieldError> errors = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            errors.add(new FieldError(fieldName, "PASSWORD_WEAK", messages.get("validation.password.empty")));
            return errors;
        }

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            errors.add(new FieldError(fieldName, "PASSWORD_LENGTH", messages.get("validation.password.length")));
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isUpperCase(ch)) {
                hasUpper = true;
            } else if (Character.isLowerCase(ch)) {
                hasLower = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(ch)) {
                hasSpecial = true;
            }
        }

        if (!hasUpper) {
            errors.add(new FieldError(fieldName, "PASSWORD_UPPERCASE", messages.get("validation.password.uppercase")));
        }
        if (!hasLower) {
            errors.add(new FieldError(fieldName, "PASSWORD_LOWERCASE", messages.get("validation.password.lowercase")));
        }
        if (!hasDigit) {
            errors.add(new FieldError(fieldName, "PASSWORD_DIGIT", messages.get("validation.password.digit")));
        }
        if (!hasSpecial) {
            errors.add(new FieldError(fieldName, "PASSWORD_SPECIAL", messages.get("validation.password.special")));
        }
        if (username != null && !username.isBlank() && password.equalsIgnoreCase(username.trim())) {
            errors.add(new FieldError(fieldName, "PASSWORD_SAME_AS_USERNAME", messages.get("validation.password.sameAsUsername")));
        }

        return List.copyOf(errors);
    }
}
