package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjectionService;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * AC-9, AC-10, AC-11: Hiển thị, lọc, sắp xếp, chi tiết công khai bộ đề trên storefront; trạng thái
 * {@code UNAVAILABLE} -> 404 và {@code DISCONTINUED} -> 410; tái tạo storefront qua {@code /rebuild}.
 */
class BundleStorefrontIT extends AbstractBundleIT {

    @Autowired
    private StorefrontProjectionService examProjectionService;

    @Test
    void listsFiltersSortsAndServesPublicBundleDetailWithoutLeaks() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");

        BundleExamBuilder singleFull = fullExam("HSA", "TOAN").price(299_000L);
        BundleExamBuilder partMath = partExam("HSA", "TOAN").named("Phần Toán").metrics(30, 45, 50);
        BundleExamBuilder subjectLit = subjectExam("HSA", "VAN").named("Môn Ngữ văn").metrics(40, 60, 100);
        serve(singleFull);
        serve(partMath);
        serve(subjectLit);
        examProjectionService.project(singleFull.examId);

        String pubCode = "SF-PUB-" + shortToken();
        Map<String, Object> pubReq = createPayload(pubCode, "Bộ đề ĐGNL Toàn diện", hsaId,
                List.of(partMath.examId, subjectLit.examId));
        pubReq.put("listPriceVnd", 160_000L);
        pubReq.put("attemptsPerExam", 4);
        JsonNode published = createBundle(pubReq, auth);
        UUID pubId = UUID.fromString(published.path("id").asText());
        performPublish(pubId, published.path("revision").asLong(), auth).andExpect(status().isOk());

        // Bộ đề nháp không bao giờ xuất hiện trên storefront công khai
        String draftCode = "SF-DFT-" + shortToken();
        createBundle(createPayload(draftCode, "Bộ đề Nháp", hsaId, List.of(partMath.examId, subjectLit.examId)), auth);

        // Filters chứa format = BUNDLE và môn học của thành phần bộ đề
        mockMvc.perform(get(STOREFRONT_BASE + "/filters")
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formats[*].code", hasItem("BUNDLE")))
                .andExpect(jsonPath("$.subjects[*].code", hasItem("VAN")));

        // Lọc format=BUNDLE chỉ trả về bộ đề đang bán
        String listJson = mockMvc.perform(get(STOREFRONT_BASE + "/products")
                        .param("format", "BUNDLE")
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code", hasItem(pubCode)))
                .andReturn().getResponse().getContentAsString();
        assertThat(listJson).doesNotContain(draftCode);
        assertThat(listJson).doesNotContain(singleFull.code);

        // Lọc theo subject=VAN tìm thấy bộ đề nhờ subject_item_ids
        mockMvc.perform(get(STOREFRONT_BASE + "/products")
                        .param("kind", "HSA")
                        .param("subject", "VAN")
                        .param("q", pubCode)
                        .param("sort", "PRICE_ASC")
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productType").value("BUNDLE"))
                .andExpect(jsonPath("$.items[0].format").value("BUNDLE"))
                .andExpect(jsonPath("$.items[0].questionCount.min").value(70))
                .andExpect(jsonPath("$.items[0].durationMinutes.min").value(105));

        // Chi tiết sản phẩm bộ đề
        String detailJson = mockMvc.perform(get(STOREFRONT_BASE + "/products/" + pubCode)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productType").value("BUNDLE"))
                .andExpect(jsonPath("$.format").value("BUNDLE"))
                .andExpect(jsonPath("$.attemptsGranted").value(4))
                .andExpect(jsonPath("$.listPrice").value(160_000))
                .andExpect(jsonPath("$.resultView").value("FULL_SOLUTION"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value(partMath.code))
                .andExpect(jsonPath("$.items[1].code").value(subjectLit.code))
                .andReturn().getResponse().getContentAsString();
        assertThat(detailJson).contains(partMath.code, subjectLit.code);
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(detailJson);
    }

    @Test
    void returns404WhenBundleIsUnavailableAnd410WhenDiscontinued() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder e1 = partExam("HSA", "TOAN");
        BundleExamBuilder e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);

        String code = "SF-STATE-" + shortToken();
        JsonNode created = createBundle(createPayload(code, "Bộ đề Trạng thái", hsaId,
                List.of(e1.examId, e2.examId)), auth);
        UUID bundleId = UUID.fromString(created.path("id").asText());
        String pubJson = performPublish(bundleId, created.path("revision").asLong(), auth)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long revAfterPub = mapper.readTree(pubJson).path("revision").asLong();

        // Thành phần ngừng bán -> UNAVAILABLE -> 404 PRODUCT_NOT_FOUND trên storefront detail
        serve(e2.discontinued());
        emitExamEvent(ExamEventTopics.DISCONTINUED, e2.examId, 1);

        assertThat(jdbc.queryForObject(
                "SELECT sale_state FROM commerce.storefront_product WHERE bundle_id = ?", String.class, bundleId))
                .isEqualTo("UNAVAILABLE");
        mockMvc.perform(get(STOREFRONT_BASE + "/products")
                        .param("q", code)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + code)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        // Ngừng bán chính bộ đề -> DISCONTINUED -> 410 PRODUCT_DISCONTINUED
        performDiscontinue(bundleId, revAfterPub, auth).andExpect(status().isOk());
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + code)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"));
    }

    @Test
    void rebuildRestoresPublishedAndDiscontinuedBundlesIdempotentlyWithSavepointsAndSkipsDrafts() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder e1 = partExam("HSA", "TOAN");
        BundleExamBuilder e2 = subjectExam("HSA", "ANH");
        BundleExamBuilder eFaulty = subjectExam("HSA", "HOA");
        serve(e1);
        serve(e2);
        serve(eFaulty);

        String pubCode = "RB-PUB-" + shortToken();
        JsonNode pub = createBundle(createPayload(pubCode, "Rebuild Pub", hsaId, List.of(e1.examId, e2.examId)), auth);
        UUID pubId = UUID.fromString(pub.path("id").asText());
        performPublish(pubId, pub.path("revision").asLong(), auth).andExpect(status().isOk());

        String discCode = "RB-DISC-" + shortToken();
        JsonNode disc = createBundle(createPayload(discCode, "Rebuild Disc", hsaId, List.of(e1.examId, e2.examId)), auth);
        UUID discId = UUID.fromString(disc.path("id").asText());
        String discPubJson = performPublish(discId, disc.path("revision").asLong(), auth)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        performDiscontinue(discId, mapper.readTree(discPubJson).path("revision").asLong(), auth)
                .andExpect(status().isOk());

        String draftCode = "RB-DFT-" + shortToken();
        JsonNode draft = createBundle(createPayload(draftCode, "Rebuild Draft", hsaId, List.of(e1.examId, e2.examId)), auth);
        UUID draftId = UUID.fromString(draft.path("id").asText());

        // Xóa trực tiếp storefront rows rồi chạy rebuild
        jdbc.update("DELETE FROM commerce.storefront_product WHERE bundle_id IN (?, ?)", pubId, discId);

        mockMvc.perform(post("/api/v1/admin/storefront/rebuild").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.written").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.failed").value(0));

