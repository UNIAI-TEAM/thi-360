package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.commerce.entitlement.access.application.ExamLearnerGate;
import vn.unicom.thi360.commerce.entitlement.access.application.MyExamsService;

/**
 * Bài thi của tôi (TM 12): thẻ của chính tài khoản đang đăng nhập. Không có tham số id tài khoản, nên chỉ đọc được dữ liệu
 * của mình; không gắn {@code @RequirePermission} (như {@code /me}). Chỉ tài khoản học viên {@code ACTIVE} được gọi.
 */
@RestController
@RequestMapping(value = "/api/v1/me/exams", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "My exams", description = "Bài thi của tài khoản đang đăng nhập")
public class MyExamsController {

    private final ExamLearnerGate gate;
    private final MyExamsService service;

    public MyExamsController(ExamLearnerGate gate, MyExamsService service) {
        this.gate = gate;
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "listMyExams", summary = "Thẻ bài thi của tài khoản đang đăng nhập (TM 12)")
    public MyExamsResponse list() {
        return new MyExamsResponse(service.list(gate.requireLearner()));
    }
}
