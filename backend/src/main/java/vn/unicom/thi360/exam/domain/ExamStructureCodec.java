package vn.unicom.thi360.exam.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;

/** JSON boundary for the jsonb structure document (spec 9.2). */
public final class ExamStructureCodec {
    private final ObjectMapper mapper;

    public ExamStructureCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    public ExamStructureDocument read(JsonNode json) {
        if (json == null || json.isNull()) {
            throw new IllegalArgumentException("structure must not be null");
        }
        try {
            return mapper.treeToValue(json, ExamStructureDocument.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid exam structure JSON", ex);
        }
    }

    public ExamStructureDocument read(String json) {
        try {
            return read(mapper.readTree(json));
        } catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("invalid exam structure JSON", ex);
        }
    }

    public JsonNode write(ExamStructureDocument document) {
        return mapper.valueToTree(Objects.requireNonNull(document, "document must not be null"));
    }

    public String writeString(ExamStructureDocument document) {
        try {
            return mapper.writeValueAsString(document);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("cannot serialize exam structure", ex);
        }
    }
}
