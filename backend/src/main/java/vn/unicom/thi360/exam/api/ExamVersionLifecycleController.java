package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.exam.application.ExamDiscontinueService;
import vn.unicom.thi360.exam.application.ExamVersionQueryService;
import vn.unicom.thi360.exam.application.ExamService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Exam versions")
@RestController
@RequestMapping("/api/v1/admin/exams/{examId}")
public class ExamVersionLifecycleController {
    private final ExamDiscontinueService discontinue;
    private final ExamVersionQueryService versions;
    private final ExamService exams;

    public ExamVersionLifecycleController(ExamDiscontinueService discontinue, ExamVersionQueryService versions,
            ExamService exams) {
        this.discontinue = discontinue;
        this.versions = versions;
        this.exams = exams;
    }

    @Schema(name = "ExamDiscontinueRequest")
    public record DiscontinueRequest(@NotNull Long expectedRevision) {}

    @Operation(operationId = "discontinueExam")
    @RequirePermission(Permissions.EXAM_PUBLISH)
    @PostMapping("/discontinue")
    public ExamDetailResponse discontinue(@PathVariable UUID examId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DiscontinueRequest request) {
        return discontinue.discontinue(examId, key, request);
    }

    @Operation(operationId = "listExamVersions")
    @RequirePermission(Permissions.EXAM_VIEW)
    @GetMapping("/versions")
    public ExamVersionList list(@PathVariable UUID examId) {
        return versions.list(examId, exams.getExamDetail(examId));
    }
}
