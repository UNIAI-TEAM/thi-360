package vn.unicom.thi360.exam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads the questions a check selected, per block, from the stored witness of the latest check run.
 * The check response carries the verdict; the witness is the proof that a solve actually picked questions.
 */
final class CheckWitnessPicks {
    private CheckWitnessPicks() { }

    /** Selected root question IDs by group (block) ID, in the order the solver picked them. */
    static Map<String, List<UUID>> byBlock(JdbcTemplate jdbc, ObjectMapper mapper, UUID examId) {
        String stored = jdbc.queryForObject("SELECT witness::text FROM exam.exam_check_run WHERE exam_id = ?",
                String.class, examId);
        Map<String, List<UUID>> picks = new LinkedHashMap<>();
        try {
            collect(mapper.readTree(stored), picks);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored witness is not JSON", e);
        }
        return picks;
    }

    private static void collect(JsonNode node, Map<String, List<UUID>> picks) {
        if (node.isObject() && node.hasNonNull("groupId") && node.path("picks").isArray()) {
            var list = picks.computeIfAbsent(node.path("groupId").asText(), key -> new java.util.ArrayList<>());
            for (JsonNode pick : node.path("picks")) list.add(UUID.fromString(pick.path("questionId").asText()));
            return;
        }
        node.forEach(child -> collect(child, picks));
    }
}
