package vn.unicom.thi360.exam;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Runs Flyway and the application with the dev profile on a fresh database. */
@ActiveProfiles(value = {"api", "worker", "dev"}, inheritProfiles = false)
@TestPropertySource(properties = "thi360.s3.mode=memory")
class ExamStructureV2DevdataDevIT extends AbstractExamStructureV2DevdataIT {
}
