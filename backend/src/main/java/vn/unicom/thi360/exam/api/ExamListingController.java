package vn.unicom.thi360.exam.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final ExamListingRequestReader reader;

    public ExamListingController(ExamListingService listings, ExamListingRequestReader reader) {
        this.listings = listings;
        this.reader = reader;
    }

    @GetMapping
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(operationId = "getExamListing", summary = "Thông tin bán: bản làm việc, bản đang bán, lỗi kiểm tra")
    public ExamListingResponse get(@PathVariable UUID examId) {
        return listings.get(examId);
    }

    /**
     * The body is read as JSON and checked by token type ({@link ExamListingRequestReader}), so a wrong type is a 422
     * {@code EXAM_LISTING_INVALID} with field errors instead of being coerced; the contract stays
     * {@link UpdateExamListingRequest}.
     */
    @PutMapping
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(operationId = "updateExamListing", summary = "Lưu thông tin bán của bài thi",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateExamListingRequest.class))))
    public ExamListingResponse update(@PathVariable UUID examId, @RequestBody JsonNode body) {
        return listings.update(examId, reader.read(body));
    }
}