        assertThat(jdbc.queryForObject(
                "SELECT sale_state FROM commerce.storefront_product WHERE bundle_id = ?", String.class, pubId))
                .isEqualTo("ON_SALE");
        assertThat(jdbc.queryForObject(
                "SELECT sale_state FROM commerce.storefront_product WHERE bundle_id = ?", String.class, discId))
                .isEqualTo("DISCONTINUED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.storefront_product WHERE bundle_id = ?", Long.class, draftId))
                .isZero();

        Timestamp updatedBefore = jdbc.queryForObject(
                "SELECT updated_at FROM commerce.storefront_product WHERE bundle_id = ?", Timestamp.class, pubId);

        // Chạy lại rebuild không làm thay đổi row
        mockMvc.perform(post("/api/v1/admin/storefront/rebuild").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.written").value(0));
        Timestamp updatedAfter = jdbc.queryForObject(
                "SELECT updated_at FROM commerce.storefront_product WHERE bundle_id = ?", Timestamp.class, pubId);
        assertThat(updatedAfter).isEqualTo(updatedBefore);

        // Savepoint isolation: lỗi ở bộ đề khác không làm rollback bộ đề khỏe mạnh
        String failBundleCode = "RB-FAIL-" + shortToken();
        JsonNode failB = createBundle(createPayload(failBundleCode, "Fail Bundle", hsaId,
                List.of(e1.examId, eFaulty.examId)), auth);
        UUID failBId = UUID.fromString(failB.path("id").asText());
        performPublish(failBId, failB.path("revision").asLong(), auth).andExpect(status().isOk());

        jdbc.update("DELETE FROM commerce.storefront_product WHERE bundle_id = ?", pubId);
        port.failFor(eFaulty.examId);
        try {
            mockMvc.perform(post("/api/v1/admin/storefront/rebuild").with(auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.failed").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.written").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM commerce.storefront_product WHERE bundle_id = ?", Long.class, pubId))
                    .isEqualTo(1L);
        } finally {
            port.recover(eFaulty.examId);
        }
    }
}
