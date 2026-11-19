package vn.unicom.thi360.questionbank.api;

import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Khi range không thỏa, Spring MVC đã đặt status {@code 416} và
 * {@code Content-Range: bytes *&#47;total} nhưng vẫn giữ nguyên {@link Resource} trong
 * body nên converter ghi lại toàn bộ tệp kèm {@code Content-Length} của cả tệp.
 * Advice này chỉ chạy cho {@link AdminQuestionAssetController}: thấy status đã là 416
 * thì trả body {@code null} để bỏ qua bước ghi — phản hồi 416 không tải tài nguyên.
 */
@RestControllerAdvice(assignableTypes = AdminQuestionAssetController.class)
public class QuestionAssetRangeNotSatisfiableAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Resource && response instanceof ServletServerHttpResponse servletResponse
                && servletResponse.getServletResponse().getStatus()
                        == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
            return null;
        }
        return body;
    }
}
