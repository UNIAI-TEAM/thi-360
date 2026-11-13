package vn.unicom.thi360.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionFilter extends OncePerRequestFilter {

    public static final String MDC_USER_ID = "userId";

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");

    private final SessionStore sessionStore;
    private final TwoIssuerJwtDecoder jwtDecoder;
    private final SecurityErrorWriter errorWriter;
    private final SessionAuthzResolver authzResolver;

    public SessionFilter(
            SessionStore sessionStore,
            TwoIssuerJwtDecoder jwtDecoder,
            SecurityErrorWriter errorWriter,
            SessionAuthzResolver authzResolver) {
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore must not be null");
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder must not be null");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter must not be null");
        this.authzResolver = authzResolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof CurrentUserAuthenticationToken currentAuth
                && currentAuth.isAuthenticated()) {
            CurrentUser user = currentAuth.getPrincipal();
            runWithUserMdc(user, request, response, filterChain);
            return;
        }

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth) || !jwtAuth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String sid = jwt.getClaimAsString("sid");
        UUID userId = parseUuid(jwt.getSubject());
        if (sid == null || sid.isBlank() || userId == null) {
            rejectRevokedSession(request, response, userId, "missing_sid_or_sub");
            return;
        }

        Optional<SessionStore.SessionData> sessionOpt = sessionStore.findSession(sid);
        if (sessionOpt.isEmpty() || !userId.equals(sessionOpt.get().userId())) {
            rejectRevokedSession(request, response, userId, "session_not_found");
            return;
        }

        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
        Realm realm = jwtDecoder.platformIssuer().equals(issuer) ? Realm.MASTER : Realm.APP;
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = userId.toString();
        }

        Access access;
        PermissionSet permissions;
        if (authzResolver != null) {
            SessionAuthzResolver.AuthzSnapshot snapshot = authzResolver.resolve(userId, realm);
            access = snapshot.access();
            permissions = snapshot.permissions();
        } else {
            access = realm == Realm.MASTER ? Access.MANAGE : Access.EXAM;
            permissions = PermissionSet.empty();
        }

        CurrentUser currentUser = new CurrentUser(userId, username, realm, access, permissions);
        SecurityContextHolder.getContext().setAuthentication(new CurrentUserAuthenticationToken(currentUser, sid));
        runWithUserMdc(currentUser, request, response, filterChain);
    }

    private void runWithUserMdc(
            CurrentUser user,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String mdcUserId = user.realm() == Realm.MASTER
                ? "platform:" + user.id()
                : user.id().toString();
        MDC.put(MDC_USER_ID, mdcUserId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_ID);
        }
    }

    private void rejectRevokedSession(
            HttpServletRequest request,
            HttpServletResponse response,
            UUID userId,
            String reason)
            throws IOException {
        SecurityContextHolder.clearContext();
        securityLog.atWarn()
                .addKeyValue("event", "session.revoked")
                .addKeyValue("userId", userId != null ? userId.toString() : "unknown")
                .addKeyValue("reason", reason)
                .addKeyValue("path", request.getRequestURI())
                .log("Rejected request with missing or revoked session");
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "UNAUTHENTICATED");
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
