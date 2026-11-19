package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.shared.testapi.TestApiPaths;

/**
 * {@code GET /api/v1/test/mail} (AC-18, AC-19): chỉ có ở profile {@code e2e}. Lớp ngoài chạy với
 * profile thường (không có {@code e2e}) nên chứng minh đường vắng mặt; {@link UnderE2e} bật {@code e2e}
 * và đọc thư thật. Mỗi lớp có ngữ cảnh Spring riêng.
 */
class MailTestApiIT extends AbstractIntegrationTest {

    private static final String MAIL = "/api/v1/test/mail";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MailQueue queue;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** AC-19: không bật {@code e2e} thì 404, không có handler, không có trong OpenAPI. */
    @Test
    void absentWithoutE2eProfile() throws Exception {
        String to = MailSamples.randomAddress();
        // Thư có thật: 404 dưới đây không thể là "chưa có thư".
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                queue.enqueue(MailKind.EMAIL_VERIFY, to, "vi", MailSamples.model(MailKind.EMAIL_VERIFY), null));

        mockMvc.perform(get(MAIL).param("to", to).param("kind", "EMAIL_VERIFY"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(MAIL).param("to", to).param("kind", "EMAIL_VERIFY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(MAIL)).andExpect(status().isNotFound());

        assertThat(context.getBeanNamesForType(MailTestController.class)).isEmpty();
        for (RequestMappingHandlerMapping handlerMapping :
                context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
                assertThat(info.getPathPatternsCondition().getPatternValues())
                        .as("mapping %s", info)
                        .noneMatch(TestApiPaths::matches);
            }
        }
        assertThat(openApiPaths(mockMvc, new ObjectMapper()).fieldNames())
                .toIterable()
                .noneMatch(path -> path.startsWith("/api/v1/test"));
    }

    private static JsonNode openApiPaths(MockMvc mockMvc, ObjectMapper mapper) throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode paths = mapper.readTree(body).path("paths");
        assertThat(paths.size()).isGreaterThan(0);
        return paths;
    }

    /** Profile {@code e2e} cộng {@code api,worker,test} của lớp nền, như {@code E2eClockApiIT}. */
    @Nested
    @ActiveProfiles("e2e")
    class UnderE2e {

        @Autowired
        private MockMvc e2eMockMvc;

        @Autowired
        private MailQueue e2eQueue;

        @Autowired
        private PlatformTransactionManager e2eTransactionManager;

        @Autowired
        private Clock clock;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private MailProperties mailProperties;

        private UUID enqueue(MailKind kind, String to, Map<String, Object> model) {
            return new TransactionTemplate(e2eTransactionManager)
                    .execute(status -> e2eQueue.enqueue(kind, to, "vi", model, null));
        }

        /** AC-18: thư mới nhất kèm mã OTP trong thân; không có thư thì 404. */
        @Test
        void returnsLatestMail() throws Exception {
            assertThat(clock).isInstanceOf(MutableClock.class);
            String to = MailSamples.randomAddress();

            // Chưa có thư nào: 404.
            e2eMockMvc.perform(get(MAIL).param("to", to).param("kind", "EMAIL_VERIFY"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));

            enqueue(MailKind.EMAIL_VERIFY, to, MailSamples.model(MailKind.EMAIL_VERIFY, "111111"));
            Thread.sleep(20);
            UUID newest = enqueue(MailKind.EMAIL_VERIFY, to, MailSamples.model(MailKind.EMAIL_VERIFY, "222222"));
            // Thư loại khác và thư của người khác không được lẫn vào.
            Thread.sleep(20);
            enqueue(MailKind.PASSWORD_RESET, to, MailSamples.model(MailKind.PASSWORD_RESET));
            enqueue(MailKind.EMAIL_VERIFY, MailSamples.randomAddress(), MailSamples.model(MailKind.EMAIL_VERIFY, "333333"));

            // Không đăng nhập; địa chỉ không phân biệt hoa thường.
            String body = e2eMockMvc.perform(get(MAIL).param("to", to.toUpperCase()).param("kind", "EMAIL_VERIFY"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            JsonNode mail = objectMapper.readTree(body);

            assertThat(mail.path("id").asText()).isEqualTo(newest.toString());
            assertThat(mail.path("kind").asText()).isEqualTo("EMAIL_VERIFY");
            assertThat(mail.path("to").asText()).isEqualTo(to);
            assertThat(mail.path("text").asText()).contains("222222").doesNotContain("111111");
            assertThat(mail.path("html").asText()).contains("222222").doesNotContain("111111");
            assertThat(mail.path("subject").asText()).isNotBlank();

            // Loại khác trả thư của loại đó.
            e2eMockMvc.perform(get(MAIL).param("to", to).param("kind", "PASSWORD_RESET"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("tok-abc123")));
            // Người nhận có thư nhưng không thuộc loại này: 404.
            e2eMockMvc.perform(get(MAIL).param("to", to).param("kind", "REFUND_DONE"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void badQueryAnswers400() throws Exception {
            e2eMockMvc.perform(get(MAIL).param("kind", "EMAIL_VERIFY"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            e2eMockMvc.perform(get(MAIL).param("to", MailSamples.randomAddress()))
                    .andExpect(status().isBadRequest());
            e2eMockMvc.perform(get(MAIL).param("to", MailSamples.randomAddress()).param("kind", "NOT_A_KIND"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("kind"));
        }

        @Test
        void mailEndpointIsHiddenFromOpenApi() throws Exception {
            assertThat(openApiPaths(e2eMockMvc, objectMapper).fieldNames())
                    .toIterable()
                    .noneMatch(path -> path.startsWith("/api/v1/test"));
        }

        /** Khởi động ở profile {@code e2e} qua kiểm tra chặt của {@link MailProperties}, với {@code tls=none}. */
        @Test
        void e2eProfileStartsWithPlainSmtp() {
            assertThat(mailProperties.tls()).isEqualTo(SmtpTls.NONE);
        }
    }
}
