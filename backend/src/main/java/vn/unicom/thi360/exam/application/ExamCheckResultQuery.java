package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.shared.api.NotFoundException;

@Service
public class ExamCheckResultQuery {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExamCheckValidity validity;
    private final ExamCheckUnitStates states;

    public ExamCheckResultQuery(JdbcTemplate jdbc, ObjectMapper mapper, ExamCheckValidity validity,
            ExamCheckUnitStates states) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.validity = validity;
        this.states = states;
    }

    @Transactional(readOnly = true)
    public ObjectNode latest(UUID examId) {
        List<String> rows = jdbc.query("SELECT r.result::text FROM exam.exam e "
                        + "LEFT JOIN exam.exam_check_run r ON r.exam_id = e.id WHERE e.id = ?",
                (rs, row) -> rs.getString(1), examId);
        if (rows.isEmpty()) throw new NotFoundException("Exam not found");
        if (rows.getFirst() == null) return null;
        try {
            var value = mapper.readTree(rows.getFirst());
            if (!(value instanceof ObjectNode result)) {
                throw new IllegalStateException("Invalid persisted check result");
            }
            String reason = validity.assess(List.of(examId)).get(examId).invalidReason();
            if (reason == null) result.putNull("invalidReason"); else result.put("invalidReason", reason);
            result.put("stale", reason != null);
            result.set("sectionStates", mapper.valueToTree(states.states(examId)));
            return result;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid persisted check result", e);
        }
    }
}
