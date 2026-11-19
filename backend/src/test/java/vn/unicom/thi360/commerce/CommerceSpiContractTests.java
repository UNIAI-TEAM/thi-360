package vn.unicom.thi360.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;
import vn.unicom.thi360.commerce.spi.EntitlementPort;

class CommerceSpiContractTests {

    @Test
    void entitlementPortDeclaresExactlyTheSixContractMethods() {
        Set<String> methodNames = Set.of(EntitlementPort.class.getDeclaredMethods()).stream()
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(methodNames).containsExactlyInAnyOrder(
                "describe", "reserve", "confirmUse", "release", "benefitsOf", "compensate");
        assertThat(methodNames).doesNotContain("grantForOrder", "revokeUnused");
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
