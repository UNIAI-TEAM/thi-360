package vn.unicom.thi360.exam;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.unicom.thi360.classroom.application.DevClassSeeder;
import vn.unicom.thi360.identity.application.DevDataInitializer;

/** Runs Flyway and the application with the dev profile on a fresh database. */
@ActiveProfiles(value = {"api", "worker", "dev"}, inheritProfiles = false)
@TestPropertySource(properties = "thi360.s3.mode=memory")
class ExamStructureV2DevdataDevIT extends AbstractExamStructureV2DevdataIT {
    // Identity and classroom account seeds require Keycloak; Flyway exam devdata does not.
    @MockitoBean
    private DevDataInitializer devDataInitializer;

    @MockitoBean
    private DevClassSeeder devClassSeeder;
}
