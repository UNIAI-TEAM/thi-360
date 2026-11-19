package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.exam.application.ExamListingService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Exam listing", description = "Thông tin bán của bài thi")
@RestController
@RequestMapping("/api/v1/admin/exams/{examId}/listing")
public class ExamListingController {
    private final ExamListingService listings;

    public ExamListingController(ExamListingService listings) {
        this.listings = listings;
    }

    @GetMapping
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(operationId = "getExamListing", summary = "Thông tin bán: bản làm việc, bản đang bán, lỗi kiểm tra")
    public ExamListingResponse get(@PathVariable UUID examId) {
        return listings.get(examId);
    }

    @PutMapping
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(operationId = "updateExamListing", summary = "Lưu thông tin bán của bài thi")
    public ExamListingResponse update(@PathVariable UUID examId, @Valid @RequestBody UpdateExamListingRequest request) {
        return listings.update(examId, request);
    }
}
