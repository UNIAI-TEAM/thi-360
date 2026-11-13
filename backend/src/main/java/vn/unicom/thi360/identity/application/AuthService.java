package vn.unicom.thi360.identity.application;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpServletResponse;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.domain.PlatformAdmin;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.AuthFailureReason;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.KeycloakAuthException;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.TokenResult;
import vn.unicom.thi360.identity.infrastructure.PlatformAdminRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.api.RateLimitedException;
import vn.unicom.thi360.shared.api.UnauthenticatedException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.CtxCookieSigner;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.KeycloakProperties;
import vn.unicom.thi360.shared.security.LoginThrottle;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.security.SessionAuthzResolver;
import vn.unicom.thi360.shared.security.SessionStore;
import vn.unicom.thi360.shared.security.TwoIssuerJwtDecoder;

@Service
public class AuthService {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");
    private static final String MODULE = "identity";

    private final UserAccountRepository userAccountRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final KeycloakIdentityClient keycloakClient;
    private final KeycloakProperties keycloakProperties;
    private final TwoIssuerJwtDecoder twoIssuerJwtDecoder;
    private final SessionStore sessionStore;
    private final LoginThrottle loginThrottle;
    private final CtxCookieSigner ctxCookieSigner;
    private final AuthCookieService authCookieService;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordPolicy passwordPolicy;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final SessionAuthzResolver authzResolver;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PlatformAdminRepository platformAdminRepository,
            KeycloakIdentityClient keycloakClient,
            KeycloakProperties keycloakProperties,
            TwoIssuerJwtDecoder twoIssuerJwtDecoder,
            SessionStore sessionStore,
            LoginThrottle loginThrottle,
            CtxCookieSigner ctxCookieSigner,
            AuthCookieService authCookieService,
            CurrentUserProvider currentUserProvider,
            PasswordPolicy passwordPolicy,
            AuditRecorder auditRecorder,
            TransactionTemplate transactionTemplate,
            Clock clock,
            @Autowired(required = false) SessionAuthzResolver authzResolver) {
        this.userAccountRepository = Objects.requireNonNull(userAccountRepository);
        this.platformAdminRepository = Objects.requireNonNull(platformAdminRepository);
        this.keycloakClient = Objects.requireNonNull(keycloakClient);
        this.keycloakProperties = Objects.requireNonNull(keycloakProperties);
        this.twoIssuerJwtDecoder = Objects.requireNonNull(twoIssuerJwtDecoder);
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.loginThrottle = Objects.requireNonNull(loginThrottle);
        this.ctxCookieSigner = Objects.requireNonNull(ctxCookieSigner);
        this.authCookieService = Objects.requireNonNull(authCookieService);
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider);
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.clock = Objects.requireNonNull(clock);
        this.authzResolver = authzResolver;
    }

    public LoginResult login(
            String identifier,
            String password,
            String callbackUrl,
            HttpServletResponse response) {
        String normalized = Normalizer.normalize(identifier);
        if (normalized == null) {
            throw new ValidationException(List.of(
                    new FieldError("identifier", "NOTBLANK", "Tên đăng nhập hoặc email là bắt buộc")));
        }

        try {
            loginThrottle.assertNotBlocked(normalized);
        } catch (RateLimitedException ex) {
            securityLog.atWarn()
                    .addKeyValue("event", "login.throttled")
                    .log("Login blocked by per-identifier throttle");
            recordAudit(AuditEvent.failure(MODULE, "LOGIN_FAILED", "USER", null, normalized, "LOGIN_THROTTLED"));
            throw ex;
        }

        Optional<UserAccount> accountOpt = resolveActiveAccount(normalized);
        if (accountOpt.isPresent()) {
            return loginAppRealm(accountOpt.get(), normalized, password, response);
        }
        return loginMasterRealm(identifier.trim(), normalized, password, response);
    }

    private LoginResult loginAppRealm(
            UserAccount account,
            String normalizedIdentifier,
            String password,
            HttpServletResponse response) {
        TokenResult tokens;
        try {
            tokens = keycloakClient.passwordGrant(keycloakProperties.realm(), account.getUsername(), password);
        } catch (KeycloakAuthException ex) {
            if (ex.getReason() == AuthFailureReason.ACCOUNT_LOCKED) {
                recordAudit(AuditEvent.failure(
                        MODULE, "LOGIN_FAILED", "USER", account.getId().toString(), account.getUsername(), "LOGIN_THROTTLED"));
                throw new RateLimitedException("LOGIN_THROTTLED");
            }
            loginThrottle.recordFailure(normalizedIdentifier);
            securityLog.atWarn()
                    .addKeyValue("event", "login.invalid_credentials")
                    .addKeyValue("userId", account.getId().toString())
                    .log("Authentication failed for user account");
            recordAudit(AuditEvent.failure(
                    MODULE, "LOGIN_FAILED", "USER", account.getId().toString(), account.getUsername(), "INVALID_CREDENTIALS"));
            throw new UnauthenticatedException("INVALID_CREDENTIALS", null);
        }

        if (account.getStatus() == UserStatus.INACTIVE) {
            keycloakClient.logout(keycloakProperties.realm(), tokens.refreshToken());
            securityLog.atWarn()
                    .addKeyValue("event", "login.account_inactive")
                    .addKeyValue("userId", account.getId().toString())
                    .log("Login refused for inactive account after password verification");
            recordAudit(AuditEvent.failure(
                    MODULE, "LOGIN_FAILED", "USER", account.getId().toString(), account.getUsername(), "ACCOUNT_INACTIVE"));
            throw new ForbiddenException("ACCOUNT_INACTIVE", null);
        }
        if (account.getStatus() != UserStatus.ACTIVE) {
            keycloakClient.logout(keycloakProperties.realm(), tokens.refreshToken());
            loginThrottle.recordFailure(normalizedIdentifier);
            recordAudit(AuditEvent.failure(
                    MODULE, "LOGIN_FAILED", "USER", account.getId().toString(), account.getUsername(), "INVALID_CREDENTIALS"));
            throw new UnauthenticatedException("INVALID_CREDENTIALS", null);
        }

        Jwt jwt = twoIssuerJwtDecoder.decode(tokens.accessToken());
        String sid = requireSid(jwt);
        Instant now = Instant.now(clock);

        loginThrottle.reset(normalizedIdentifier);
        sessionStore.createSession(sid, account.getId(), Realm.APP);

        transactionTemplate.executeWithoutResult(status -> {
            UserAccount fresh = userAccountRepository.findById(account.getId()).orElse(account);
            fresh.markLoggedIn(now);
            userAccountRepository.save(fresh);
            auditRecorder.record(AuditEvent.success(
                    MODULE, "LOGIN", "USER", fresh.getId().toString(), fresh.getUsername(),
                    Map.of("realm", Realm.APP.name())));
        });

        Access access = resolveAccess(account.getId(), Realm.APP);
        long ver = resolveAuthzVersion(account.getId(), Realm.APP);
        String ctx = ctxCookieSigner.sign(account.getId(), Realm.APP, access, ver);
        authCookieService.setAuthCookies(
                response,
                tokens.accessToken(),
                tokens.expiresInSeconds(),
                tokens.refreshToken(),
                tokens.refreshExpiresInSeconds(),
                ctx);

        return new LoginResult("/", account.isMustChangePassword());
    }

    private LoginResult loginMasterRealm(
            String rawUsername,
            String normalizedIdentifier,
            String password,
            HttpServletResponse response) {
        TokenResult tokens;
        try {
            tokens = keycloakClient.passwordGrant(keycloakProperties.platformRealm(), rawUsername, password);
        } catch (KeycloakAuthException ex) {
            if (ex.getReason() == AuthFailureReason.ACCOUNT_LOCKED) {
                recordAudit(AuditEvent.failure(
                        MODULE, "LOGIN_FAILED", "USER", null, normalizedIdentifier, "LOGIN_THROTTLED"));
                throw new RateLimitedException("LOGIN_THROTTLED");
            }
            loginThrottle.recordFailure(normalizedIdentifier);
            securityLog.atWarn()
                    .addKeyValue("event", "login.invalid_credentials")
                    .log("Login failed for unknown or master realm user");
            recordAudit(AuditEvent.failure(
                    MODULE, "LOGIN_FAILED", "USER", null, normalizedIdentifier, "INVALID_CREDENTIALS"));
            throw new UnauthenticatedException("INVALID_CREDENTIALS", null);
        }

        Jwt jwt;
        try {
            jwt = twoIssuerJwtDecoder.decode(tokens.accessToken());
        } catch (JwtException ex) {
            keycloakClient.logout(keycloakProperties.platformRealm(), tokens.refreshToken());
            loginThrottle.recordFailure(normalizedIdentifier);
            securityLog.atWarn()
                    .addKeyValue("event", "login.master_role_missing")
                    .log("Master realm user lacks required platform role");
            recordAudit(AuditEvent.failure(
                    MODULE, "LOGIN_FAILED", "PLATFORM_ADMIN", null, normalizedIdentifier, "INVALID_CREDENTIALS"));
            throw new UnauthenticatedException("INVALID_CREDENTIALS", null);
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        String username = Optional.ofNullable(jwt.getClaimAsString("preferred_username")).orElse(rawUsername);
        String sid = requireSid(jwt);
        Instant now = Instant.now(clock);

        loginThrottle.reset(normalizedIdentifier);
        sessionStore.createSession(sid, userId, Realm.MASTER);

        transactionTemplate.executeWithoutResult(status -> {
            PlatformAdmin admin = platformAdminRepository.findById(userId)
                    .map(existing -> {
                        existing.recordLogin(username, now);
                        return existing;
                    })
                    .orElseGet(() -> new PlatformAdmin(userId, username, now));
            platformAdminRepository.save(admin);
            auditRecorder.record(AuditEvent.success(
                    MODULE, "LOGIN", "PLATFORM_ADMIN", userId.toString(), username,
                    Map.of("realm", Realm.MASTER.name())));
        });

        Access access = resolveAccess(userId, Realm.MASTER);
        long ver = resolveAuthzVersion(userId, Realm.MASTER);
        String ctx = ctxCookieSigner.sign(userId, Realm.MASTER, access, ver);
        authCookieService.setAuthCookies(
                response,
                tokens.accessToken(),
                tokens.expiresInSeconds(),
                tokens.refreshToken(),
                tokens.refreshExpiresInSeconds(),
                ctx);

        return new LoginResult("/", false);
    }

    public LoginResult refresh(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthenticatedException("UNAUTHENTICATED", null);
        }

        JWTClaimsSet refreshClaims = parseClaimsUnverified(refreshToken);
        String oldSid = refreshClaims == null ? null : getStringClaim(refreshClaims, "sid");
        if (oldSid == null || oldSid.isBlank()) {
            throw new UnauthenticatedException("SESSION_REVOKED", null);
        }

        SessionStore.SessionData session = sessionStore.findSession(oldSid)
                .orElseThrow(() -> new UnauthenticatedException("SESSION_REVOKED", null));

        String targetRealmName = session.realm() == Realm.MASTER
                ? keycloakProperties.platformRealm()
                : keycloakProperties.realm();

        TokenResult tokens;
        try {
            tokens = keycloakClient.refresh(targetRealmName, refreshToken);
        } catch (KeycloakAuthException ex) {
            sessionStore.deleteSession(oldSid);
            authCookieService.clearAuthCookies(response);
            throw new UnauthenticatedException("SESSION_REVOKED", null);
        }

        Jwt jwt = twoIssuerJwtDecoder.decode(tokens.accessToken());
        UUID userId = UUID.fromString(jwt.getSubject());
        String newSid = requireSid(jwt);

        boolean mustChangePassword = false;
        if (session.realm() == Realm.APP) {
            UserAccount account = userAccountRepository.findById(userId).orElse(null);
            if (account == null || account.isDeleted() || account.getStatus() != UserStatus.ACTIVE) {
                sessionStore.deleteAllSessions(userId);
                keycloakClient.logout(targetRealmName, tokens.refreshToken());
                authCookieService.clearAuthCookies(response);
                throw new UnauthenticatedException("SESSION_REVOKED", null);
            }
            mustChangePassword = account.isMustChangePassword();
        }

        if (!oldSid.equals(newSid)) {
            sessionStore.deleteSession(oldSid);
            sessionStore.createSession(newSid, userId, session.realm());
        }

        Access access = resolveAccess(userId, session.realm());
        long ver = resolveAuthzVersion(userId, session.realm());
        String ctx = ctxCookieSigner.sign(userId, session.realm(), access, ver);
        authCookieService.setAuthCookies(
                response,
                tokens.accessToken(),
                tokens.expiresInSeconds(),
                tokens.refreshToken(),
                tokens.refreshExpiresInSeconds(),
                ctx);

        return new LoginResult("/", mustChangePassword);
    }

    public void logout(String refreshToken, String accessToken, HttpServletResponse response) {
        Optional<CurrentUser> currentUserOpt = currentUserProvider.getCurrentUser();
        UUID userId = currentUserOpt.map(CurrentUser::id).orElse(null);
        String username = currentUserOpt.map(CurrentUser::username).orElse(null);
        Realm realm = currentUserOpt.map(CurrentUser::realm).orElse(null);
        String sid = null;

        JWTClaimsSet accessClaims = parseClaimsUnverified(accessToken);
        JWTClaimsSet refreshClaims = parseClaimsUnverified(refreshToken);

        if (accessClaims != null) {
            sid = getStringClaim(accessClaims, "sid");
            if (userId == null) {
                userId = parseUuid(accessClaims.getSubject());
            }
            if (username == null) {
                username = getStringClaim(accessClaims, "preferred_username");
            }
            if (realm == null) {
                realm = resolveRealmByIssuer(accessClaims.getIssuer());
            }
        }
        if (refreshClaims != null) {
            if (sid == null) {
                sid = getStringClaim(refreshClaims, "sid");
            }
            if (userId == null) {
                userId = parseUuid(refreshClaims.getSubject());
            }
            if (realm == null) {
                realm = resolveRealmByIssuer(refreshClaims.getIssuer());
            }
        }

        if (sid != null && !sid.isBlank()) {
            if (realm == null) {
                realm = sessionStore.findSession(sid).map(SessionStore.SessionData::realm).orElse(null);
            }
            sessionStore.deleteSession(sid);
        }

        if (refreshToken != null && !refreshToken.isBlank() && realm != null) {
            String realmName = realm == Realm.MASTER
                    ? keycloakProperties.platformRealm()
                    : keycloakProperties.realm();
            keycloakClient.logout(realmName, refreshToken);
        }

        authCookieService.clearAuthCookies(response);

        if (userId != null) {
            String targetType = realm == Realm.MASTER ? "PLATFORM_ADMIN" : "USER";
            String label = username != null ? username : userId.toString();
            recordAudit(AuditEvent.success(MODULE, "LOGOUT", targetType, userId.toString(), label));
        }
    }

    public void changePassword(String oldPassword, String newPassword) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        if (currentUser.realm() != Realm.APP) {
            throw new ForbiddenException("FORBIDDEN", null);
        }

        UserAccount account = userAccountRepository.findById(currentUser.id())
                .filter(u -> !u.isDeleted() && u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new UnauthenticatedException("UNAUTHENTICATED", null));

        passwordPolicy.validate("newPassword", newPassword, account.getUsername());

        TokenResult verified;
        try {
            verified = keycloakClient.passwordGrant(keycloakProperties.realm(), account.getUsername(), oldPassword);
        } catch (KeycloakAuthException ex) {
            throw new ValidationException(
                    "WRONG_OLD_PASSWORD",
                    null,
                    List.of(new FieldError("oldPassword", "WRONG_OLD_PASSWORD", "Mật khẩu hiện tại không đúng")));
        }
        keycloakClient.logout(keycloakProperties.realm(), verified.refreshToken());

        keycloakClient.setPassword(account.getId(), newPassword, false);

        Instant now = Instant.now(clock);
        transactionTemplate.executeWithoutResult(status -> {
            UserAccount fresh = userAccountRepository.findById(account.getId()).orElse(account);
            fresh.clearMustChangePassword(now, currentUser.id());
            userAccountRepository.save(fresh);
            auditRecorder.record(AuditEvent.success(
                    MODULE, "PASSWORD_CHANGED", "USER", fresh.getId().toString(), fresh.getUsername()));
        });
    }

    public MeResult me() {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        String username = currentUser.username();
        String fullName = currentUser.username();
        if (currentUser.realm() == Realm.APP) {
            Optional<UserAccount> account = userAccountRepository.findById(currentUser.id());
            username = account.map(UserAccount::getUsername).orElse(username);
            fullName = account.map(UserAccount::getFullName).orElse(fullName);
        }
        List<String> permissions = currentUser.permissions().codes().stream().sorted().toList();
        return new MeResult(
                currentUser.id(),
                username,
                fullName,
                currentUser.realm(),
                currentUser.access(),
                permissions);
    }

    private Optional<UserAccount> resolveActiveAccount(String normalized) {
        Optional<UserAccount> byUsername = userAccountRepository.findActiveByUsernameNormalized(normalized);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        if (normalized.contains("@")) {
            return userAccountRepository.findActiveByEmailNormalized(normalized);
        }
        return Optional.empty();
    }

    private Access resolveAccess(UUID userId, Realm realm) {
        if (authzResolver != null) {
            return authzResolver.resolve(userId, realm).access();
        }
        return realm == Realm.MASTER ? Access.MANAGE : Access.EXAM;
    }

    private long resolveAuthzVersion(UUID userId, Realm realm) {
        if (authzResolver != null) {
            return authzResolver.resolve(userId, realm).version();
        }
        return 0L;
    }

    private Realm resolveRealmByIssuer(String issuer) {
        if (issuer == null) {
            return null;
        }
        if (twoIssuerJwtDecoder.platformIssuer().equals(issuer)) {
            return Realm.MASTER;
        }
        if (twoIssuerJwtDecoder.appIssuer().equals(issuer)) {
            return Realm.APP;
        }
        return null;
    }

    private void recordAudit(AuditEvent event) {
        transactionTemplate.executeWithoutResult(status -> auditRecorder.record(event));
    }

    private static String requireSid(Jwt jwt) {
        String sid = jwt.getClaimAsString("sid");
        if (sid == null || sid.isBlank()) {
            throw new UnauthenticatedException("UNAUTHENTICATED", null);
        }
        return sid;
    }

    private static JWTClaimsSet parseClaimsUnverified(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return JWTParser.parse(token).getJWTClaimsSet();
        } catch (ParseException ex) {
            return null;
        }
    }

    private static String getStringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException ex) {
            return null;
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record LoginResult(String redirectTo, boolean mustChangePassword) {
    }

    public record MeResult(
            UUID id,
            String username,
            String fullName,
            Realm realm,
            Access access,
            List<String> permissions
    ) {
    }
}
