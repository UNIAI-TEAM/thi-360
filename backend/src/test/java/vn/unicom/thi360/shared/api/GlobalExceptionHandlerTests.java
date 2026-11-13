package vn.unicom.thi360.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@Import(GlobalExceptionHandlerTests.SampleErrorController.class)
class GlobalExceptionHandlerTests extends AbstractIntegrationTest {

    @RestController
    @RequestMapping("/api/v1/test-errors")
    static class SampleErrorController {

        record SampleInput(@NotBlank(message = "Tên đăng nhập là bắt buộc") String username) {
        }

        @PostMapping("/400-bean")
        void beanValidation(@Valid @RequestBody SampleInput input) {
        }

        @GetMapping("/400-custom")
        void customValidation() {
            throw new ValidationException(List.of(
                    new FieldError("username", "TAKEN", "Tên đăng nhập đã tồn tại")));
        }

        @GetMapping("/401")
        void unauthenticated() {
            throw new UnauthenticatedException();
        }

        @GetMapping("/403")
        void forbidden() {
            throw new ForbiddenException();
        }

        @GetMapping("/404")
        void notFound() {
            throw new NotFoundException("Không tìm thấy người dùng");
        }

        @GetMapping("/409")
        void conflict() {
            throw new ConflictException("USER_HAS_TRANSACTIONS", "Tài khoản đã có giao dịch");
        }

        @GetMapping("/412")
        void versionConflict() {
            throw new VersionConflictException();
        }

        @GetMapping("/429")
        void rateLimited() {
            throw new RateLimitedException("RATE_LIMITED");
        }

        @GetMapping("/400-param")
        void requiredIntParam(@RequestParam int count) {
        }

        @GetMapping("/403-unknown-code")
        void forbiddenUnknownCode() {
            throw new ForbiddenException("UNREGISTERED_FORBIDDEN_CODE", null);
        }

        @GetMapping("/409-unknown-code")
        void conflictUnknownCode() {
            throw new ConflictException("USER_LOCKED");
        }

        @GetMapping("/500")
        void internalError() {
            throw new IllegalStateException("Sensitive SQL detail that must not leak");
        }

        @GetMapping("/500-illegal-arg")
        void internalIllegalArgument() {
            throw new IllegalArgumentException("Internal serialization defect that must not be 400");
        }
    }

    @Test
    void returns400ValidationFailed_withFieldErrors_andTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/400-bean")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-400-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "req-400-a"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("username"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NOTBLANK"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Tên đăng nhập là bắt buộc"))
                .andExpect(jsonPath("$.traceId").value("req-400-a"));

        mockMvc.perform(get("/api/v1/test-errors/400-custom")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-400-b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("username"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("TAKEN"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Tên đăng nhập đã tồn tại"))
                .andExpect(jsonPath("$.traceId").value("req-400-b"));
    }

    @Test
    void returns401Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/401")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-401"));
    }

    @Test
    void returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/403")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-403"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-403"));
    }

    @Test
    void returns404NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/404")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy người dùng"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-404"));
    }

    @Test
    void returns409ConflictWithBusinessCode() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/409")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_TRANSACTIONS"))
                .andExpect(jsonPath("$.message").value("Tài khoản đã có giao dịch"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-409"));
    }

    @Test
    void returns412VersionConflict() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/412")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-412"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-412"));
    }

    @Test
    void returns429RateLimited() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/429")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-429"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-429"));
    }

    @Test
    void returns500InternalError_withoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/500")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Đã xảy ra lỗi hệ thống, vui lòng thử lại sau"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-500"));
    }

    @Test
    void returns400ValidationFailed_forMalformedBody_typeMismatch_andMissingParameter() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/400-bean")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-400-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed-json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "req-400-json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-400-json"));

        mockMvc.perform(get("/api/v1/test-errors/400-param?count=not-a-number")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-400-mismatch"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "req-400-mismatch"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-400-mismatch"));

        mockMvc.perform(get("/api/v1/test-errors/400-param")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-400-missing"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "req-400-missing"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-400-missing"));
    }

    @Test
    void returns500InternalError_whenApplicationThrowsIllegalArgumentException() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/500-illegal-arg")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-500-ia"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "req-500-ia"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Đã xảy ra lỗi hệ thống, vui lòng thử lại sau"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-500-ia"));
    }

    @Test
    void returns404NotFound_forUnmappedApiRoute() throws Exception {
        mockMvc.perform(get("/api/v1/unmapped-route-does-not-exist")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-404-unmapped"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "req-404-unmapped"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy dữ liệu yêu cầu"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-404-unmapped"));
    }

    @Test
    void usesLocalizedVietnameseFallbackMessage_whenDomainExceptionCodeIsUnregistered() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/409-unknown-code")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-409-unknown"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_LOCKED"))
                .andExpect(jsonPath("$.message").value("Xung đột dữ liệu nghiệp vụ"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-409-unknown"));

        mockMvc.perform(get("/api/v1/test-errors/403-unknown-code")
                        .with(asStudent())
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "req-403-unknown"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNREGISTERED_FORBIDDEN_CODE"))
                .andExpect(jsonPath("$.message").value("Không có quyền thực hiện thao tác này"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").value("req-403-unknown"));
    }
}
