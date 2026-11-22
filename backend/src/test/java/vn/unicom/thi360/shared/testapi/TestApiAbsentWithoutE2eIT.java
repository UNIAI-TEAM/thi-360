package vn.unicom.thi360.shared.testapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.security.AuthCookieService;

/**
 * Profile thường ({@code api,worker,test}, không có {@code e2e}): {@code /api/v1/test/**} không có
 * handler và nhận 404 bất kể thông tin xác thực kèm theo (không bao giờ 401 hay 403). ML và CM-3a
 * thêm endpoint theo cùng quy tắc, nên lớp này phải vẫn xanh sau khi hai task đó merge.
 * {@link TestApiAbsentUnderProdIT} chạy lại toàn bộ ca với profile {@code prod}.
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

    /** R1-06: Bearer bị từ chối hay cookie phiên cũng không đổi 404 thành 401/403. */
    @Test
    void anyCredentialsStillAnswer404UnderTestPrefix() throws Exception {
        String[] paths = {"/api/v1/test/clock", "/api/v1/test/mail", "/api/v1/test/anything", "/api/v1/test"};
        for (String path : paths) {
            // Bearer hỏng, Bearer sai khóa ký, Bearer rỗng giá trị.
            for (String authorization : new String[] {
                    "Bearer not-a-jwt", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.c2ln", "Bearer ", "Basic Zm9vOmJhcg=="}) {
                expect404(get(path).header(HttpHeaders.AUTHORIZATION, authorization));
                expect404(post(path).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"advanceSeconds\":3600}"));
            }
            // Cookie phiên cũ; POST có cookie mà không có token XSRF không còn là 403.
            Cookie staleSession = new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "stale.session.token");
            expect404(get(path).cookie(staleSession));
            expect404(post(path).cookie(staleSession).contentType(MediaType.APPLICATION_JSON).content("{}"));
            // Người dùng đã đăng nhập hợp lệ cũng chỉ thấy 404.
            expect404(get(path).with(asUser()));
            expect404(post(path).with(asUser()).contentType(MediaType.APPLICATION_JSON).content("{}"));
        }
    }

    /** Chuỗi bộ lọc riêng chỉ khớp tiền tố test: đường kề vẫn yêu cầu xác thực như thường. */
    @Test
    void adjacentRoutesKeepTheirAuthentication() throws Exception {
        for (String path : new String[] {"/api/v1/testing", "/api/v1/test-clock", "/api/v1/tests/clock"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void expect404(MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request).andExpect(status().isNotFound());
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
