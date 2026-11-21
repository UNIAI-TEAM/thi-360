package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.commerce.bundle.application.BundleStorefrontProjector;
import vn.unicom.thi360.commerce.bundle.domain.Bundle;
import vn.unicom.thi360.commerce.bundle.domain.BundleVersion;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleRepository;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleVersionRepository;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjectionService;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontRow;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/**
 * AC-9, AC-10, AC-11: Hiển thị, lọc, sắp xếp, chi tiết công khai bộ đề trên storefront; trạng thái
 * {@code UNAVAILABLE} -> 404 và {@code DISCONTINUED} -> 410; tái tạo storefront qua {@code /rebuild}.
 */
class BundleStorefrontIT extends AbstractBundleIT {

    @Autowired
    private StorefrontProjectionService examProjectionService;

    @Autowired
    private StorefrontProductRepository storefrontProductRepository;

    @Autowired
    private BundleStorefrontProjector bundleStorefrontProjector;

    @Autowired
    private BundleRepository bundleRepository;

    @MockitoSpyBean
    private BundleVersionRepository bundleVersionRepository;

    @BeforeEach
    void resetBundleVersionSpyBefore() {
        reset(bundleVersionRepository);
    }

    @AfterEach
    void resetBundleVersionSpyAfter() {
        reset(bundleVersionRepository);
    }

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

