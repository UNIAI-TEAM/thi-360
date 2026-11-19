package vn.unicom.thi360.exam;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** Plan B5: the exam module reads time from the {@code Clock} bean, so the e2e test clock reaches it. */
class ExamClockUsageTest {

    private static final JavaClasses EXAM = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("vn.unicom.thi360.exam");

    @Test
    void examModuleReadsTimeFromClock() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .should().callMethod(Instant.class, "now")
                .orShould().callMethod(LocalDate.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(OffsetDateTime.class, "now")
                .orShould().callMethod(ZonedDateTime.class, "now")
                .orShould().callMethod(System.class, "currentTimeMillis")
                .because("the exam module takes the time from the Clock bean (plan B5)");
        rule.check(EXAM);
    }
}
