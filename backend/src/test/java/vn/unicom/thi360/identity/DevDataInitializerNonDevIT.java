package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.application.DevDataInitializer;

class DevDataInitializerNonDevIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void nonDevProfile_doesNotRegisterDevDataInitializer() {
        assertThat(applicationContext.getBeansOfType(DevDataInitializer.class)).isEmpty();
    }
}