        // Chi tiết sản phẩm bộ đề (kiểm đầy đủ field BundleDetailItem — R1-27)
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
                .andExpect(jsonPath("$.items[0].name").value("Phần Toán"))
                .andExpect(jsonPath("$.items[0].subjectName.nameVi").value("Toán học"))
                .andExpect(jsonPath("$.items[0].questionCount.min").value(30))
                .andExpect(jsonPath("$.items[0].questionCount.max").value(30))
                .andExpect(jsonPath("$.items[0].durationMinutes.min").value(45))
                .andExpect(jsonPath("$.items[0].durationMinutes.max").value(45))
                .andExpect(jsonPath("$.items[0].maxPoints.min").value(50))
                .andExpect(jsonPath("$.items[0].maxPoints.max").value(50))
                .andExpect(jsonPath("$.items[0].attemptsGranted").value(4))
                .andExpect(jsonPath("$.items[0].resultView").value("FULL_SOLUTION"))
                .andExpect(jsonPath("$.items[0].benefits.aiExplanation").value(true))
                .andExpect(jsonPath("$.items[0].benefits.strengthWeakness").value(true))
                .andExpect(jsonPath("$.items[0].benefits.practice").value(false))
                .andExpect(jsonPath("$.items[0].benefits.note").value("Hỗ trợ giải thích AI"))
                .andExpect(jsonPath("$.items[1].code").value(subjectLit.code))
                .andExpect(jsonPath("$.items[1].name").value("Môn Ngữ văn"))
                .andExpect(jsonPath("$.items[1].subjectName.nameVi").value("Ngữ văn"))
                .andExpect(jsonPath("$.items[1].questionCount.min").value(40))
                .andExpect(jsonPath("$.items[1].questionCount.max").value(40))
                .andExpect(jsonPath("$.items[1].durationMinutes.min").value(60))
                .andExpect(jsonPath("$.items[1].durationMinutes.max").value(60))
                .andExpect(jsonPath("$.items[1].maxPoints.min").value(100))
                .andExpect(jsonPath("$.items[1].maxPoints.max").value(100))
                .andExpect(jsonPath("$.items[1].attemptsGranted").value(4))
                .andExpect(jsonPath("$.items[1].resultView").value("FULL_SOLUTION"))
                .andExpect(jsonPath("$.items[1].benefits.aiExplanation").value(true))
                .andExpect(jsonPath("$.items[1].benefits.strengthWeakness").value(true))
                .andExpect(jsonPath("$.items[1].benefits.practice").value(false))
                .andExpect(jsonPath("$.items[1].benefits.note").value("Hỗ trợ giải thích AI"))
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
        // Project e1 lên storefront trước để rebuild sau discontinue chỉ kiểm chứng idempotency của bundleId (R1-16/17).
        emitExamEvent(ExamEventTopics.PUBLISHED, e1.examId, 1);

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
        Timestamp updatedAfterDiscontinue = jdbc.queryForObject(
                "SELECT updated_at FROM commerce.storefront_product WHERE bundle_id = ?",
                Timestamp.class, bundleId);
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + code)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"));

        // R1-16/17: Chạy rebuild ngay sau discontinue (không xóa row) -> projection_hash đã khớp nên written == 0.
        mockMvc.perform(post("/api/v1/admin/storefront/rebuild").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.written").value(0))
                .andExpect(jsonPath("$.failed").value(0));
        Timestamp updatedAfterRebuild = jdbc.queryForObject(
                "SELECT updated_at FROM commerce.storefront_product WHERE bundle_id = ?",
                Timestamp.class, bundleId);
        assertThat(updatedAfterRebuild).isEqualTo(updatedAfterDiscontinue);

        // R1-14/15: Ghi đè stale SaleState.UNAVAILABLE ở cùng version_no không được phép đè DISCONTINUED.
        Bundle discBundle = bundleRepository.findById(bundleId).orElseThrow();
        BundleVersion discVersion = bundleVersionRepository
                .findByBundleIdAndVersionNo(bundleId, discBundle.getServingVersion()).orElseThrow();
        StorefrontRow staleUnavailable = bundleStorefrontProjector.buildRow(
                discBundle, discVersion, SaleState.UNAVAILABLE,
                Map.of(e1.examId, port.findServing(e1.examId).orElseThrow(),
                        e2.examId, port.findServing(e2.examId).orElseThrow()));
        assertThat(storefrontProductRepository.upsertBundle(UUID.randomUUID(), staleUnavailable, Instant.now()))
                .isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT sale_state FROM commerce.storefront_product WHERE bundle_id = ?", String.class, bundleId))
                .isEqualTo("DISCONTINUED");
    }

    @Test
    void rebuildIsolatesTransactionalBundleRepositoryExceptionWithoutMarkingOuterTxRollbackOnly() throws Exception {
        UUID actor = trackActor();
        var auth = bundleAdminAs(actor);
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder eHealthy = partExam("HSA", "TOAN");
        BundleExamBuilder e2 = subjectExam("HSA", "LY");
        serve(eHealthy);
        serve(e2);

        JsonNode healthy = createBundle(createPayload("RB-TX-OK-" + shortToken(), "Healthy Bundle", hsaId,
                List.of(eHealthy.examId, e2.examId)), auth);
        UUID healthyId = UUID.fromString(healthy.path("id").asText());
        performPublish(healthyId, healthy.path("revision").asLong(), auth).andExpect(status().isOk());

        JsonNode failing = createBundle(createPayload("RB-TX-ERR-" + shortToken(), "Failing Bundle", hsaId,
                List.of(eHealthy.examId, e2.examId)), auth);
        UUID failingId = UUID.fromString(failing.path("id").asText());
        performPublish(failingId, failing.path("revision").asLong(), auth).andExpect(status().isOk());

        jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ? OR bundle_id = ?",
                eHealthy.examId, healthyId);

        doThrow(new RuntimeException("Simulated @Transactional repository failure during bundle rebuild"))
                .when(bundleVersionRepository).findByBundleIdAndVersionNo(eq(failingId), anyInt());
        try {
            mockMvc.perform(post("/api/v1/admin/storefront/rebuild").with(auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.failed").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.written").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM commerce.storefront_product WHERE exam_id = ?",
                    Long.class, eHealthy.examId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM commerce.storefront_product WHERE bundle_id = ?",
                    Long.class, healthyId)).isEqualTo(1L);

            drainOutbox();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM audit.audit_event WHERE action = 'STOREFRONT_REBUILT' AND actor_id = ?",
                    Long.class, actor)).isEqualTo(1L);
        } finally {
            reset(bundleVersionRepository);
        }
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
