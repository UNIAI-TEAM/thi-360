package vn.unicom.thi360.questionbank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.QuestionAssetUploadRateLimiter;
import vn.unicom.thi360.questionbank.domain.QuestionAssetProbeTests;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;

/**
 * IT của giới hạn 60 tệp / 10 phút / người cho {@code POST /question-assets} (spec 10:
 * tệp thứ 61 của cùng người → 429; người khác không bị ảnh hưởng). Bucket nằm trên Redis
 * (Testcontainers) nên mỗi test dọn key trước để không phụ thuộc thứ tự chạy.
 */
class QuestionAssetRateLimitIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/question-assets";
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    @Autowired
    private StringRedisTemplate redis;

    private byte[] png;

    @BeforeEach
    void resetBuckets() throws Exception {
        png = QuestionAssetProbeTests.imageBytes("PNG", 2, 2);
        redis.delete(QuestionAssetUploadRateLimiter.KEY_PREFIX + USER_A);
        redis.delete(QuestionAssetUploadRateLimiter.KEY_PREFIX + USER_B);
    }

    @Test
    void sixtyFirstUpload429_otherUserUnaffected() throws Exception {
        for (int i = 0; i < 60; i++) {
            upload(USER_A).andExpect(status().isCreated());
        }
        upload(USER_A)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        // Người khác cùng lúc vẫn tải được (bucket theo user).
        upload(USER_B).andExpect(status().isCreated());
    }

    @Test
    void getIsNotRateLimited() throws Exception {
        mockMvc.perform(multipart(HttpMethod.GET, BASE)
                        .with(asUser(USER_A, "rl-it", Permissions.QUESTION_VIEW)))
                .andExpect(status().isMethodNotAllowed()); // 405, không phải 429
    }

    private org.springframework.test.web.servlet.ResultActions upload(UUID userId) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.POST, BASE)
                .file(new MockMultipartFile("file", "a.png", "image/png", png))
                .param("kind", "IMAGE")
                .with(asUser(userId, "rl-it", Permissions.QUESTION_MODIFY)));
    }
}
