package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.application.ProvisioningReconcilerJob;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

class AccountSelfDeletionKeycloakFailureIT extends AbstractKeycloakIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ProvisioningReconcilerJob provisioningReconcilerJob;

    @MockitoSpyBean
    private KeycloakIdentityClient keycloakClient;

    private SelfRegistrationSupport regSupport;
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final List<String> usedIps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reset(keycloakClient);
        regSupport = new SelfRegistrationSupport(mockMvc, jdbc, keycloakClient, redis, objectMapper);
    }

    @AfterEach
    void tearDown() {
        reset(keycloakClient);
        for (UUID userId : createdUserIds) {
            jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_identity WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redis.delete(List.of("user-sess:" + userId, "user-sess-seq:" + userId, "user-rev-gen:" + userId));
            try {
                keycloakClient.deleteUser(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
        for (String ip : usedIps) {
            redis.delete(RateLimitFilter.KEY_PREFIX + ip);
        }
        usedIps.clear();
        regSupport.cleanup();
    }

    @Test
    void keycloakDeleteFailureLeavesPendingCleanupAndReconcilerDeletesByIdAllowingReRegistration() throws Exception {
        String sfx = SelfRegistrationSupport.suffix();
        String username = "delkcfail." + sfx;
        String email = username + "@thi360.vn";
        regSupport.register(regSupport.body(username, email), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());
        UUID oldId = regSupport.accountId(username);
        createdUserIds.add(oldId);

        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR"))
                .when(keycloakClient).logoutAllSessions(oldId);
        doThrow(new ExternalServiceException("IDENTITY_PROVIDER_ERROR"))
                .when(keycloakClient).deleteUser(oldId);

        mockMvc.perform(post("/api/v1/me/account/deletion")
                        .with(testSecuritySupport.apply(new SimulatedPrincipal(oldId, username, "STUDENT", false, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", SelfRegistrationSupport.PASSWORD))))
                .andExpect(status().isNoContent());

        UserAccount deleted = userAccountRepository.findById(oldId).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.isIdpCleanupPending()).isTrue();
        assertThat(deleted.getUsername()).isEqualTo("deleted-" + oldId.toString().toLowerCase());
        assertThat(deleted.getEmail()).isNull();

        String loginIp = SelfRegistrationSupport.freshIp();
        usedIps.add(loginIp);
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, loginIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username, "password", SelfRegistrationSupport.PASSWORD))))
                .andExpect(status().isUnauthorized());

        reset(keycloakClient);
        assertThat(keycloakClient.findUserIdByUsername(username)).contains(oldId);

        ProvisioningReconcilerJob.ReconcileResult result = provisioningReconcilerJob.reconcile();
        assertThat(result.deleted()).isGreaterThanOrEqualTo(1);
        assertThat(result.failed()).isZero();

        UserAccount reconciled = userAccountRepository.findById(oldId).orElseThrow();
        assertThat(reconciled.isIdpCleanupPending()).isFalse();
        assertThat(keycloakClient.findUserIdByUsername(username)).isEmpty();

        regSupport.register(regSupport.body(username, email), SelfRegistrationSupport.freshIp())
                .andExpect(status().isCreated());
        UUID newId = regSupport.accountId(username);
        createdUserIds.add(newId);
        assertThat(newId).isNotNull().isNotEqualTo(oldId);
    }
}
