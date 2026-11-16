package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.exam.application.ExamPublishService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Exam versions")
@RestController
@RequestMapping("/api/v1/admin/exams/{examId}")
public class ExamVersionController {
    private final ExamPublishService service;

    public ExamVersionController(ExamPublishService service) { this.service = service; }

    @Operation(operationId = "publishExam")
    @RequirePermission(Permissions.EXAM_PUBLISH)
    @PostMapping("/publish")
    public ExamPublishResponse publish(@PathVariable UUID examId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ExamPublishRequest request) {
        return service.publish(examId, key, request);
    }
}
