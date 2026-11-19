package vn.unicom.thi360.shared.testapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.unicom.thi360.AbstractIntegrationTest;

/**
 * Profile thường ({@code api,worker,test}, không có {@code e2e}): {@code /api/v1/test/**} không có
 * handler và bảo mật không trả 401. ML và CM-3a thêm endpoint theo cùng quy tắc, nên lớp này phải
 * vẫn xanh sau khi hai task đó merge.
 */
class TestApiAbsentWithoutE2eIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void clockEndpointAnswers404() throws Exception {
        mockMvc.perform(post("/api/v1/test/clock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"advanceSeconds\":3600}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/test/clock")).andExpect(status().isNotFound());
        // Ẩn danh không bao giờ nhận 401 ở tiền tố này, kể cả đường chưa có ai đăng ký.
        mockMvc.perform(get("/api/v1/test/mail")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/test/anything")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noHandlerUnderTestPrefix() {
        Map<String, RequestMappingHandlerMapping> mappings = context.getBeansOfType(RequestMappingHandlerMapping.class);
        assertThat(mappings).isNotEmpty();
        for (RequestMappingHandlerMapping handlerMapping : mappings.values()) {
            for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
                assertThat(info.getPathPatternsCondition().getPatternValues())
                        .as("mapping %s", info)
                        .noneMatch(TestApiPaths::matches);
            }
        }
        assertThat(context.getBeanNamesForType(TestClockController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MutableClock.class)).isEmpty();
        assertThat(context.getBean(Clock.class)).isNotInstanceOf(MutableClock.class);
    }
}
