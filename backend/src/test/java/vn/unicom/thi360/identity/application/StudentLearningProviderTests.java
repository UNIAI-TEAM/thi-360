package vn.unicom.thi360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository;
import vn.unicom.thi360.commerce.entitlement.access.infrastructure.ExamAccessReadRepository.ProductRef;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementLearningProvider;
import vn.unicom.thi360.commerce.entitlement.domain.Entitlement;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementSource;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementRepository;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
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

    @Nested
    class EntitlementLearningProviderUnitTests {

        private final EntitlementRepository entitlementRepository = mock(EntitlementRepository.class);
        private final ExamAccessReadRepository examAccessReadRepository = mock(ExamAccessReadRepository.class);
        private final PublishedExamPort publishedExamPort = mock(PublishedExamPort.class);
        private final EntitlementLearningProvider provider = new EntitlementLearningProvider(
                entitlementRepository, examAccessReadRepository, publishedExamPort, new ObjectMapper());

        @Test
        void nullUserId_returnsEmptyWithoutDownstreamCalls() {
            StudentLearningProvider.StudentLearningData data = provider.loadForUser(null);

            assertThat(data.dataAvailable()).isFalse();
            assertThat(data.entitlements()).isEmpty();
            assertThat(data.attempts()).isEmpty();
            verifyNoInteractions(entitlementRepository, examAccessReadRepository, publishedExamPort);
        }

        @Test
        void emptyEntitlements_returnsEmptyWithoutExamLookups() {
            UUID userId = UUID.randomUUID();
            when(entitlementRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId)).thenReturn(List.of());

            StudentLearningProvider.StudentLearningData data = provider.loadForUser(userId);

            assertThat(data.dataAvailable()).isFalse();
            assertThat(data.entitlements()).isEmpty();
            verify(entitlementRepository).findByUserIdOrderByCreatedAtAscIdAsc(userId);
            verifyNoInteractions(examAccessReadRepository, publishedExamPort);
        }

        @Test
        @SuppressWarnings("unchecked")
        void projectedExams_useSingleBatchLookupWithDistinctIdsAndSkipPublishedExamPort() {
            UUID userId = UUID.randomUUID();
            UUID exam1 = UUID.randomUUID();
            UUID exam2 = UUID.randomUUID();
            UUID ent1 = UUID.randomUUID();
            UUID ent2 = UUID.randomUUID();
            UUID ent3 = UUID.randomUUID();

            Entitlement purchase = mockEntitlement(ent1, exam1, EntitlementSource.PURCHASE, 4, 1, 1, 0);
            Entitlement classGrant = mockEntitlement(ent2, exam1, EntitlementSource.CLASS, 2, 0, 0, 0);
            Entitlement secondExam = mockEntitlement(ent3, exam2, EntitlementSource.CLASS, 3, 1, 0, 0);

            when(entitlementRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId))
                    .thenReturn(List.of(purchase, classGrant, secondExam));
            when(examAccessReadRepository.findProductsByExamIds(any()))
                    .thenReturn(List.of(
                            new ProductRef(exam1, "HSA-01", "hsa-01", SaleState.ON_SALE, "{\"name\":\"HSA Exam 1\"}"),
                            new ProductRef(exam2, "VACT-01", "vact-01", SaleState.ON_SALE, "{\"name\":\"V-ACT Exam 1\"}")));

            StudentLearningProvider.StudentLearningData data = provider.loadForUser(userId);

            ArgumentCaptor<Collection<UUID>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
            verify(examAccessReadRepository, times(1)).findProductsByExamIds(idsCaptor.capture());
            assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(exam1, exam2);
            verifyNoInteractions(publishedExamPort);

            assertThat(data.dataAvailable()).isTrue();
            assertThat(data.entitlements()).containsExactly(
                    new EntitlementItem(
                            ent1, exam1, "HSA-01", "HSA Exam 1", EntitlementLearningSource.PURCHASE,
                            4, 1, 1, 0, 2, EntitlementLearningStatus.ACTIVE),
                    new EntitlementItem(
                            ent2, exam1, "HSA-01", "HSA Exam 1", EntitlementLearningSource.CLASS,
                            2, 0, 0, 0, 2, EntitlementLearningStatus.ACTIVE),
                    new EntitlementItem(
                            ent3, exam2, "VACT-01", "V-ACT Exam 1", EntitlementLearningSource.CLASS,
                            3, 1, 0, 0, 2, EntitlementLearningStatus.ACTIVE));
        }

        @Test
        void unprojectedAndPartialStorefrontRows_fallBackToPublishedExamPortAndMergeFields() {
            UUID userId = UUID.randomUUID();
            UUID unprojectedExam = UUID.randomUUID();
            UUID emptyCardExam = UUID.randomUUID();
            UUID corruptJsonExam = UUID.randomUUID();
            UUID nonTextNameExam = UUID.randomUUID();
            UUID blankCodeExam = UUID.randomUUID();
            UUID missingEverywhereExam = UUID.randomUUID();
            UUID partialMissingServingExam = UUID.randomUUID();

            List<Entitlement> entitlements = List.of(
                    mockEntitlement(UUID.randomUUID(), unprojectedExam, EntitlementSource.PURCHASE, 2, 0, 0, 0),
                    mockEntitlement(UUID.randomUUID(), emptyCardExam, EntitlementSource.PURCHASE, 2, 0, 0, 0),
                    mockEntitlement(UUID.randomUUID(), corruptJsonExam, EntitlementSource.PURCHASE, 2, 0, 0, 0),
                    mockEntitlement(UUID.randomUUID(), nonTextNameExam, EntitlementSource.PURCHASE, 2, 0, 0, 0),
                    mockEntitlement(UUID.randomUUID(), blankCodeExam, EntitlementSource.CLASS, 2, 0, 0, 0),
                    mockEntitlement(UUID.randomUUID(), missingEverywhereExam, EntitlementSource.PURCHASE, 1, 0, 0, 0),
                    mockEntitlement(UUID.randomUUID(), partialMissingServingExam, EntitlementSource.PURCHASE, 1, 0, 0, 0));
            when(entitlementRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId)).thenReturn(entitlements);

            when(examAccessReadRepository.findProductsByExamIds(any())).thenReturn(List.of(
                    new ProductRef(emptyCardExam, "SF-EMPTY", "sf-empty", SaleState.ON_SALE, "{}"),
                    new ProductRef(corruptJsonExam, "SF-BAD", "sf-bad", SaleState.ON_SALE, "{not-valid-json"),
                    new ProductRef(nonTextNameExam, "SF-NUM", "sf-num", SaleState.ON_SALE, "{\"name\":123}"),
                    new ProductRef(blankCodeExam, "   ", "", SaleState.ON_SALE, "{\"name\":\"Storefront Name\"}"),
                    new ProductRef(partialMissingServingExam, "SF-ONLY", "sf-only", SaleState.ON_SALE, "{\"name\":\"  \"}")));

            when(publishedExamPort.findServing(unprojectedExam))
                    .thenReturn(Optional.of(servingExam(unprojectedExam, "UNPROJ-1", "Unprojected Exam")));
            when(publishedExamPort.findServing(emptyCardExam))
                    .thenReturn(Optional.of(servingExam(emptyCardExam, "SERV-EMPTY", "Fallback Empty Card Name")));
            when(publishedExamPort.findServing(corruptJsonExam))
                    .thenReturn(Optional.of(servingExam(corruptJsonExam, "SERV-BAD", "Fallback Bad Json Name")));
            when(publishedExamPort.findServing(nonTextNameExam))
                    .thenReturn(Optional.of(servingExam(nonTextNameExam, "SERV-NUM", "Fallback Numeric Name")));
            when(publishedExamPort.findServing(blankCodeExam))
                    .thenReturn(Optional.of(servingExam(blankCodeExam, "SERV-CODE", "Fallback Serving Name")));
            when(publishedExamPort.findServing(missingEverywhereExam)).thenReturn(Optional.empty());
            when(publishedExamPort.findServing(partialMissingServingExam)).thenReturn(Optional.empty());

            StudentLearningProvider.StudentLearningData data = provider.loadForUser(userId);

            assertThat(data.dataAvailable()).isTrue();
            assertThat(data.entitlements()).hasSize(7);

            assertThat(data.entitlements().get(0).examCode()).isEqualTo("UNPROJ-1");
            assertThat(data.entitlements().get(0).examName()).isEqualTo("Unprojected Exam");

            // Partial storefront rows preserve non-blank fields and fill only missing code/name from PublishedExamPort
            assertThat(data.entitlements().get(1).examCode()).isEqualTo("SF-EMPTY");
            assertThat(data.entitlements().get(1).examName()).isEqualTo("Fallback Empty Card Name");

            assertThat(data.entitlements().get(2).examCode()).isEqualTo("SF-BAD");
            assertThat(data.entitlements().get(2).examName()).isEqualTo("Fallback Bad Json Name");

            assertThat(data.entitlements().get(3).examCode()).isEqualTo("SF-NUM");
            assertThat(data.entitlements().get(3).examName()).isEqualTo("Fallback Numeric Name");

            assertThat(data.entitlements().get(4).examCode()).isEqualTo("SERV-CODE");
            assertThat(data.entitlements().get(4).examName()).isEqualTo("Storefront Name");
            assertThat(data.entitlements().get(4).source()).isEqualTo(EntitlementLearningSource.CLASS);

            // Absent from both storefront and PublishedExamPort -> preserves row with null code and name
            assertThat(data.entitlements().get(5).examCode()).isNull();
            assertThat(data.entitlements().get(5).examName()).isNull();

            assertThat(data.entitlements().get(6).examCode()).isEqualTo("SF-ONLY");
            assertThat(data.entitlements().get(6).examName()).isNull();
        }

        @Test
        void computeStatus_coversAllBranchesIncludingPartialRefundExhausted() {
            UUID userId = UUID.randomUUID();
            UUID examId = UUID.randomUUID();

            Entitlement active = mockEntitlement(UUID.randomUUID(), examId, EntitlementSource.PURCHASE, 4, 1, 1, 1);
            Entitlement inUse = mockEntitlement(UUID.randomUUID(), examId, EntitlementSource.PURCHASE, 3, 1, 1, 1);
            Entitlement revoked = mockEntitlement(UUID.randomUUID(), examId, EntitlementSource.PURCHASE, 2, 0, 0, 2);
            Entitlement exhaustedAllUsed =
                    mockEntitlement(UUID.randomUUID(), examId, EntitlementSource.PURCHASE, 2, 2, 0, 0);
            // R1-4: total=2, used=1, reserved=0, revoked=1, left=0 -> EXHAUSTED (not REVOKED)
            Entitlement partialRefundExhausted =
                    mockEntitlement(UUID.randomUUID(), examId, EntitlementSource.PURCHASE, 2, 1, 0, 1);

            when(entitlementRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId))
                    .thenReturn(List.of(active, inUse, revoked, exhaustedAllUsed, partialRefundExhausted));
            when(examAccessReadRepository.findProductsByExamIds(any()))
                    .thenReturn(List.of(new ProductRef(examId, "EX-S", "ex-s", SaleState.ON_SALE, "{\"name\":\"Exam S\"}")));

            StudentLearningProvider.StudentLearningData data = provider.loadForUser(userId);

            assertThat(data.entitlements())
                    .extracting(EntitlementItem::status)
                    .containsExactly(
                            EntitlementLearningStatus.ACTIVE,
                            EntitlementLearningStatus.IN_USE,
                            EntitlementLearningStatus.REVOKED,
                            EntitlementLearningStatus.EXHAUSTED,
                            EntitlementLearningStatus.EXHAUSTED);
        }

        private static Entitlement mockEntitlement(
                UUID id, UUID examId, EntitlementSource source, int total, int used, int reserved, int revoked) {
            Entitlement entitlement = mock(Entitlement.class);
            when(entitlement.getId()).thenReturn(id);
            when(entitlement.getExamId()).thenReturn(examId);
            when(entitlement.getSource()).thenReturn(source);
            when(entitlement.getAttemptsTotal()).thenReturn(total);
            when(entitlement.getAttemptsUsed()).thenReturn(used);
            when(entitlement.getAttemptsReserved()).thenReturn(reserved);
            when(entitlement.getAttemptsRevoked()).thenReturn(revoked);
            when(entitlement.attemptsLeft()).thenReturn(total - used - reserved - revoked);
            return entitlement;
        }

        private static ServingExam servingExam(UUID examId, String code, String name) {
            PublishedVersion version = new PublishedVersion(
                    examId,
                    1,
                    "PUBLISHED",
                    "ON_SALE",
                    NOW,
                    code,
                    name,
                    UUID.randomUUID(),
                    "HSA",
                    "PART",
                    "TOAN",
                    null,
                    "STANDARD",
                    "SHARED",
                    60,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
            return new ServingExam(examId, "PUBLISHED", version, List.of());
        }
    }

    @Nested
    class StudentLearningResponseSchemaAnnotationTests {

        @Test
        void responseAndNestedItemRecordsDeclareExpectedRequiredAndNullableSchemas() {
            Map<String, Schema> responseSchemas = recordSchemas(StudentLearningResponse.class);
            assertThat(responseSchemas.keySet())
                    .containsExactlyInAnyOrder(
                            "userId",
                            "dataAvailable",
                            "entitlements",
                            "attempts",
                            "assessments",
                            "practice",
                            "supportRequests");
            responseSchemas.forEach((name, schema) -> {
                assertThat(schema.requiredMode())
                        .as("StudentLearningResponse.%s requiredMode", name)
                        .isEqualTo(Schema.RequiredMode.REQUIRED);
                assertThat(List.of(schema.types()))
                        .as("StudentLearningResponse.%s types", name)
                        .doesNotContain("null");
            });

            Map<String, Schema> entitlementSchemas = recordSchemas(StudentLearningEntitlement.class);
            for (String required : List.of(
                    "entitlementId",
                    "examId",
                    "source",
                    "attemptsTotal",
                    "attemptsUsed",
                    "attemptsReserved",
                    "attemptsRevoked",
                    "attemptsLeft",
                    "status")) {
                assertThat(entitlementSchemas.get(required).requiredMode())
                        .as("StudentLearningEntitlement.%s requiredMode", required)
                        .isEqualTo(Schema.RequiredMode.REQUIRED);
                assertThat(List.of(entitlementSchemas.get(required).types())).doesNotContain("null");
            }
            for (String nullable : List.of("examCode", "examName")) {
                Schema schema = entitlementSchemas.get(nullable);
                assertThat(schema.requiredMode())
                        .as("StudentLearningEntitlement.%s requiredMode", nullable)
                        .isNotEqualTo(Schema.RequiredMode.REQUIRED);
                assertThat(List.of(schema.types()))
                        .as("StudentLearningEntitlement.%s types", nullable)
                        .containsExactlyInAnyOrder("string", "null");
            }

            Map<String, Schema> attemptSchemas = recordSchemas(StudentLearningAttempt.class);
            for (String required : List.of("attemptId", "examId", "examName", "examKindCode", "scope", "status")) {
                assertThat(attemptSchemas.get(required).requiredMode())
                        .as("StudentLearningAttempt.%s requiredMode", required)
                        .isEqualTo(Schema.RequiredMode.REQUIRED);
                assertThat(List.of(attemptSchemas.get(required).types())).doesNotContain("null");
            }
            assertNullableSchema(attemptSchemas.get("attemptNo"), "StudentLearningAttempt.attemptNo", "integer", "");
            assertNullableSchema(
                    attemptSchemas.get("startedAt"), "StudentLearningAttempt.startedAt", "string", "date-time");
            assertNullableSchema(
                    attemptSchemas.get("finishedAt"), "StudentLearningAttempt.finishedAt", "string", "date-time");
            assertNullableSchema(attemptSchemas.get("score"), "StudentLearningAttempt.score", "number", "");
            assertNullableSchema(attemptSchemas.get("maxScore"), "StudentLearningAttempt.maxScore", "number", "");
        }

        private static void assertNullableSchema(
                Schema schema, String propertyName, String expectedBaseType, String expectedFormat) {
            assertThat(schema.requiredMode())
                    .as("%s requiredMode", propertyName)
                    .isNotEqualTo(Schema.RequiredMode.REQUIRED);
            assertThat(List.of(schema.types()))
                    .as("%s types", propertyName)
                    .containsExactlyInAnyOrder(expectedBaseType, "null");
            assertThat(schema.format()).as("%s format", propertyName).isEqualTo(expectedFormat);
        }

        private static Map<String, Schema> recordSchemas(Class<?> recordClass) {
            Map<String, Schema> result = new LinkedHashMap<>();
            for (RecordComponent component : recordClass.getRecordComponents()) {
                Schema schema = component.getAnnotation(Schema.class);
                if (schema == null && component.getAccessor() != null) {
                    schema = component.getAccessor().getAnnotation(Schema.class);
                }
                if (schema == null) {
                    try {
                        Field field = recordClass.getDeclaredField(component.getName());
                        schema = field.getAnnotation(Schema.class);
                    } catch (NoSuchFieldException ex) {
                        throw new AssertionError("Missing record field " + component.getName(), ex);
                    }
                }
                assertThat(schema)
                        .as("@Schema on %s.%s", recordClass.getSimpleName(), component.getName())
                        .isNotNull();
                result.put(component.getName(), schema);
            }
            return result;
        }
    }
}
