package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.exam.application.ExamCheckCompletion;
import vn.unicom.thi360.exam.application.ExamCheckOrchestrator;
import vn.unicom.thi360.exam.application.ExamCheckResultQuery;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.security.RequirePermission;
import vn.unicom.thi360.shared.api.NotImplementedException;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@RestController
@RequestMapping("/api/v1/admin/exams/{examId}/check")
@Tag(name = "Kiểm tra cấu hình bài thi")
public class ExamCheckController {
    private final ExamCheckOrchestrator checks;
    private final ExamCheckResultQuery query;
    private final CurrentUserProvider users;

    public ExamCheckController(ExamCheckOrchestrator checks, ExamCheckResultQuery query, CurrentUserProvider users) {
        this.checks = checks;
        this.query = query;
        this.users = users;
    }

    @GetMapping
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(operationId = "getLatestExamCheck", summary = "Xem kết quả kiểm tra gần nhất")
    @ApiResponse(responseCode = "200", description = "Latest check, or null when not checked",
            content = @Content(schema = @Schema(implementation = ExamCheckApiModels.Result.class, nullable = true)))
    public JsonNode latest(@PathVariable UUID examId) {
        ObjectNode result = query.latest(examId);
        return result == null ? NullNode.getInstance() : result;
    }

    @Schema(name = "ExamCheckStarted")
    public record Started(UUID runId) { }

    @Schema(name = "ExamCheckRequest")
    public record CheckRequest(
            @Schema(allowableValues = {"EXAM", "PART", "SECTION"}) String scope,
            String sectionId, String partId) { }

    @PostMapping
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(operationId = "checkExam", summary = "Kiểm tra cấu hình bài thi")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Synchronous check",
                content = @Content(schema = @Schema(implementation = ExamCheckApiModels.Result.class))),
        @ApiResponse(responseCode = "202", description = "Background check started or already running",
                content = @Content(schema = @Schema(implementation = Started.class)))
    })
    public ResponseEntity<?> check(@PathVariable UUID examId,
            @RequestParam(defaultValue = "false") boolean deep,
            @RequestBody(required = false) CheckRequest request) {
        if (request != null && request.scope() != null && !"EXAM".equals(request.scope())) {
            throw new NotImplementedException();
        }
        var actor = actor();
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (deep) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new Started(checks.startDeep(examId, actor, traceId)));
        }
        return ResponseEntity.ok(checks.check(examId, actor, traceId));
    }

    @GetMapping("/runs/{runId}")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(operationId = "getExamCheckRun", summary = "Xem trạng thái kiểm tra kỹ")
    @ApiResponse(responseCode = "200", description = "Background check state",
            content = @Content(schema = @Schema(implementation = ExamCheckApiModels.Run.class)))
    public ExamCheckOrchestrator.Run run(@PathVariable UUID examId, @PathVariable UUID runId) {
        return checks.run(examId, runId, actor(), MDC.get(TraceIdFilter.MDC_TRACE_ID));
    }

    private ExamCheckCompletion.Actor actor() {
        CurrentUser user = users.requireCurrentUser();
        return new ExamCheckCompletion.Actor(user.id(), user.username(),
                user.realm() == Realm.MASTER ? AuditActorType.PLATFORM : AuditActorType.USER);
    }
}
