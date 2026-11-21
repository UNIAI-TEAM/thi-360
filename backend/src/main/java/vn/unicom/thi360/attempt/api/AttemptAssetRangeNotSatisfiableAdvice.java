package vn.unicom.thi360.attempt.api;

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
 * Suppresses writing the full {@link Resource} body when Spring MVC sets HTTP 416 Range Not
 * Satisfiable on {@link AttemptAssetController} responses (mirroring
 * {@code QuestionAssetRangeNotSatisfiableAdvice}).
 */
@RestControllerAdvice(assignableTypes = AttemptAssetController.class)
public class AttemptAssetRangeNotSatisfiableAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof Resource
                && response instanceof ServletServerHttpResponse servletResponse
                && servletResponse.getServletResponse().getStatus()
                        == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
            return null;
        }
        return body;
    }
}
