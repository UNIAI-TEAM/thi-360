package vn.unicom.thi360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import vn.unicom.thi360.identity.api.StudentLearningResponse.StudentLearningAttempt;
import vn.unicom.thi360.identity.api.StudentLearningResponse.StudentLearningEntitlement;
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
import vn.unicom.thi360.identity.spi.StudentLearningProvider.AttemptItem;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.AttemptLearningStatus;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.EntitlementItem;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.EntitlementLearningSource;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.EntitlementLearningStatus;
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
    void getUserLearning_withProviderBean_mapsAllEntitlementFields() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UUID userId = UUID.randomUUID();
        UUID entitlementId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.of(activeAccount(userId)));

        StudentLearningProvider provider = mock(StudentLearningProvider.class);
        when(provider.loadForUser(userId)).thenReturn(new StudentLearningProvider.StudentLearningData(
                true,
                List.of(new EntitlementItem(
                        entitlementId,
                        examId,
                        "HSA-MATH-01",
                        "Đề thi thử HSA Toán",
                        EntitlementLearningSource.PURCHASE,
                        4,
                        1,
                        1,
                        0,
                        2,
                        EntitlementLearningStatus.ACTIVE)),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        UserAdminService service = createService(userRepo, List.of(provider));

        StudentLearningResponse response = service.getUserLearning(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.dataAvailable()).isTrue();
        assertThat(response.entitlements()).containsExactly(new StudentLearningEntitlement(
                entitlementId,
                examId,
                "HSA-MATH-01",
                "Đề thi thử HSA Toán",
                EntitlementLearningSource.PURCHASE,
                4,
                1,
                1,
                0,
                2,
                EntitlementLearningStatus.ACTIVE));
        assertThat(response.attempts()).isEmpty();
        verify(provider).loadForUser(userId);
    }

    @Test
    void mergesResultsOfTwoProviders() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UUID userId = UUID.randomUUID();
        UUID ent1 = UUID.randomUUID();
        UUID ent2 = UUID.randomUUID();
        UUID exam1 = UUID.randomUUID();
        UUID exam2 = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.of(activeAccount(userId)));

        EntitlementItem item1 = new EntitlementItem(
                ent1, exam1, "EX-1", "Exam 1", EntitlementLearningSource.PURCHASE,
                2, 0, 1, 0, 1, EntitlementLearningStatus.ACTIVE);
        EntitlementItem item2 = new EntitlementItem(
                ent2, exam2, null, null, EntitlementLearningSource.CLASS,
                1, 0, 1, 0, 0, EntitlementLearningStatus.IN_USE);
        AttemptItem attemptItem = new AttemptItem(
                attemptId, exam1, "Exam 1", "HSA", "FULL", 1, AttemptLearningStatus.SCORED,
                NOW, NOW.plusSeconds(1200), new BigDecimal("85.50"), new BigDecimal("150.00"));

        // commerce contributes entitlements only; attempt contributes attempts and
        // dataAvailable on its own side — merged response concatenates every list.
        StudentLearningProvider commerceProvider = mock(StudentLearningProvider.class);
        when(commerceProvider.loadForUser(userId)).thenReturn(new StudentLearningProvider.StudentLearningData(
                false,
                List.of(item1, item2),
                List.of(),
                List.of(),
                List.of(),
                List.of(Map.of("ticket", "S-0"), Map.of("ticket", "S-00"))));
        StudentLearningProvider attemptProvider = mock(StudentLearningProvider.class);
        when(attemptProvider.loadForUser(userId)).thenReturn(new StudentLearningProvider.StudentLearningData(
                true,
                List.of(),
                List.of(attemptItem),
                List.of(),
                List.of(),
                List.of(Map.of("ticket", "S-1"))));

        UserAdminService service = createService(userRepo, List.of(commerceProvider, attemptProvider));

        StudentLearningResponse response = service.getUserLearning(userId);

        assertThat(response.dataAvailable()).isTrue();
        assertThat(response.attempts()).containsExactly(new StudentLearningAttempt(
                attemptId,
                exam1,
                "Exam 1",
                "HSA",
                "FULL",
                1,
                AttemptLearningStatus.SCORED,
                NOW,
                NOW.plusSeconds(1200),
                new BigDecimal("85.50"),
                new BigDecimal("150.00")));
        assertThat(response.assessments()).isEmpty();
        assertThat(response.practice()).isEmpty();
        // Both providers fill supportRequests: every entry survives, in provider order.
        assertThat(response.supportRequests()).containsExactly(
                Map.of("ticket", "S-0"), Map.of("ticket", "S-00"), Map.of("ticket", "S-1"));
        assertThat(response.entitlements()).containsExactly(
                new StudentLearningEntitlement(
                        ent1, exam1, "EX-1", "Exam 1", EntitlementLearningSource.PURCHASE,
                        2, 0, 1, 0, 1, EntitlementLearningStatus.ACTIVE),
                new StudentLearningEntitlement(
                        ent2, exam2, null, null, EntitlementLearningSource.CLASS,
                        1, 0, 1, 0, 0, EntitlementLearningStatus.IN_USE));
    }

    @Test
    void enforcesScoreAndStatusFidelityWhenMappingAttemptItems() {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        UUID userId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.of(activeAccount(userId)));

        AttemptItem scoredZero = new AttemptItem(
                UUID.randomUUID(), examId, "Exam A", "HSA", "PART", 1, AttemptLearningStatus.SCORED,
                NOW, NOW.plusSeconds(60), BigDecimal.ZERO, new BigDecimal("50.00"));
        AttemptItem pendingWithStaleScore = new AttemptItem(
                UUID.randomUUID(), examId, "Exam A", "BCA", "FULL", 2, AttemptLearningStatus.PENDING_REVIEW,
                NOW, NOW.plusSeconds(120), new BigDecimal("45.50"), new BigDecimal("100.00"));
        AttemptItem inProgress = new AttemptItem(
                UUID.randomUUID(), examId, "Exam A", "VACT", "FULL", 3, AttemptLearningStatus.IN_PROGRESS,
                NOW, null, BigDecimal.ZERO, new BigDecimal("1200.00"));
        AttemptItem gradingError = new AttemptItem(
                UUID.randomUUID(), examId, "Exam A", "HSA", "PART", 4, AttemptLearningStatus.GRADING_ERROR,
                NOW, NOW.plusSeconds(180), BigDecimal.ZERO, new BigDecimal("50.00"));

        StudentLearningProvider provider = mock(StudentLearningProvider.class);
        when(provider.loadForUser(userId)).thenReturn(new StudentLearningProvider.StudentLearningData(
                true,
                List.of(),
                List.of(scoredZero, pendingWithStaleScore, inProgress, gradingError),
                List.of(),
                List.of(),
                List.of()));

        UserAdminService service = createService(userRepo, List.of(provider));
        StudentLearningResponse response = service.getUserLearning(userId);

        assertThat(response.attempts()).hasSize(4);
        assertThat(response.attempts().get(0).status()).isEqualTo(AttemptLearningStatus.SCORED);
        assertThat(response.attempts().get(0).score()).isEqualByComparingTo("0");
        assertThat(response.attempts().get(0).maxScore()).isEqualByComparingTo("50.00");

        for (int i = 1; i < 4; i++) {
            StudentLearningAttempt nonScored = response.attempts().get(i);
            assertThat(nonScored.score()).isNull();
            assertThat(nonScored.maxScore()).isNull();
        }
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
