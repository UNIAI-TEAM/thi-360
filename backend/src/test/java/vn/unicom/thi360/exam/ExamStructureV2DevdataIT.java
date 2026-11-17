package vn.unicom.thi360.exam;

import org.springframework.test.context.ActiveProfiles;

/** Devdata structures under the `test` profile. */
@ActiveProfiles({"api", "worker", "test"})
class ExamStructureV2DevdataIT extends AbstractExamStructureV2DevdataIT {
}
