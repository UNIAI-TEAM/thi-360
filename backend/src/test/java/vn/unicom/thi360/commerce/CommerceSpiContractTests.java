package vn.unicom.thi360.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;
import vn.unicom.thi360.commerce.spi.CompensationResult;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementDescription;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.spi.GrantSource;

class CommerceSpiContractTests {

    /** The exact SPI AT-1 / CM-4a build against (plan B1, AD-8): name, parameter types, result type. */
    @Test
    void entitlementPortDeclaresExactlyTheSixContractSignatures() {
        List<String> signatures = Stream.of(EntitlementPort.class.getDeclaredMethods())
                .map(CommerceSpiContractTests::signature)
                .sorted()
                .toList();

        assertThat(signatures).containsExactlyInAnyOrder(
                "EntitlementDescription describe(UUID, UUID)",
                "EntitlementBenefits reserve(UUID, UUID, UUID)",
                "void confirmUse(UUID)",
                "void release(UUID)",
                "EntitlementBenefits benefitsOf(UUID)",
                "CompensationResult compensate(UUID, UUID, String)");
        assertThat(signatures).hasSize(6);
        assertThat(Stream.of(EntitlementPort.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("grantForOrder", "revokeUnused");
    }

    @Test
    void describeAndReserveResultsCarryTheExamOfTheGrant() {
        assertThat(components(EntitlementDescription.class)).containsExactly(
                "UUID examId", "int attemptsLeft", "UUID grantId", "GrantSource source",
                "String orderCode", "Instant grantedAt", "List optionCodes");
        assertThat(components(EntitlementBenefits.class)).containsExactly(
                "UUID grantId", "UUID examId", "Map benefits", "String resultView",
                "List optionCodes", "int examVersionNo");
    }

    /** The exam id is its own value, distinct from the grant and entitlement ids it travels with. */
    @Test
    void examIdIsKeptApartFromGrantAndEntitlementIds() {
        UUID examId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        UUID entitlementId = UUID.randomUUID();

        EntitlementDescription description = new EntitlementDescription(
                examId, 2, grantId, GrantSource.PURCHASE, "ORD-1", Instant.EPOCH, List.of("OPT"));
        EntitlementBenefits benefits = new EntitlementBenefits(
                grantId, examId, Map.of("review", true), "FULL", List.of("OPT"), 3);
        CompensationResult compensation = new CompensationResult(entitlementId, grantId);

        assertThat(description.examId()).isEqualTo(examId).isNotEqualTo(grantId).isNotEqualTo(entitlementId);
        assertThat(description.grantId()).isEqualTo(grantId);
        assertThat(benefits.examId()).isEqualTo(examId).isNotEqualTo(grantId);
        assertThat(benefits.grantId()).isEqualTo(grantId);
        assertThat(compensation.entitlementId()).isEqualTo(entitlementId);
    }

    private static String signature(Method method) {
        return method.getReturnType().getSimpleName() + " " + method.getName() + "("
                + Stream.of(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", "))
                + ")";
    }

    private static List<String> components(Class<? extends Record> type) {
        return Stream.of(type.getRecordComponents())
                .map(c -> c.getType().getSimpleName() + " " + c.getName())
                .toList();
    }

    @Test
    void emptyAttemptStatusProviderReportsNoAttempts() {
        AttemptStatusProvider provider = AttemptStatusProvider.EMPTY;
        UUID userId = UUID.randomUUID();
        UUID entitlementId = UUID.randomUUID();

        Optional<AttemptStatusProvider.OpenAttempt> open = provider.findOpenAttempt(userId, entitlementId);
        Optional<AttemptStatusProvider.ResultAttempt> result =
                provider.findLatestResultAttempt(userId, entitlementId);

        assertThat(open).isEmpty();
        assertThat(result).isEmpty();
    }
}
