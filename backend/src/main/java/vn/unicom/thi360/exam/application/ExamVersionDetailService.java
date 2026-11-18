package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.api.ExamVersionDetail;
import vn.unicom.thi360.exam.api.ExamVersionPath;
import vn.unicom.thi360.exam.api.ExamVersionResponse;
import vn.unicom.thi360.exam.api.PersonRef;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.api.NotFoundException;

@Service
public class ExamVersionDetailService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final UserDirectory users;

    public ExamVersionDetailService(JdbcTemplate jdbc, ObjectMapper mapper, UserDirectory users) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ExamVersionDetail detail(UUID examId, int version) {
        List<Version> rows = jdbc.query("SELECT version_no, state, published_at, published_by, note, config::text, parts::text, published_path_ids, serving_path_ids FROM exam.exam_version WHERE exam_id = ? AND version_no = ?",
                (rs, n) -> new Version(rs.getInt(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
                        rs.getObject(4, UUID.class), rs.getString(5), json(rs.getString(6)), json(rs.getString(7)),
                        List.of((String[]) rs.getArray(8).getArray()), List.of((String[]) rs.getArray(9).getArray())), examId, version);
        if (rows.isEmpty()) throw new NotFoundException(ExamErrorCodes.VERSION_NOT_FOUND, "Exam version not found");
        Version current = rows.getFirst();
        List<Snapshot> previous = jdbc.query("SELECT config::text, parts::text FROM exam.exam_version WHERE exam_id = ? AND version_no < ? ORDER BY version_no DESC LIMIT 1",
                (rs, n) -> new Snapshot(json(rs.getString(1)), json(rs.getString(2))), examId, version);
        PersonRef actor = users.findById(current.actor()).map(user -> new PersonRef(user.id(), user.displayName()))
                .orElseGet(() -> new PersonRef(current.actor(), null));
        ExamVersionResponse row = new ExamVersionResponse(version, current.state(), current.publishedAt(), actor, 0,
                current.note(), paths(current.published(), current.parts(), current.config()),
                paths(current.serving(), current.parts(), current.config()));
        var changes = previous.isEmpty() ? List.<vn.unicom.thi360.exam.api.ExamVersionChange>of()
                : ExamVersionChanges.between(previous.getFirst().config(), previous.getFirst().parts(), current.config(), current.parts());
        List<JsonNode> parts = StreamSupport.stream(current.parts().path("parts").spliterator(), false).toList();
        return new ExamVersionDetail(examId, row, current.config(), parts, changes);
    }

    private List<ExamVersionPath> paths(List<String> ids, JsonNode document, JsonNode config) {
        List<ExamVersionPath> paths = new ArrayList<>();
        for (String id : ids) {
            Set<String> selected = "default".equals(id) ? Set.of() : Set.copyOf(Arrays.asList(id.split("\\+")));
            List<ExamVersionPath.Choice> choices = new ArrayList<>();
            for (JsonNode part : document.path("parts")) {
                for (JsonNode option : part.path("options")) {
                    if (selected.contains(option.path("id").asText())) {
                        String code = option.path("code").asText();
                        choices.add(new ExamVersionPath.Choice(part.path("id").asText(), option.path("id").asText(),
                                option.path("kind").asText(), code, option.path("name").asText(code)));
                    }
                }
                for (JsonNode combination : config.path("combinations")) {
                    String partId = part.path("id").asText();
                    String code = combination.path("code").asText();
                    String optionId = partId + ":" + code;
                    if (partId.equals(combination.path("partId").asText()) && selected.contains(optionId)) {
                        choices.add(new ExamVersionPath.Choice(partId, optionId, "COMBINATION", code,
                                combination.path("name").asText(code)));
                    }
                }
            }
            paths.add(new ExamVersionPath(id, List.copyOf(choices)));
        }
        return List.copyOf(paths);
    }

    private JsonNode json(String value) {
        try { return mapper.readTree(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid version snapshot", e); }
    }

    private record Version(int number, String state, Instant publishedAt, UUID actor, String note,
                           JsonNode config, JsonNode parts, List<String> published, List<String> serving) { }
    private record Snapshot(JsonNode config, JsonNode parts) { }
}
