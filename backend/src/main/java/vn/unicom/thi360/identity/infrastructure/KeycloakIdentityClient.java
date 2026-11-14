package vn.unicom.thi360.identity.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.security.KeycloakProperties;
import vn.unicom.thi360.shared.web.AppMessages;

@Component
public class KeycloakIdentityClient {

    private static final Logger externalLog = LoggerFactory.getLogger("thi360.external");

    private final KeycloakProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final AppMessages messages;
    private volatile Keycloak adminClient;

    public KeycloakIdentityClient(
            KeycloakProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this(properties, objectMapper, restClientBuilder, AppMessages.defaultInstance());
    }

    @Autowired
    public KeycloakIdentityClient(
            KeycloakProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            AppMessages messages) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        this.messages = messages != null ? messages : AppMessages.defaultInstance();
    }

    @PreDestroy
    public void close() {
        Keycloak client = this.adminClient;
        if (client != null) {
            client.close();
        }
    }

    public UUID createUser(CreateUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        long startNanos = System.nanoTime();
        String targetRealm = properties.realm();
        try {
            UserRepresentation rep = new UserRepresentation();
            rep.setUsername(command.username());
            if (command.email() != null && !command.email().isBlank()) {
                rep.setEmail(command.email().trim());
            }
            if (command.firstName() != null && !command.firstName().isBlank()) {
                rep.setFirstName(command.firstName().trim());
            }
            if (command.lastName() != null && !command.lastName().isBlank()) {
                rep.setLastName(command.lastName().trim());
            }
            rep.setEnabled(command.enabled());
            rep.setEmailVerified(command.email() != null && !command.email().isBlank() && command.emailVerified());
            rep.setRequiredActions(List.of());
            if (command.password() != null && !command.password().isBlank()) {
                rep.setCredentials(List.of(passwordCredential(command.password(), command.temporaryPassword())));
            }

            try (Response response = appRealm().users().create(rep)) {
                int status = response.getStatus();
                if (status == 409) {
                    logExternal("createUser", targetRealm, "conflict", startNanos);
                    throw new ConflictException("USERNAME_TAKEN");
                }
                if (status == 400) {
                    String body = safeReadBody(response);
                    logExternal("createUser", targetRealm, "bad_request", startNanos);
                    if (isPasswordPolicyError(body)) {
                        throw new ValidationException(
                                "PASSWORD_WEAK",
                                null,
                                List.of(new FieldError("password", "PASSWORD_WEAK", messages.get("PASSWORD_WEAK"))));
                    }
                    throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, null);
                }
                if (status >= 300) {
                    logExternal("createUser", targetRealm, "error_" + status, startNanos);
                    throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, null);
                }
                URI location = response.getLocation();
                if (location == null || location.getPath() == null) {
                    throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, null);
                }
                String path = location.getPath();
                String idPart = path.substring(path.lastIndexOf('/') + 1);
                UUID userId = UUID.fromString(idPart);
                logExternal("createUser", targetRealm, "success", startNanos);
                return userId;
            }
        } catch (ConflictException | ValidationException | ExternalServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            logExternal("createUser", targetRealm, "exception", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public void setPassword(UUID userId, String password, boolean temporary) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(password, "password must not be null");
        long startNanos = System.nanoTime();
        String targetRealm = properties.realm();
        try {
            appRealm().users().get(userId.toString()).resetPassword(passwordCredential(password, temporary));
            logExternal("setPassword", targetRealm, "success", startNanos);
        } catch (WebApplicationException ex) {
            int status = ex.getResponse() != null ? ex.getResponse().getStatus() : 500;
            String body = safeReadBody(ex.getResponse());
            logExternal("setPassword", targetRealm, "error_" + status, startNanos);
            if (status == 400 && isPasswordPolicyError(body)) {
                throw new ValidationException(
                        "PASSWORD_WEAK",
                        null,
                        List.of(new FieldError("password", "PASSWORD_WEAK", messages.get("PASSWORD_WEAK"))));
            }
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        } catch (RuntimeException ex) {
            logExternal("setPassword", targetRealm, "exception", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public void setEnabled(UUID userId, boolean enabled) {
        Objects.requireNonNull(userId, "userId must not be null");
        long startNanos = System.nanoTime();
        String targetRealm = properties.realm();
        try {
            var userResource = appRealm().users().get(userId.toString());
            UserRepresentation rep = userResource.toRepresentation();
            rep.setEnabled(enabled);
            userResource.update(rep);
            logExternal("setEnabled", targetRealm, "success", startNanos);
        } catch (NotFoundException ex) {
            logExternal("setEnabled", targetRealm, "not_found", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        } catch (RuntimeException ex) {
            logExternal("setEnabled", targetRealm, "exception", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public void updateUserProfile(
            UUID userId, String email, String firstName, String lastName, boolean emailVerified) {
        Objects.requireNonNull(userId, "userId must not be null");
        long startNanos = System.nanoTime();
        String targetRealm = properties.realm();
        try {
            var userResource = appRealm().users().get(userId.toString());
            UserRepresentation rep = userResource.toRepresentation();
            if (email != null) {
                String trimmedEmail = email.trim();
                rep.setEmail(trimmedEmail.isEmpty() ? "" : trimmedEmail);
                rep.setEmailVerified(!trimmedEmail.isEmpty() && emailVerified);
            }
            if (firstName != null) {
                String trimmedFirst = firstName.trim();
                rep.setFirstName(trimmedFirst.isEmpty() ? "" : trimmedFirst);
            }
            if (lastName != null) {
                String trimmedLast = lastName.trim();
                rep.setLastName(trimmedLast.isEmpty() ? "" : trimmedLast);
            }
            userResource.update(rep);
            logExternal("updateUserProfile", targetRealm, "success", startNanos);
        } catch (NotFoundException ex) {
            logExternal("updateUserProfile", targetRealm, "not_found", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        } catch (WebApplicationException ex) {
            int status = ex.getResponse() != null ? ex.getResponse().getStatus() : 500;
            logExternal("updateUserProfile", targetRealm, "error_" + status, startNanos);
            if (status == 409) {
                throw new ConflictException("EMAIL_TAKEN");
            }
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        } catch (RuntimeException ex) {
            logExternal("updateUserProfile", targetRealm, "exception", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public void deleteUser(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        long startNanos = System.nanoTime();
        String targetRealm = properties.realm();
        try (Response response = appRealm().users().delete(userId.toString())) {
            int status = response.getStatus();
            if (status == 404) {
                logExternal("deleteUser", targetRealm, "not_found_ignored", startNanos);
                return;
            }
            if (status >= 300) {
                logExternal("deleteUser", targetRealm, "error_" + status, startNanos);
                throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, null);
            }
            logExternal("deleteUser", targetRealm, "success", startNanos);
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            logExternal("deleteUser", targetRealm, "exception", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public void logoutAllSessions(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        long startNanos = System.nanoTime();
        String targetRealm = properties.realm();
        try {
            appRealm().users().get(userId.toString()).logout();
            logExternal("logoutAllSessions", targetRealm, "success", startNanos);
        } catch (NotFoundException ex) {
            logExternal("logoutAllSessions", targetRealm, "not_found_ignored", startNanos);
        } catch (RuntimeException ex) {
            logExternal("logoutAllSessions", targetRealm, "exception", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public Optional<UUID> findUserIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        long startNanos = System.nanoTime();
        String targetRealm = properties.realm();
        try {
            List<UserRepresentation> users = appRealm().users().searchByUsername(username.trim(), true);
            logExternal("findUserIdByUsername", targetRealm, "success", startNanos);
            if (users == null || users.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(users.getFirst().getId()));
        } catch (RuntimeException ex) {
            logExternal("findUserIdByUsername", targetRealm, "exception", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public boolean hasPasswordCredential(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        long startNanos = System.nanoTime();
        String targetRealm = properties.realm();
        try {
            List<CredentialRepresentation> credentials =
                    appRealm().users().get(userId.toString()).credentials();
            logExternal("listCredentials", targetRealm, "success", startNanos);
            if (credentials == null) {
                return false;
            }
            return credentials.stream()
                    .anyMatch(c -> CredentialRepresentation.PASSWORD.equalsIgnoreCase(c.getType()));
        } catch (NotFoundException ex) {
            logExternal("listCredentials", targetRealm, "not_found", startNanos);
            return false;
        } catch (RuntimeException ex) {
            logExternal("listCredentials", targetRealm, "exception", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    public TokenResult passwordGrant(String realm, String username, String password) {
        ClientCredentials creds = resolveClientCredentials(realm);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", OAuth2Constants.PASSWORD);
        form.add("client_id", creds.clientId());
        form.add("client_secret", creds.clientSecret());
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid");
        return executeTokenRequest("passwordGrant", realm, form);
    }

    public TokenResult refresh(String realm, String refreshToken) {
        ClientCredentials creds = resolveClientCredentials(realm);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", OAuth2Constants.REFRESH_TOKEN);
        form.add("client_id", creds.clientId());
        form.add("client_secret", creds.clientSecret());
        form.add("refresh_token", refreshToken);
        return executeTokenRequest("refresh", realm, form);
    }

    public void logout(String realm, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        ClientCredentials creds = resolveClientCredentials(realm);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", creds.clientId());
        form.add("client_secret", creds.clientSecret());
        form.add("refresh_token", refreshToken);

        long startNanos = System.nanoTime();
        String endpoint = tokenEndpointBase(realm) + "/logout";
        try {
            restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            logExternal("logout", realm, "success", startNanos);
        } catch (RestClientResponseException ex) {
            logExternal("logout", realm, "http_" + ex.getStatusCode().value(), startNanos);
        } catch (RestClientException ex) {
            logExternal("logout", realm, "exception", startNanos);
        }
    }

    private TokenResult executeTokenRequest(String operation, String realm, MultiValueMap<String, String> form) {
        long startNanos = System.nanoTime();
        String endpoint = tokenEndpointBase(realm) + "/token";
        try {
            TokenPayload payload = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenPayload.class);
            if (payload == null || payload.accessToken() == null || payload.accessToken().isBlank()) {
                logExternal(operation, realm, "empty_response", startNanos);
                throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, null);
            }
            logExternal(operation, realm, "success", startNanos);
            return new TokenResult(
                    payload.accessToken(),
                    payload.refreshToken(),
                    payload.expiresIn(),
                    payload.refreshExpiresIn());
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            logExternal(operation, realm, "http_" + status, startNanos);
            throw mapTokenError(operation, ex);
        } catch (RestClientException ex) {
            logExternal(operation, realm, "exception", startNanos);
            throw new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
        }
    }

    private RuntimeException mapTokenError(String operation, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String body = ex.getResponseBodyAsString();
        String error = "";
        String description = "";
        try {
            JsonNode node = objectMapper.readTree(body);
            error = node.path("error").asText("");
            description = node.path("error_description").asText("");
        } catch (Exception ignored) {
            // Non-JSON response falls through to status-based mapping.
        }

        if ("invalid_grant".equals(error)) {
            if ("refresh".equals(operation)) {
                return new KeycloakAuthException(AuthFailureReason.INVALID_TOKEN);
            }
            String lowerDesc = description.toLowerCase(Locale.ROOT);
            if (lowerDesc.contains("temporarily disabled") || lowerDesc.contains("locked")) {
                return new KeycloakAuthException(AuthFailureReason.ACCOUNT_LOCKED);
            }
            if (lowerDesc.contains("disabled")) {
                return new KeycloakAuthException(AuthFailureReason.USER_DISABLED);
            }
            return new KeycloakAuthException(AuthFailureReason.INVALID_CREDENTIALS);
        }

        if (status == 401) {
            return new KeycloakAuthException(
                    "refresh".equals(operation)
                            ? AuthFailureReason.INVALID_TOKEN
                            : AuthFailureReason.INVALID_CREDENTIALS);
        }

        if (status == 403) {
            return new KeycloakAuthException(AuthFailureReason.ACCOUNT_LOCKED);
        }

        return new ExternalServiceException("IDENTITY_PROVIDER_ERROR", null, ex);
    }

    private RealmResource appRealm() {
        return getOrCreateAdminClient().realm(properties.realm());
    }

    private Keycloak getOrCreateAdminClient() {
        Keycloak client = this.adminClient;
        if (client == null) {
            synchronized (this) {
                client = this.adminClient;
                if (client == null) {
                    client = KeycloakBuilder.builder()
                            .serverUrl(properties.url())
                            .realm(properties.realm())
                            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                            .clientId(properties.clientId())
                            .clientSecret(properties.clientSecret())
                            .build();
                    this.adminClient = client;
                }
            }
        }
        return client;
    }

    private ClientCredentials resolveClientCredentials(String realm) {
        if (properties.realm().equals(realm)) {
            return new ClientCredentials(properties.clientId(), properties.clientSecret());
        }
        if (properties.platformRealm().equals(realm)) {
            return new ClientCredentials(properties.platformClientId(), properties.platformClientSecret());
        }
        throw new IllegalArgumentException("Unsupported Keycloak realm: " + realm);
    }

    private String tokenEndpointBase(String realm) {
        String baseUrl = properties.url().endsWith("/")
                ? properties.url().substring(0, properties.url().length() - 1)
                : properties.url();
        return baseUrl + "/realms/" + realm + "/protocol/openid-connect";
    }

    private static CredentialRepresentation passwordCredential(String password, boolean temporary) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setTemporary(temporary);
        cred.setValue(password);
        return cred;
    }

    private static String safeReadBody(Response response) {
        if (response == null) {
            return "";
        }
        try {
            if (response.hasEntity()) {
                return response.readEntity(String.class);
            }
        } catch (Exception ignored) {
            // Ignore unreadable body.
        }
        return "";
    }

    private static boolean isPasswordPolicyError(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("invalidpassword")
                || lower.contains("password") && lower.contains("policy");
    }

    private static void logExternal(String operation, String realm, String outcome, long startNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        externalLog.atInfo()
                .addKeyValue("target", "keycloak")
                .addKeyValue("operation", operation)
                .addKeyValue("realm", realm)
                .addKeyValue("outcome", outcome)
                .addKeyValue("durationMs", durationMs)
                .log("Keycloak request completed");
    }

    private record ClientCredentials(String clientId, String clientSecret) {
    }

    private record TokenPayload(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("refresh_expires_in") long refreshExpiresIn
    ) {
    }

    public record CreateUserCommand(
            String username,
            String email,
            String firstName,
            String lastName,
            String password,
            boolean enabled,
            boolean temporaryPassword,
            boolean emailVerified
    ) {
    }

    public record TokenResult(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            long refreshExpiresInSeconds
    ) {
    }

    public enum AuthFailureReason {
        INVALID_CREDENTIALS,
        USER_DISABLED,
        ACCOUNT_LOCKED,
        INVALID_TOKEN
    }

    public static class KeycloakAuthException extends RuntimeException {
        private final AuthFailureReason reason;

        public KeycloakAuthException(AuthFailureReason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public AuthFailureReason getReason() {
            return reason;
        }
    }
}
