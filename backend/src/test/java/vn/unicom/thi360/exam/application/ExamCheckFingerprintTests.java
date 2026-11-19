package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExamCheckFingerprintTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExamCheckFingerprint fingerprint = new ExamCheckFingerprint(mapper);

    @Test
    void canonicalObjectsMatchButInputAndReferenceChangesInvalidate() throws Exception {
        var config = new ExamCheckFingerprint.Config(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "FULL", null, null, "HSA", 2026, 2026, true, "SHARED", 120,
                BigDecimal.TEN, BigDecimal.valueOf(100), "RAW");
        var structure = mapper.readTree("{\"parts\":[{\"id\":\"p\",\"x\":1}]}");
        var reordered = mapper.readTree("{\"parts\":[{\"x\":1,\"id\":\"p\"}]}");
        String original = fingerprint.calculate(structure, config, 4L, 5L);

        assertThat(fingerprint.calculate(reordered, config, 4L, 5L)).isEqualTo(original);
        assertThat(fingerprint.calculate(mapper.readTree("{\"parts\":[]}"), config, 4L, 5L)).isNotEqualTo(original);
        assertThat(fingerprint.calculate(structure, config, 5L, 5L)).isNotEqualTo(original);
        assertThat(fingerprint.calculate(structure, config, 4L, 6L)).isNotEqualTo(original);
        var changedConfig = new ExamCheckFingerprint.Config(config.kindItemId(), config.scope(),
                config.partCode(), config.subjectItemId(), config.templateCode(), config.structureYear(),
                config.matrixYear(), config.yearsConfirmed(), config.timing(), 121,
                config.maxScore(), config.referenceMax(), config.practiceScoring());
        assertThat(fingerprint.calculate(structure, changedConfig, 4L, 5L)).isNotEqualTo(original);
        var withQuota = mapper.readTree("""
                {"parts":[{"id":"p","x":1}],
                 "examQuotas":[{"id":"q","enforcement":"REQUIRED","subjectCodes":["TOAN"]}]}
                """);
        assertThat(fingerprint.calculate(withQuota, config, 4L, 5L)).isNotEqualTo(original);
    }
}
