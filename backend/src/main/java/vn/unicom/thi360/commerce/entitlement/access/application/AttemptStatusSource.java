package vn.unicom.thi360.commerce.entitlement.access.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;

/**
 * The {@link AttemptStatusProvider} of the moment (LT 3, plan B1): the attempt module's bean once it exists, the empty
 * default of {@code commerce/spi} before that. It is looked up on every call, so the answer follows the application
 * context and nothing is cached here.
 */
@Component
public class AttemptStatusSource {

    private final ObjectProvider<AttemptStatusProvider> provider;

    public AttemptStatusSource(ObjectProvider<AttemptStatusProvider> provider) {
        this.provider = provider;
    }

    public AttemptStatusProvider get() {
        return provider.getIfAvailable(() -> AttemptStatusProvider.EMPTY);
    }
}
