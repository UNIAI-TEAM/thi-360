package vn.unicom.thi360;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(Thi360Application.class).verify();
    }
}
