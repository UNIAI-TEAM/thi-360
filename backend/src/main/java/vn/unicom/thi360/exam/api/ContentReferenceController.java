package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.exam.application.ContentReferenceService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Exam", description = "Dữ liệu tham chiếu và bài thi")
@RestController
@RequestMapping("/api/v1/admin")
public class ContentReferenceController {

    private final ContentReferenceService contentReferenceService;

    public ContentReferenceController(ContentReferenceService contentReferenceService) {
        this.contentReferenceService = contentReferenceService;
    }

    @GetMapping("/content-reference")
    @RequirePermission(anyOf = {Permissions.QUESTION_VIEW, Permissions.EXAM_VIEW})
    @Operation(
            summary = "Dữ liệu tham chiếu gộp cho màn Ngân hàng câu hỏi và Bài thi",
            description = "Mở cho người có QUESTION.VIEW hoặc EXAM.VIEW. Trả lựa chọn danh mục, "
                    + "dạng bài, tám dạng câu cố định và cây kiến thức; examKinds rỗng tới BE-4. "
                    + "Kết quả cache theo revision danh mục.")
    public ContentReferenceResponse getContentReference() {
        return contentReferenceService.getContentReference();
    }
}
