package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.identity.PasswordResetTestSupport.Account;
import vn.unicom.thi360.identity.application.AvatarService;
import vn.unicom.thi360.shared.api.ExternalServiceException;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/** Ảnh đại diện với MinIO thật (TM 5.5, E20): AC-27 (phần ảnh), AC-28 đến AC-33 của plan ID-2. */
class AvatarMinioIT extends AbstractAvatarMinioIT {

    private static final int FIVE_MB = 5 * 1024 * 1024;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AvatarService avatarService;
    @MockitoSpyBean(name = "avatarObjectStorage")
    private ObjectStorage avatarStorage;

    private String upload(Account account, byte[] content, String fileName, String contentType) throws Exception {
        MvcResult result = mockMvc.perform(putAvatar(content, fileName, contentType).with(sessionOf(account)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("avatarUrl").asText()).isEqualTo("/api/v1/avatars/" + body.get("avatarAssetId").asText());
        return body.get("avatarAssetId").asText();
    }

    private MockHttpServletResponse uploadExpecting(Account account, byte[] content, String fileName, int status)
            throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        putAvatar(content, fileName, "image/jpeg").with(sessionOf(account)))
                .andReturn()
                .getResponse();
        assertThat(response.getStatus()).as(fileName).isEqualTo(status);
        return response;
    }

    @Test
    void requiresSessionAndUnknownAsset404() throws Exception {
        Account owner = support.eligible("avatar.auth");
        String assetId = upload(owner, AvatarTestImages.png(64, 64), "a.png", "image/png");

        // Không có phiên: 401 ở cả ba API.
        mockMvc.perform(putAvatar(AvatarTestImages.png(8, 8), "a.png", "image/png")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/me/avatar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/avatars/{id}", assetId)).andExpect(status().isUnauthorized());

        // Có phiên: assetId không tồn tại nhận 404; ảnh có thật thì đọc được.
        Account other = support.eligible("avatar.other");
        mockMvc.perform(get("/api/v1/avatars/{id}", UUID.randomUUID()).with(sessionOf(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/avatars/{id}", assetId).with(sessionOf(other))).andExpect(status().isOk());

        // Tài khoản nền tảng (realm master) không có ảnh đại diện: PUT và DELETE nhận 404.
        mockMvc.perform(putAvatar(AvatarTestImages.png(8, 8), "a.png", "image/png").with(asPlatformAdmin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/me/avatar").with(asPlatformAdmin())).andExpect(status().isNotFound());
    }

    @Test
    void storesSquarePngInAvatarBucket() throws Exception {
        Account account = support.eligible("avatar.store");
        for (var sample : List.of(
                new Sample(AvatarTestImages.jpeg(640, 360), "photo.jpg", "image/jpeg"),
                new Sample(AvatarTestImages.png(300, 500), "photo.png", "image/png"),
                new Sample(AvatarTestImages.webp(), "photo.webp", "image/webp"))) {
            String assetId = upload(account, sample.content(), sample.fileName(), sample.contentType());

            MockHttpServletResponse served = mockMvc.perform(get("/api/v1/avatars/{id}", assetId).with(sessionOf(account)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse();
            assertThat(served.getContentType()).isEqualTo("image/png");
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(served.getContentAsByteArray()));
            assertThat(image.getWidth()).isEqualTo(256);
            assertThat(image.getHeight()).isEqualTo(256);
            assertThat(objectExists(AVATAR_BUCKET, avatarKey(assetId))).as(sample.fileName()).isTrue();
            assertThat(objectKeys(PRIVATE_BUCKET)).as("avatars never go to the shared private bucket")
                    .doesNotContain(avatarKey(assetId));
        }
    }

    @Test
    void sizeBoundary() throws Exception {
        Account account = support.eligible("avatar.size");
        byte[] png = AvatarTestImages.png(64, 64);

        upload(account, AvatarTestImages.padded(png, FIVE_MB), "exact.png", "image/png");

        MockHttpServletResponse tooLarge = uploadExpecting(account, AvatarTestImages.padded(png, FIVE_MB + 1), "one-more.png", 413);
        assertThat(tooLarge.getContentAsString()).contains("AVATAR_TOO_LARGE");
        uploadExpecting(account, AvatarTestImages.padded(png, 6 * 1024 * 1024), "six-mb.png", 413);
    }

    @Test
    void fakeExtensionRefused() throws Exception {
        Account account = support.eligible("avatar.fake");

        MockHttpServletResponse fake = uploadExpecting(account, AvatarTestImages.text(), "avatar.jpg", 422);

        assertThat(fake.getContentAsString()).contains("AVATAR_INVALID_IMAGE");
        // Đuôi sai nhưng nội dung là ảnh thật: nhận theo chữ ký byte.
        upload(account, AvatarTestImages.png(32, 32), "really-a-png.txt", "text/plain");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identity.user_avatar WHERE user_id = ?", Integer.class, account.id())).isEqualTo(1);
    }

    @Test
    void dimensionBoundary() throws Exception {
        Account account = support.eligible("avatar.dims");

        upload(account, AvatarTestImages.png(4096, 4096), "limit.png", "image/png");

        for (byte[] over : List.of(
                AvatarTestImages.pngHeaderOnly(4097, 4096),
                AvatarTestImages.pngHeaderOnly(4096, 4097),
                AvatarTestImages.pngHeaderOnly(8000, 8000))) {
            MockHttpServletResponse response = uploadExpecting(account, over, "huge.png", 422);
            assertThat(response.getContentAsString()).contains("AVATAR_DIMENSIONS_EXCEEDED");
        }
    }

    @Test
    void replaceAndDeleteRemoveObjects() throws Exception {
        Account account = support.eligible("avatar.replace");
        String first = upload(account, AvatarTestImages.png(64, 64), "one.png", "image/png");
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(first))).isTrue();

        String second = upload(account, AvatarTestImages.jpeg(80, 80), "two.jpg", "image/jpeg");

        assertThat(second).isNotEqualTo(first);
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(first))).as("replaced object is deleted").isFalse();
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(second))).isTrue();
        mockMvc.perform(get("/api/v1/avatars/{id}", first).with(sessionOf(account))).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/me/avatar").with(sessionOf(account))).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identity.user_avatar WHERE user_id = ?", Integer.class, account.id())).isZero();
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(second))).as("deleted object is gone").isFalse();
        mockMvc.perform(get("/api/v1/avatars/{id}", second).with(sessionOf(account))).andExpect(status().isNotFound());
        // Xóa lần nữa khi không còn ảnh: không làm gì.
        mockMvc.perform(delete("/api/v1/me/avatar").with(sessionOf(account))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/me/profile").with(sessionOf(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void concurrentReplaceLeavesSingleObject() throws Exception {
        Account account = support.eligible("avatar.race");
        List<String> keysBefore = objectKeys(AVATAR_BUCKET);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> assetIds = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int size = 40 + i * 10;
                futures.add(pool.submit(() -> {
                    start.await();
                    assetIds.add(upload(account, AvatarTestImages.png(size, size), "race.png", "image/png"));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(assetIds).hasSize(2);
        String current = jdbc.queryForObject(
                "SELECT asset_id::text FROM identity.user_avatar WHERE user_id = ?", String.class, account.id());
        assertThat(assetIds).contains(current);
        // Toàn bộ thay đổi khóa của bucket (không lọc theo phản hồi): đúng một đối tượng mới và là của dòng hiện tại.
        List<String> delta = new ArrayList<>(objectKeys(AVATAR_BUCKET));
        delta.removeAll(keysBefore);
        assertThat(delta).as("only the object of the current row remains, no orphan from the losing write")
                .containsExactly(avatarKey(current));
        assertThat(objectKeys(AVATAR_BUCKET)).containsAll(keysBefore);
    }

    @Test
    void failedObjectDeleteIsRetriedWithoutTouchingTheCurrentObject() throws Exception {
        Account account = support.eligible("avatar.cleanup");
        String first = upload(account, AvatarTestImages.png(64, 64), "one.png", "image/png");
        doThrow(new ExternalServiceException("INTERNAL_ERROR", null, new RuntimeException("minio down")))
                .when(avatarStorage).delete(anyString());

        // Thay ảnh: DB đã đổi, xóa đối tượng cũ lỗi nhưng yêu cầu vẫn thành công và ý định xóa được giữ lại.
        String second = upload(account, AvatarTestImages.png(70, 70), "two.png", "image/png");

        assertThat(objectExists(AVATAR_BUCKET, avatarKey(first))).as("old object could not be deleted yet").isTrue();
        assertThat(pendingCleanup(avatarKey(first))).isEqualTo(1);
        assertThat(avatarService.sweepPendingDeletes()).as("delete still failing").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT attempts FROM identity.avatar_object_cleanup WHERE object_key = ?",
                Integer.class, avatarKey(first))).isEqualTo(1);

        // Xóa ảnh: cũng lỗi xóa đối tượng, cũng được ghi lại.
        mockMvc.perform(delete("/api/v1/me/avatar").with(sessionOf(account))).andExpect(status().isNoContent());
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(second))).isTrue();
        assertThat(pendingCleanup(avatarKey(second))).isEqualTo(1);

        // MinIO hồi phục: job dọn cả hai.
        org.mockito.Mockito.doCallRealMethod().when(avatarStorage).delete(anyString());
        assertThat(avatarService.sweepPendingDeletes()).isEqualTo(2);
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(first))).isFalse();
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(second))).isFalse();
        assertThat(pendingCleanup(avatarKey(first)) + pendingCleanup(avatarKey(second))).isZero();

        // Không bao giờ xóa đối tượng đang được tham chiếu, kể cả khi có dòng chờ xóa trỏ vào nó.
        String third = upload(account, AvatarTestImages.png(50, 50), "three.png", "image/png");
        jdbc.update("INSERT INTO identity.avatar_object_cleanup (object_key) VALUES (?)", avatarKey(third));
        assertThat(avatarService.sweepPendingDeletes()).isEqualTo(1);
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(third))).as("current object is kept").isTrue();
        mockMvc.perform(get("/api/v1/avatars/{id}", third).with(sessionOf(account))).andExpect(status().isOk());
        assertThat(pendingCleanup(avatarKey(third))).isZero();
    }

    private int pendingCleanup(String objectKey) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM identity.avatar_object_cleanup WHERE object_key = ?", Integer.class, objectKey);
        return count == null ? 0 : count;
    }

    @Test
    void storageFailureKeepsCurrentAvatar() throws Exception {
        Account account = support.eligible("avatar.fail");
        String first = upload(account, AvatarTestImages.png(64, 64), "one.png", "image/png");
        List<String> keysBefore = objectKeys(AVATAR_BUCKET);
        doThrow(new ExternalServiceException("INTERNAL_ERROR", null, new RuntimeException("minio down")))
                .when(avatarStorage).put(anyString(), any(), anyString());

        MockHttpServletResponse response = mockMvc.perform(
                        putAvatar(AvatarTestImages.png(70, 70), "two.png", "image/png").with(sessionOf(account)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(jdbc.queryForObject(
                "SELECT asset_id::text FROM identity.user_avatar WHERE user_id = ?", String.class, account.id()))
                .isEqualTo(first);
        assertThat(objectExists(AVATAR_BUCKET, avatarKey(first))).isTrue();
        mockMvc.perform(get("/api/v1/avatars/{id}", first).with(sessionOf(account))).andExpect(status().isOk());
        assertThat(objectKeys(AVATAR_BUCKET)).as("no object was written by the failed upload")
                .isEqualTo(keysBefore);
    }

    private record Sample(byte[] content, String fileName, String contentType) {
    }
}
