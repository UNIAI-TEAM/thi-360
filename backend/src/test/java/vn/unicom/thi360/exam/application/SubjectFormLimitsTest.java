package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** FTM R-12 table (CR-4 AC-8): the web compares its own table with this one. */
class SubjectFormLimitsTest {
    @ParameterizedTest
    @ValueSource(strings = {"HSA", "QDA"})
    void physicsChemistryBiologyNeedAShortAnswer(String kind) {
        for (String subject : List.of("LY", "HOA", "SINH")) {
            assertThat(SubjectFormLimits.of(kind, subject)).hasValue(new SubjectFormLimits.Limit(1, 2, 3, 1));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"HSA", "QDA"})
    void historyGeographyAllowNoShortAnswer(String kind) {
        for (String subject : List.of("SU", "DIA")) {
            assertThat(SubjectFormLimits.of(kind, subject)).hasValue(new SubjectFormLimits.Limit(1, 2, 3, 0));
        }
    }

    @Test
    void everyOtherPairHasNoLimit() {
        for (String kind : List.of("BCA", "HSCA", "VSAT", "THPTQG", "SP2E", "SPT", "VACT", "ZZZ", "hsa", "")) {
            for (String subject : List.of("LY", "HOA", "SINH", "SU", "DIA")) {
                assertThat(SubjectFormLimits.of(kind, subject)).as(kind + "/" + subject).isEmpty();
            }
        }
        for (String subject : List.of("TOAN", "VAN", "ANH", "TIN", "GDKTPL", "ly", "PHYSICS", "")) {
            assertThat(SubjectFormLimits.of("HSA", subject)).as("HSA/" + subject).isEmpty();
            assertThat(SubjectFormLimits.of("QDA", subject)).as("QDA/" + subject).isEmpty();
        }
    }

    @Test
    void nullCodesHaveNoLimit() {
        assertThat(SubjectFormLimits.of(null, "LY")).isEmpty();
        assertThat(SubjectFormLimits.of("HSA", null)).isEmpty();
    }
}
