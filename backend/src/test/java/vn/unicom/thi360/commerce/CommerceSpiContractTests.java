package vn.unicom.thi360.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
        Method[] declared = EntitlementPort.class.getDeclaredMethods();

        assertThat(declared).hasSize(6);
        assertContractMethod(
                declared, "describe", EntitlementDescription.class, UUID.class, UUID.class);
        assertContractMethod(
                declared, "reserve", EntitlementBenefits.class, UUID.class, UUID.class, UUID.class);
        assertContractMethod(declared, "confirmUse", void.class, UUID.class);
        assertContractMethod(declared, "release", void.class, UUID.class);
        assertContractMethod(declared, "benefitsOf", EntitlementBenefits.class, UUID.class);
        assertContractMethod(
                declared, "compensate", CompensationResult.class, UUID.class, UUID.class, String.class);
        assertThat(Stream.of(declared).map(Method::getName))
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

    private static void assertContractMethod(
            Method[] declared, String name, Class<?> returnType, Class<?>... parameterTypes) {
        Method[] matching = Stream.of(declared)
                .filter(m -> m.getName().equals(name))
                .toArray(Method[]::new);
        assertThat(matching).as("exactly one method named %s", name).hasSize(1);
        assertThat(matching[0].getReturnType()).isEqualTo(returnType);
        assertThat(matching[0].getParameterTypes()).containsExactly(parameterTypes);
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
