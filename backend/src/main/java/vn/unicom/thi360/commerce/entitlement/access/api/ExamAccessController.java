package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.entitlement.access.application.ExamAccessService;
import vn.unicom.thi360.commerce.entitlement.access.application.ExamLearnerGate;

/**
 * Quyền dùng của chính tài khoản đang đăng nhập (TM 10.2). Không có tham số id tài khoản, nên chỉ đọc được dữ liệu của
 * mình; không gắn {@code @RequirePermission} (như {@code /me}). Chỉ tài khoản học viên {@code ACTIVE} được gọi.
 */
@RestController
@RequestMapping(value = "/api/v1/me/exam-access", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Exam access", description = "Quyền dùng bài thi của tài khoản đang đăng nhập")
public class ExamAccessController {

    private final ExamLearnerGate gate;
    private final ExamAccessService service;

    public ExamAccessController(ExamLearnerGate gate, ExamAccessService service) {
        this.gate = gate;
        this.service = service;
    }

    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "queryMyExamAccess",
            summary = "Hỏi trạng thái quyền dùng theo mã sản phẩm (tối đa 48 mã)")
    public ExamAccessQueryResponse query(@Valid @RequestBody ExamAccessQueryRequest request) {
        return new ExamAccessQueryResponse(service.query(gate.requireLearner(), request.codes()));
    }
}
