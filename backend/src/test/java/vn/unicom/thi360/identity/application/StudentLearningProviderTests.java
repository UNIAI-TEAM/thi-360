package vn.unicom.thi360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.api.StudentLearningResponse;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.domain.UsernamePolicy;
import vn.unicom.thi360.identity.infrastructure.ImportBatchRepository;
import vn.unicom.thi360.identity.infrastructure.ImportRowRepository;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.RoleRepository;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.infrastructure.UserIdentityRepository;
import vn.unicom.thi360.identity.infrastructure.UserRoleRepository;
import vn.unicom.thi360.identity.spi.ClassMembershipPort;
import vn.unicom.thi360.identity.spi.StudentLearningProvider;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.idempotency.IdempotencyService;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.SessionStore;
import vn.unicom.thi360.shared.web.AppMessages;

class StudentLearningProviderTests {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    @Test
    void getUserLearning_withoutProviderBean_returnsEmptyStructureAndDataAvailableFalse() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.of(activeAccount(userId)));

        UserAdminService service = createService(userRepo, List.of());

        StudentLearningResponse response = service.getUserLearning(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.dataAvailable()).isFalse();
        assertThat(response.entitlements()).isEmpty();
        assertThat(response.attempts()).isEmpty();
        assertThat(response.assessments()).isEmpty();
        assertThat(response.practice()).isEmpty();
        assertThat(response.supportRequests()).isEmpty();
    }

    @Test
    void getUserLearning_withProviderBean_delegatesToProvider() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.of(activeAccount(userId)));

        StudentLearningProvider provider = mock(StudentLearningProvider.class);
        when(provider.loadForUser(userId)).thenReturn(new StudentLearningProvider.StudentLearningData(
                true,
                List.of(Map.of("code", "PKG-1")),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        UserAdminService service = createService(userRepo, List.of(provider));

        StudentLearningResponse response = service.getUserLearning(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.dataAvailable()).isTrue();
        assertThat(response.entitlements()).hasSize(1);
        assertThat(response.attempts()).isEmpty();
        verify(provider).loadForUser(userId);
    }

    @Test
    void mergesResultsOfTwoProviders() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.of(activeAccount(userId)));

        // commerce contributes entitlements only; attempt contributes attempts and
        // dataAvailable on its own side — merged response concatenates every list.
        StudentLearningProvider commerceProvider = mock(StudentLearningProvider.class);
        when(commerceProvider.loadForUser(userId)).thenReturn(new StudentLearningProvider.StudentLearningData(
                false,
                List.of(Map.of("entitlementId", "E-1"), Map.of("entitlementId", "E-2")),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        StudentLearningProvider attemptProvider = mock(StudentLearningProvider.class);
        when(attemptProvider.loadForUser(userId)).thenReturn(new StudentLearningProvider.StudentLearningData(
                true,
                List.of(),
                List.of(Map.of("attemptId", "A-1")),
                List.of(),
                List.of(),
                List.of(Map.of("ticket", "S-1"))));

        UserAdminService service = createService(userRepo, List.of(commerceProvider, attemptProvider));

        StudentLearningResponse response = service.getUserLearning(userId);

        assertThat(response.dataAvailable()).isTrue();
        assertThat(response.entitlements()).hasSize(2);
        assertThat(response.attempts()).hasSize(1);
        assertThat(response.assessments()).isEmpty();
        assertThat(response.practice()).isEmpty();
        assertThat(response.supportRequests()).hasSize(1);
    }

    @Test
    void noProviderOrNullResultIsEmpty() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.of(activeAccount(userId)));

        StudentLearningProvider nullProvider = mock(StudentLearningProvider.class);
        when(nullProvider.loadForUser(userId)).thenReturn(null);

        UserAdminService withoutProvider = createService(userRepo, List.of());
        UserAdminService withNullProvider = createService(userRepo, List.of(nullProvider));

        for (StudentLearningResponse response : List.of(
                withoutProvider.getUserLearning(userId),
                withNullProvider.getUserLearning(userId))) {
            assertThat(response.dataAvailable()).isFalse();
            assertThat(response.entitlements()).isEmpty();
            assertThat(response.attempts()).isEmpty();
            assertThat(response.assessments()).isEmpty();
            assertThat(response.practice()).isEmpty();
            assertThat(response.supportRequests()).isEmpty();
        }
    }

    @Test
    void getUserLearning_unknownOrProvisioningUser_throwsNotFoundWithoutCallingProvider() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UUID missingId = UUID.randomUUID();
        UUID provId = UUID.randomUUID();
        when(userRepo.findById(missingId)).thenReturn(Optional.empty());
        when(userRepo.findById(provId)).thenReturn(Optional.of(new UserAccount(
                provId,
                "U000099",
                "provuser",
                "Provisioning",
                null,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.PROVISIONING,
                false,
                NOW,
                null)));

        StudentLearningProvider provider = mock(StudentLearningProvider.class);
        UserAdminService service = createService(userRepo, List.of(provider));

        assertThatThrownBy(() -> service.getUserLearning(missingId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getUserLearning(provId)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(provider);
    }

    private static UserAccount activeAccount(UUID userId) {
        return new UserAccount(
                userId,
                "U000001",
                "student01",
                "Học viên 01",
                "student01@thi360.vn",
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                NOW,
                null);
    }

    private static UserAdminService createService(
            UserAccountRepository userRepo, List<StudentLearningProvider> learningProviders) {
        return new UserAdminService(
                userRepo,
                mock(UserIdentityRepository.class),
                mock(RoleRepository.class),
                mock(UserRoleRepository.class),
                mock(ImportRowRepository.class),
                mock(ImportBatchRepository.class),
                mock(UsernamePolicy.class),
                mock(PasswordPolicy.class),
                mock(PrivilegeGuard.class),
                mock(AuthzSnapshotService.class),
                mock(SessionStore.class),
                mock(IdempotencyService.class),
                mock(OutboxPublisher.class),
                List.of(),
                List.of(),
                mock(ClassMembershipPort.class),
                learningProviders,
                mock(KeycloakIdentityClient.class),
                mock(CurrentUserProvider.class),
                mock(AuditRecorder.class),
                mock(JdbcTemplate.class),
                mock(PlatformTransactionManager.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                AppMessages.defaultInstance());
    }
}
