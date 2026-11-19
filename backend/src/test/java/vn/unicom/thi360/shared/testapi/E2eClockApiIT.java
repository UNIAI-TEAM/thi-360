package vn.unicom.thi360.shared.testapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.security.CtxCookieSigner;
import vn.unicom.thi360.shared.security.SessionStore;

/** Profile {@code e2e} cộng {@code api,worker,test} của lớp nền (realm, khóa ngữ cảnh, S3 bộ nhớ). */
@ActiveProfiles("e2e")
class E2eClockApiIT extends AbstractIntegrationTest {

    private static final String CLOCK = "/api/v1/test/clock";

    @Autowired
    private Clock clock;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private CtxCookieSigner ctxCookieSigner;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void resetClock() throws Exception {
        mockMvc.perform(post(CLOCK).contentType(MediaType.APPLICATION_JSON).content("{\"reset\":true}"))
                .andExpect(status().isOk());
    }

    private Instant serverNow() throws Exception {
        String body = mockMvc.perform(get(CLOCK))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return Instant.parse(objectMapper.readTree(body).path("serverNow").asText());
    }

    private Instant postClock(String json) throws Exception {
        String body = mockMvc.perform(post(CLOCK).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return Instant.parse(objectMapper.readTree(body).path("serverNow").asText());
    }

    @Test
    void advanceMovesServerNow() throws Exception {
        Instant before = Instant.now();

        Instant advanced = postClock("{\"advanceSeconds\":3600}");

        assertThat(Duration.between(before, advanced)).isBetween(Duration.ofSeconds(3600), Duration.ofSeconds(3610));
        Instant read = serverNow();
        assertThat(read).isAfterOrEqualTo(advanced);
        assertThat(Duration.between(advanced, read)).isLessThan(Duration.ofSeconds(10));
        // Cộng dồn qua nhiều lần tua.
        Instant again = postClock("{\"advanceSeconds\":60}");
        assertThat(Duration.between(read, again)).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(70));
    }

    @Test
    void setAndResetClock() throws Exception {
        Instant target = Instant.parse("2031-05-06T07:08:09Z");

        Instant set = postClock("{\"at\":\"2031-05-06T07:08:09Z\"}");
        assertThat(set).isBetween(target, target.plusSeconds(5));
        assertThat(serverNow()).isBetween(target, target.plusSeconds(10));

        Instant before = Instant.now();
        Instant reset = postClock("{\"reset\":true}");
        Instant after = Instant.now();
        assertThat(reset).isBetween(before, after.plusSeconds(1));
        assertThat(serverNow()).isBetween(before, Instant.now().plusSeconds(1));
    }

    @Test
    void invalidBodyAnswers400() throws Exception {
        for (String body : new String[] {
                "{}",
                "{\"reset\":false}",
                "{\"at\":\"2031-05-06T07:08:09Z\",\"advanceSeconds\":5}",
                "{\"at\":\"2031-05-06T07:08:09Z\",\"reset\":true}",
                "{\"advanceSeconds\":5,\"reset\":true}",
                "{\"at\":\"2031-05-06T07:08:09Z\",\"advanceSeconds\":5,\"reset\":true}",
                "{\"at\":\"not-a-time\"}",
                "{\"at\":\"2031-05-06 07:08:09\"}",
                "{\"advanceSeconds\":\"soon\"}",
                "{\"advanceSeconds\":-5}",
                "{\"unknown\":1}",
                "[]"}) {
            mockMvc.perform(post(CLOCK).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        // Thân rỗng hẳn.
        mockMvc.perform(post(CLOCK).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        // Mọi yêu cầu lỗi đều không đổi đồng hồ.
        assertThat(Duration.between(Instant.now(), serverNow()).abs()).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void injectedClockIsMutable() throws Exception {
        assertThat(clock).isInstanceOf(MutableClock.class);
        assertThat(ReflectionTestUtils.getField(sessionStore, "clock")).isSameAs(clock);
        assertThat(ReflectionTestUtils.getField(ctxCookieSigner, "clock")).isSameAs(clock);

        Instant first = postClock("{\"advanceSeconds\":10800}");

        // Bean Clock được tiêm ở mọi nơi thấy giờ đã tua (/api/v1/ping đọc Clock).
        JsonNode ping = objectMapper.readTree(mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        Instant pingTime = Instant.parse(ping.path("time").asText());
        assertThat(Duration.between(first, pingTime).abs()).isLessThan(Duration.ofSeconds(10));
        assertThat(Duration.between(Instant.now(), pingTime)).isGreaterThan(Duration.ofSeconds(10790));

        // Giờ vẫn chạy sau khi tua.
        Thread.sleep(120);
        assertThat(serverNow()).isAfter(first);
    }

    @Test
    void testApiIsHiddenFromOpenApi() throws Exception {
        JsonNode paths = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("paths");

        assertThat(paths.size()).isGreaterThan(0);
        paths.fieldNames().forEachRemaining(path -> assertThat(path).doesNotStartWith("/api/v1/test"));
    }

    @Test
    void clockApiNeedsNoLoginUnderE2e() throws Exception {
        // Không cookie, không token: gọi thẳng được (Playwright gọi API này như vậy).
        mockMvc.perform(get(CLOCK)).andExpect(status().isOk()).andExpect(jsonPath("$.serverNow").isNotEmpty());
        mockMvc.perform(post(CLOCK).contentType(MediaType.APPLICATION_JSON).content("{\"advanceSeconds\":1}"))
                .andExpect(status().isOk());
    }
}
