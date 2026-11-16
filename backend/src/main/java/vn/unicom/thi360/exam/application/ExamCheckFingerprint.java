package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.DigestOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.exam.domain.Exam;

/** Canonical fingerprint of a check's structure, configuration and reference revisions. */
@Component
public class ExamCheckFingerprint {
    private final ObjectMapper mapper;

    public ExamCheckFingerprint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Config(UUID kindItemId, String scope, String partCode, UUID subjectItemId,
                         String templateCode, Integer structureYear, Integer matrixYear,
                         boolean yearsConfirmed, String timing, Integer durationMinutes,
                         BigDecimal maxScore, BigDecimal referenceMax, String practiceScoring) {
        public static Config from(Exam exam) {
            return new Config(exam.getKindItemId(), exam.getScope(), exam.getPartCode(),
                    exam.getSubjectItemId(), exam.getTemplateCode(), exam.getStructureYear(),
                    exam.getMatrixYear(), exam.isYearsConfirmed(), exam.getTiming(),
                    exam.getDurationMinutes(), exam.getMaxScore(), exam.getReferenceMax(),
                    exam.getPracticeScoring());
        }
    }

    public String calculate(JsonNode structure, Config config, Long profileRevision, long catalogRevision) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        try (var out = new DigestOutputStream(OutputStream.nullOutputStream(), digest);
             JsonGenerator json = mapper.getFactory().createGenerator(out)) {
            json.writeStartArray();
            writeCanonical(json, structure);
            writeCanonical(json, mapper.valueToTree(config));
            if (profileRevision == null) json.writeNull(); else json.writeNumber(profileRevision);
            json.writeNumber(catalogRevision);
            json.writeEndArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not fingerprint exam check input", e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void writeCanonical(JsonGenerator json, JsonNode node) throws IOException {
        if (node == null || node.isNull()) {
            json.writeNull();
        } else if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            json.writeStartObject();
            for (String name : names) {
                json.writeFieldName(name);
                writeCanonical(json, node.get(name));
            }
            json.writeEndObject();
        } else if (node.isArray()) {
            json.writeStartArray();
            for (JsonNode child : node) writeCanonical(json, child);
            json.writeEndArray();
        } else {
            mapper.writeTree(json, node);
        }
    }
}
