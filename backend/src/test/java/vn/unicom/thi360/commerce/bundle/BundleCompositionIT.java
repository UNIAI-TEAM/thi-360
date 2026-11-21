package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjectionService;

/**
 * AC-3, AC-4, AC-5: Validate thành phần bộ đề (2–12 bài, không trùng, đúng kỳ thi, không FULL, đang bán),
 * cho phép bài ôn luyện (REVIEW), giới hạn lượt thi/bài (1–100), và trùng mã không phân biệt hoa thường.
 */
class BundleCompositionIT extends AbstractBundleIT {

    @Autowired
    private StorefrontProjectionService storefrontProjectionService;

    @Test
    void enforcesItemCountRulesDuplicatesKindFullScopeAndSaleState() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");

        List<UUID> twelve = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            BundleExamBuilder e = (i % 2 == 0) ? partExam("HSA", "TOAN") : subjectExam("HSA", "LY");
            serve(e);
            twelve.add(e.examId);
        }
        BundleExamBuilder extra13 = partExam("HSA", "HOA");
        serve(extra13);
        List<UUID> thirteen = new ArrayList<>(twelve);
        thirteen.add(extra13.examId);

        // 1 item & 13 items -> 422 BUNDLE_ITEMS_COUNT_INVALID
        performCreate(createPayload("CNT1-" + shortToken(), "Count 1", hsaId, List.of(twelve.get(0))), auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ITEMS_COUNT_INVALID"));
        performCreate(createPayload("CNT13-" + shortToken(), "Count 13", hsaId, thirteen), auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ITEMS_COUNT_INVALID"));

        // 12 items -> thành công (ràng buộc trên biên 12)
        JsonNode created12 = createBundle(createPayload("CNT12-" + shortToken(), "Count 12", hsaId, twelve), auth);
        assertThat(created12.path("items").size()).isEqualTo(12);

        // Duplicate item -> 422 BUNDLE_ITEM_DUPLICATE
        performCreate(createPayload("DUP-" + shortToken(), "Dup", hsaId, List.of(twelve.get(0), twelve.get(0))), auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ITEM_DUPLICATE"));

        // Draft / unserved exam -> 422 BUNDLE_ITEM_INVALID_EXAM
        performCreate(createPayload("MISS-" + shortToken(), "Missing", hsaId,
                        List.of(twelve.get(0), UUID.randomUUID())), auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ITEM_INVALID_EXAM"));

        // Sai kỳ thi -> 422 BUNDLE_ITEM_KIND_MISMATCH
        BundleExamBuilder sp2e = partExam("SP2E", "TOAN");
        serve(sp2e);
        performCreate(createPayload("KIND-" + shortToken(), "Wrong Kind", hsaId,
                        List.of(twelve.get(0), sp2e.examId)), auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ITEM_KIND_MISMATCH"));

        // FULL scope -> 422 BUNDLE_ITEM_FULL_SCOPE_NOT_ALLOWED
        BundleExamBuilder full = fullExam("HSA", "TOAN");
        serve(full);
        performCreate(createPayload("FULL-" + shortToken(), "Full Exam", hsaId,
                        List.of(twelve.get(0), full.examId)), auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ITEM_FULL_SCOPE_NOT_ALLOWED"));

        // Discontinued hoặc không ON_SALE -> 422 BUNDLE_ITEM_NOT_ON_SALE
        BundleExamBuilder disc = partExam("HSA", "SINH").discontinued();
        serve(disc);
        performCreate(createPayload("DISC-" + shortToken(), "Disc Exam", hsaId,
                        List.of(twelve.get(0), disc.examId)), auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ITEM_NOT_ON_SALE"));

        // attemptsPerExam biên 0, 101 -> 422; 1, 100 -> 201
        Map<String, Object> att0 = createPayload("ATT0-" + shortToken(), "Att 0", hsaId, twelve.subList(0, 2));
        att0.put("attemptsPerExam", 0);
        performCreate(att0, auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ATTEMPTS_INVALID"));

        Map<String, Object> att101 = createPayload("ATT101-" + shortToken(), "Att 101", hsaId, twelve.subList(0, 2));
        att101.put("attemptsPerExam", 101);
        performCreate(att101, auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ATTEMPTS_INVALID"));

        Map<String, Object> att1 = createPayload("ATT1-" + shortToken(), "Att 1", hsaId, twelve.subList(0, 2));
        att1.put("attemptsPerExam", 1);
        JsonNode createdAtt1 = createBundle(att1, auth);
        performPublish(UUID.fromString(createdAtt1.path("id").asText()),
                createdAtt1.path("revision").asLong(), auth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptsPerExam").value(1));

        Map<String, Object> att100 = createPayload("ATT100-" + shortToken(), "Att 100", hsaId, twelve.subList(0, 2));
        att100.put("attemptsPerExam", 100);
        createBundle(att100, auth);
    }

    @Test
    void allowsReviewBuildModeExamAndPropagatesToPublicStorefrontDetail() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder standard = partExam("HSA", "TOAN").named("Đề tiêu chuẩn");
        BundleExamBuilder review = subjectExam("HSA", "VAN").review().named("Bài ôn Ngữ văn");
        serve(standard);
        serve(review);

        // AC-4: API candidates phải liệt kê cả bài ôn REVIEW với buildMode == "REVIEW".
        String candidatesJson = mockMvc.perform(get(ADMIN_BASE + "/candidates").param("examKind", "HSA").with(auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode candidates = mapper.readTree(candidatesJson);
        JsonNode reviewCandidate = null;
        for (JsonNode c : candidates) {
            if (review.examId.toString().equals(c.path("examId").asText())) {
                reviewCandidate = c;
                break;
            }
        }
        assertThat(reviewCandidate).isNotNull();
        assertThat(reviewCandidate.path("buildMode").asText()).isEqualTo("REVIEW");

        String code = "BD-REV-" + shortToken();
        JsonNode created = createBundle(
                createPayload(code, "Bộ đề có bài ôn", hsaId, List.of(standard.examId, review.examId)),
                auth);
        UUID bundleId = UUID.fromString(created.path("id").asText());
        long rev = created.path("revision").asLong();
        assertThat(created.path("items").get(1).path("buildMode").asText()).isEqualTo("REVIEW");

        performPublish(bundleId, rev, auth).andExpect(status().isOk());

        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + code)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productType").value("BUNDLE"))
                .andExpect(jsonPath("$.items[0].buildMode").value("STANDARD"))
                .andExpect(jsonPath("$.items[1].buildMode").value("REVIEW"));
    }

    @Test
    void enforcesCaseInsensitiveCodeUniquenessAgainstBundlesAndExams() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder e1 = partExam("HSA", "TOAN");
        BundleExamBuilder e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);
        // Project e1 lên storefront_product để kiểm tra va chạm với mã bài thi đã bán
        storefrontProjectionService.project(e1.examId);

        String baseCode = "UniCode-" + shortToken();
        createBundle(createPayload(baseCode, "Bundle 1", hsaId, List.of(e1.examId, e2.examId)), auth);

        // Trùng mã bộ đề khác hoa thường -> 409 BUNDLE_CODE_TAKEN
        performCreate(createPayload(baseCode.toLowerCase(), "Bundle 2", hsaId, List.of(e1.examId, e2.examId)), auth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUNDLE_CODE_TAKEN"));

        // Trùng mã bài thi (e1.code) khác hoa thường -> 409 BUNDLE_CODE_TAKEN
        performCreate(createPayload(e1.code.toLowerCase(), "Bundle Exam Conflict", hsaId,
                        List.of(e1.examId, e2.examId)), auth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUNDLE_CODE_TAKEN"));
    }
}
