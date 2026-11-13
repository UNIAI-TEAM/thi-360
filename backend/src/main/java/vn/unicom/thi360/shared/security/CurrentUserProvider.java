package vn.unicom.thi360.shared.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.api.UnauthenticatedException;

@Component
public class CurrentUserProvider {

    public Optional<CurrentUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof CurrentUser currentUser) {
            return Optional.of(currentUser);
        }
        return Optional.empty();
    }

    public CurrentUser requireCurrentUser() {
        return getCurrentUser().orElseThrow(UnauthenticatedException::new);
    }
}
