package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.unicom.thi360.exam.api.ExamListingCheckResult;
import vn.unicom.thi360.exam.infrastructure.ExamListingProperties;
import vn.unicom.thi360.shared.api.NotFoundException;

/** Runs {@link ExamListingCheck} on the working listing of an exam, as it is now. */
@Service
public class ExamListingChecks {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExamListingCheck check;
    private final long minPriceVnd;

    public ExamListingChecks(JdbcTemplate jdbc, ObjectMapper mapper, ExamListingProperties properties) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.minPriceVnd = properties.minPriceVnd();
        this.check = new ExamListingCheck(minPriceVnd);
    }

    public long minPriceVnd() {
        return minPriceVnd;
    }

    /** Issues of the working listing; an exam without a listing row reads as an empty listing. */
    public List<ExamListingCheckResult.Issue> issues(UUID examId) {
        var inputs = jdbc.query("SELECT l.list_price_vnd, l.attempts_granted, e.description, e.cover_asset_id "
                        + "FROM exam.exam e LEFT JOIN exam.exam_listing l ON l.exam_id = e.id WHERE e.id = ?",
                (rs, n) -> new ExamListingCheck.Input((Long) rs.getObject(1), (Integer) rs.getObject(2),
                        rs.getString(3), rs.getObject(4, UUID.class)), examId);
        if (inputs.isEmpty()) throw new NotFoundException("Exam not found");
        return check.evaluate(inputs.getFirst());
    }

    public ExamListingCheckResult group(UUID examId) {
        return ExamListingCheck.group(issues(examId));
    }

    /**
     * A copy of a check result with {@code listingCheck} added, computed now. The stored result (and its status,
     * issue count and groups) is never touched: the listing is not part of what the check fingerprints.
     */
    public ObjectNode withListingCheck(ObjectNode result, UUID examId) {
        ObjectNode copy = result.deepCopy();
        copy.set("listingCheck", mapper.valueToTree(group(examId)));
        return copy;
    }
}
