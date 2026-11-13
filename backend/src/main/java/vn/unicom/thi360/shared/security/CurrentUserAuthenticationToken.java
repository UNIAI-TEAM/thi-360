package vn.unicom.thi360.shared.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class CurrentUserAuthenticationToken extends AbstractAuthenticationToken {

    private final CurrentUser principal;
    private final String sessionId;

    public CurrentUserAuthenticationToken(CurrentUser principal, String sessionId) {
        super(buildAuthorities(principal));
        this.principal = Objects.requireNonNull(principal, "principal must not be null");
        this.sessionId = sessionId;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> buildAuthorities(CurrentUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ACCESS_" + user.access().name()));
        authorities.add(new SimpleGrantedAuthority("REALM_" + user.realm().name()));
        for (String code : user.permissions().codes()) {
            authorities.add(new SimpleGrantedAuthority("PERM_" + code));
        }
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public CurrentUser getPrincipal() {
        return principal;
    }

    public String getSessionId() {
        return sessionId;
    }
}
