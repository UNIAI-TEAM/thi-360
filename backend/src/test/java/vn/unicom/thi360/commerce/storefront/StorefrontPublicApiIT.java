package vn.unicom.thi360.commerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjectionService;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Combination;
import vn.unicom.thi360.exam.spi.PublishedExamPort.OmittedGroup;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/** CM-1: the public catalog API over rows the projection wrote from a fake exam port. */
class StorefrontPublicApiIT extends AbstractStorefrontIT {

    private static final String BASE = "/api/v1/public/storefront";

    @Autowired StorefrontProjectionService projection;

    /** Serves the exam and projects it, as the handler would after an event. */
    private void sell(TestExam exam) {
        serve(exam);
        projection.project(exam.examId);
    }

    private static String token() {
        return "tok" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json(result.getResponse().getContentAsString());
    }

    private List<String> codes(JsonNode page) {
        List<String> codes = new ArrayList<>();
        page.path("items").forEach(item -> codes.add(item.path("code").asText()));
        return codes;
    }

    // --------------------------------------------------------------- 404, 410

    @Test
    void draftCodeLooksLikeUnknownCode() throws Exception {
        TestExam draft = exam(); // a draft is never served by the exam module, so the projection never makes a row
        projection.project(draft.examId);
        String unknown = "NOPE" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult asDraft = publicGet(BASE + "/products/" + draft.code, newClientIp())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND")).andReturn();
        MvcResult asUnknown = publicGet(BASE + "/products/" + unknown, newClientIp())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND")).andReturn();

        ObjectNode draftBody = (ObjectNode) body(asDraft);
        ObjectNode unknownBody = (ObjectNode) body(asUnknown);
        assertThat(draftBody.path("traceId").asText()).isNotBlank();
        draftBody.remove("traceId");
        unknownBody.remove("traceId");
        assertThat(draftBody).as("same body but the trace id").isEqualTo(unknownBody);
        assertThat(draftBody.path("message").asText()).isNotBlank();
    }

    @Test
    void discontinuedCodeAnswers410WithProduct() throws Exception {
        TestExam exam = exam().named("Bai thi da ngung ban");
        sell(exam);
        sell(exam.asVersion(1, exam.name, exam.listPrice).discontinued());

        MvcResult result = publicGet(BASE + "/products/" + exam.code, newClientIp())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"))
                .andExpect(jsonPath("$.product.code").value(exam.code))
                .andExpect(jsonPath("$.product.name").value("Bai thi da ngung ban"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn();
        JsonNode body = body(result);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("traceId").asText()).isNotBlank();
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).as("an error is not cached")
                .doesNotContain("public");
        // The lower-case code reaches the same product.
        publicGet(BASE + "/products/" + exam.code.toLowerCase(Locale.ROOT), newClientIp()).andExpect(status().isGone());
    }

    @Test
    void discontinuedProductLeavesProductsAndFilters() throws Exception {
        String tag = token();
        TestExam exam = exam().named("Bai " + tag);
        sell(exam);
        String ip = newClientIp();
        assertThat(codes(body(publicGet(BASE + "/products?q=" + tag, ip).andReturn()))).containsExactly(exam.code);
        assertThat(body(publicGet(BASE + "/filters", ip).andReturn()).path("parts").toString())
                .contains(exam.defaultPartCode());

        sell(exam.asVersion(1, exam.name, exam.listPrice).discontinued());

        assertThat(codes(body(publicGet(BASE + "/products?q=" + tag, ip).andReturn()))).isEmpty();
        assertThat(body(publicGet(BASE + "/filters", ip).andReturn()).path("parts").toString())
                .doesNotContain(exam.defaultPartCode());
    }

    // ------------------------------------------------------------------ filters

    @Test
    void subjectFilterIncludesFullExams() throws Exception {
        String tag = token();
        TestExam full = exam().named("Full " + tag).withParts(
                List.of(TestExam.part("p1", 1, "Toan", "TOAN", 50, 75, 50), TestExam.part("p2", 2, "Ly", "LY", 50, 60, 50)),
                List.of(TestExam.path("default", 1, 100, 100, 135, "p1", "Toan")));
        TestExam part = exam().named("Part " + tag).scope("PART", "ZP" + tag.substring(3), null).withParts(
                List.of(TestExam.part("p2", 2, "Ly", "LY", 50, 60, 50)),
                List.of(TestExam.path("default", 1, 50, 50, 60, "p2", "Ly")));
        TestExam other = exam().named("Other " + tag).withParts(
                List.of(TestExam.part("p1", 1, "Van", "VAN", 50, 75, 50)),
                List.of(TestExam.path("default", 1, 50, 50, 75, "p1", "Van")));
        sell(full);
        sell(part);
        sell(other);

        JsonNode page = body(publicGet(BASE + "/products?subject=LY&q=" + tag, newClientIp())
                .andExpect(status().isOk()).andReturn());

        assertThat(codes(page)).containsExactlyInAnyOrder(full.code, part.code);
        assertThat(page.path("totalItems").asLong()).isEqualTo(2);
        // An unknown subject is an empty page, not an error.
        assertThat(codes(body(publicGet(BASE + "/products?subject=NOSUCH&q=" + tag, newClientIp())
                .andExpect(status().isOk()).andReturn()))).isEmpty();
    }

    @Test
    void kindFormatPartFilters() throws Exception {
        String tag = token();
        String partCode = "ZP" + tag.substring(3);
        TestExam hsaFull = exam("HSA").named("HsaFull " + tag);
        TestExam hsaPart = exam("HSA").named("HsaPart " + tag).scope("PART", partCode, null);
        TestExam qdaFull = exam("QDA").named("QdaFull " + tag);
        TestExam qdaSubject = exam("QDA").named("QdaSubject " + tag).scope("SUBJECT", null, subjectItem("TOAN"));
        sell(hsaFull);
        sell(hsaPart);
        sell(qdaFull);
        sell(qdaSubject);
        String q = "&q=" + tag;

        assertThat(codes(body(publicGet(BASE + "/products?kind=HSA" + q, newClientIp()).andReturn())))
                .containsExactlyInAnyOrder(hsaFull.code, hsaPart.code);
        assertThat(codes(body(publicGet(BASE + "/products?kind=qda" + q, newClientIp()).andReturn())))
                .as("a kind code matches whatever its case").containsExactlyInAnyOrder(qdaFull.code, qdaSubject.code);
        assertThat(codes(body(publicGet(BASE + "/products?format=PART" + q, newClientIp()).andReturn())))
                .containsExactly(hsaPart.code);
        assertThat(codes(body(publicGet(BASE + "/products?format=SUBJECT" + q, newClientIp()).andReturn())))
                .containsExactly(qdaSubject.code);
        assertThat(codes(body(publicGet(BASE + "/products?format=FULL&kind=QDA" + q, newClientIp()).andReturn())))
                .containsExactly(qdaFull.code);
        assertThat(codes(body(publicGet(BASE + "/products?part=" + partCode + "&kind=HSA" + q, newClientIp())
                .andReturn()))).containsExactly(hsaPart.code);
        assertThat(codes(body(publicGet(BASE + "/products?part=" + partCode + "&kind=QDA" + q, newClientIp())
                .andReturn()))).as("a part code means something inside its own kind").isEmpty();
        publicGet(BASE + "/products?format=NOPE", newClientIp()).andExpect(status().isBadRequest());
    }

    @Test
    void filtersOmitZeroCounts() throws Exception {
        TestExam exam = exam("HSA");
        sell(exam);
        String ip = newClientIp();

        JsonNode filters = body(publicGet(BASE + "/filters", ip).andExpect(status().isOk()).andReturn());

        List<JsonNode> counted = new ArrayList<>();
        filters.path("kinds").forEach(counted::add);
        filters.path("formats").forEach(counted::add);
        filters.path("subjects").forEach(counted::add);
        filters.path("parts").forEach(counted::add);
        assertThat(counted).isNotEmpty().allSatisfy(value -> assertThat(value.path("count").asLong()).isPositive());
        // A kind appears exactly when it has an on-sale product.
        for (var kind : catalog.listGroupItems("EXAM")) {
            long onSale = jdbc.queryForObject(
                    "SELECT count(*) FROM commerce.storefront_product WHERE sale_state = 'ON_SALE' AND kind_code = ?",
                    Long.class, kind.code());
            long shown = 0;
            for (JsonNode shownKind : filters.path("kinds")) {
                if (shownKind.path("code").asText().equals(kind.code())) {
                    shown = shownKind.path("count").asLong();
                }
            }
            assertThat(shown).as("count of kind %s", kind.code()).isEqualTo(onSale);
        }
        JsonNode ownPart = null;
        for (JsonNode part : filters.path("parts")) {
            if (part.path("code").asText().equals(exam.defaultPartCode())) {
                ownPart = part;
            }
        }
        assertThat(ownPart).isNotNull();
        assertThat(ownPart.path("kindCode").asText()).isEqualTo("HSA");
        assertThat(ownPart.path("name").asText()).isEqualTo("Phan " + exam.partOrder);
        assertThat(ownPart.path("count").asLong()).isEqualTo(1);
        assertThat(filters.path("subjects").toString()).contains("\"code\":\"TOAN\"");
        assertThat(filters.path("formats").toString()).contains("\"code\":\"FULL\"");
    }

    // ------------------------------------------------------- search, sort, paging

    @Test
    void searchSortAndPaging() throws Exception {
        String tag = token();
        TestExam oldest = exam().named("De Toan cu " + tag).price(300_000L).published(Instant.parse("2026-01-01T00:00:00Z"));
        TestExam middle = exam().named("Đề Toán giữa " + tag).price(100_000L).published(Instant.parse("2026-02-01T00:00:00Z"));
        TestExam newest = exam().named("De Van moi " + tag).price(200_000L).published(Instant.parse("2026-03-01T00:00:00Z"));
        sell(oldest);
        sell(middle);
        sell(newest);
        String ip = newClientIp();

        // q ignores accents and case, in the name and in the code.
        assertThat(codes(body(mockMvc.perform(publicRequest(BASE + "/products", ip).param("q", "de toan")).andReturn())))
                .contains(oldest.code, middle.code).doesNotContain(newest.code);
        assertThat(codes(body(mockMvc.perform(publicRequest(BASE + "/products", ip).param("q", "ĐỀ TOÁN GIỮA"))
                .andReturn()))).contains(middle.code);
        assertThat(codes(body(publicGet(BASE + "/products?q=" + newest.code.toLowerCase(Locale.ROOT), ip).andReturn())))
                .containsExactly(newest.code);
        // Wildcards of LIKE are plain characters.
        assertThat(codes(body(mockMvc.perform(publicRequest(BASE + "/products", ip).param("q", "%")).andReturn())))
                .isEmpty();

        // Default order is "Moi cong bo"; then the two price orders.
        assertThat(codes(body(publicGet(BASE + "/products?q=" + tag, ip).andReturn())))
                .containsExactly(newest.code, middle.code, oldest.code);
        assertThat(codes(body(publicGet(BASE + "/products?sort=PRICE_ASC&q=" + tag, ip).andReturn())))
                .containsExactly(middle.code, newest.code, oldest.code);
        assertThat(codes(body(publicGet(BASE + "/products?sort=PRICE_DESC&q=" + tag, ip).andReturn())))
                .containsExactly(oldest.code, newest.code, middle.code);
        publicGet(BASE + "/products?sort=CHEAPEST", ip).andExpect(status().isBadRequest());

        // Paging: size 12 by default, 48 at most (49 is cut, not refused); page counts from 0.
        String pagingTag = token();
        for (int i = 0; i < 50; i++) {
            TestExam exam = exam().named(String.format("Bai %02d %s", i, pagingTag))
                    .published(Instant.parse("2026-06-01T00:00:00Z").plusSeconds(i));
            sell(exam);
        }
        JsonNode first = body(publicGet(BASE + "/products?q=" + pagingTag, ip).andReturn());
        assertThat(first.path("size").asInt()).isEqualTo(12);
        assertThat(first.path("page").asInt()).isZero();
        assertThat(first.path("items")).hasSize(12);
        assertThat(first.path("totalItems").asLong()).isEqualTo(50);
        assertThat(first.path("totalPages").asInt()).isEqualTo(5);
        JsonNode last = body(publicGet(BASE + "/products?page=4&q=" + pagingTag, ip).andReturn());
        assertThat(last.path("items")).hasSize(2);
        JsonNode big = body(publicGet(BASE + "/products?size=49&q=" + pagingTag, ip).andReturn());
        assertThat(big.path("size").asInt()).isEqualTo(48);
        assertThat(big.path("items")).hasSize(48);
        assertThat(big.path("totalPages").asInt()).isEqualTo(2);
        publicGet(BASE + "/products?size=0", ip).andExpect(status().isBadRequest());
        publicGet(BASE + "/products?page=-1", ip).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------ fields, forbidden keys

    @Test
    void cardAndDetailCarrySpecFields() throws Exception {
        UUID cover = UUID.randomUUID();
        port.cover(cover, "image/png", new byte[] {1, 2, 3});
        Part part3 = TestExam.choicePart("p3", 3, "Khoa hoc hoac Tieng Anh", 50, 60, List.of(
                TestExam.option("o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc", 3, List.of(17, 17, 16),
                        List.of(TestExam.section("LY", 17), TestExam.section("HOA", 17), TestExam.section("SINH", 16),
                                TestExam.section("SU", 17), TestExam.section("DIA", 17))),
                TestExam.option("o-eng", "SUBJECT", "ANH", "Tieng Anh", null, null, List.of(TestExam.section("ANH", 50)))));
        ServingPath science = TestExam.optionPath("o-sci", 1, 150, 150, 195, "p3", "Khoa hoc hoac Tieng Anh",
                new PathChoice("p3", "o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc"));
        ServingPath english = TestExam.optionPath("o-eng", 1, 150, 150, 195, "p3", "Khoa hoc hoac Tieng Anh",
                new PathChoice("p3", "o-eng", "SUBJECT", "ANH", "Tieng Anh"));
        Combination combination = TestExam.combination("p3", "QD01", "Ma QD01", "KHTN", "TOAN", "LY");
        ServingPath combined = new ServingPath("p3:QD01", 1,
                List.of(new PathChoice("p3", "p3:QD01", "COMBINATION", "QD01", "Ma QD01")), 150,
                BigDecimal.valueOf(150), 195, List.of(), List.of(combination));
        TestExam exam = exam().cover(cover).withParts(
                List.of(TestExam.part("p1", 1, "Toan hoc", "TOAN", 50, 75, 50), part3),
                List.of(science, english, combined));
        exam.omitted.add(new OmittedGroup("Tu luan", new BigDecimal("2.5")));
        sell(exam);
        String ip = newClientIp();

        JsonNode card = body(publicGet(BASE + "/products?q=" + exam.code, ip).andReturn()).path("items").get(0);
        assertThat(card.path("code").asText()).isEqualTo(exam.code);
        assertThat(card.path("name").asText()).isEqualTo(exam.name);
        assertThat(card.path("summary").asText()).isEqualTo("Tom tat");
        assertThat(card.path("coverUrl").asText()).isEqualTo(BASE + "/covers/" + cover);
        assertThat(card.path("examKind").path("code").asText()).isEqualTo("HSA");
        assertThat(card.path("examKind").path("nameVi").asText()).isNotBlank();
        assertThat(card.path("examKind").path("nameEn").asText()).isNotBlank();
        assertThat(card.path("format").asText()).isEqualTo("FULL");
        assertThat(card.has("scopeLabel")).isTrue();
        assertThat(card.path("buildMode").asText()).isEqualTo("STANDARD");
        assertThat(card.path("questionCount").path("min").asInt()).isEqualTo(150);
        assertThat(card.path("durationMinutes").path("max").asInt()).isEqualTo(195);
        assertThat(card.path("attemptsGranted").asInt()).isEqualTo(3);
        assertThat(card.path("listPrice").asLong()).isEqualTo(199_000L);
        assertThat(card.path("price").asLong()).as("temporary calculator: price = list price").isEqualTo(199_000L);
        assertThat(card.has("promotion")).isTrue();
        assertThat(card.path("promotion").isNull()).isTrue();
        assertThat(card.path("versionNo").asInt()).isEqualTo(1);

        JsonNode detail = body(publicGet(BASE + "/products/" + exam.code, ip).andReturn());
        assertThat(detail.path("code").asText()).isEqualTo(exam.code);
        assertThat(detail.path("description").asText()).isEqualTo("Mo ta");
        assertThat(detail.path("objectives").asText()).isEqualTo("Muc tieu");
        assertThat(detail.path("audience").asText()).isEqualTo("Doi tuong");
        assertThat(detail.path("maxPoints").path("min").decimalValue()).isEqualByComparingTo("150");
        // Parts in the order of the exam, with their totals and whether they are required.
        JsonNode parts = detail.path("parts");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).path("name").asText()).isEqualTo("Toan hoc");
        assertThat(parts.get(0).path("required").asBoolean()).isTrue();
        assertThat(parts.get(1).path("name").asText()).isEqualTo("Khoa hoc hoac Tieng Anh");
        assertThat(parts.get(1).path("required").asBoolean()).as("part 3 is the student's choice").isFalse();
        assertThat(parts.get(1).path("questionCount").path("min").asInt()).isEqualTo(150);
        // The choices inside the scope bought: science 3 of 5 with 16 or 17 per subject, English, and the QDA code.
        JsonNode options = detail.path("choices").get(0).path("options");
        assertThat(detail.path("choices").get(0).path("partName").asText()).isEqualTo("Khoa hoc hoac Tieng Anh");
        JsonNode scienceOption = options.get(0);
        assertThat(scienceOption.path("kind").asText()).isEqualTo("SUBJECT_CHOICE");
        assertThat(scienceOption.path("chooseSubjects").asInt()).isEqualTo(3);
        assertThat(scienceOption.path("countsByPick").toString()).isEqualTo("[17,17,16]");
        assertThat(scienceOption.path("subjects")).hasSize(5);
        assertThat(scienceOption.path("subjects").get(0).path("nameVi").asText()).isEqualTo("Vật lí");
        assertThat(options.get(1).path("code").asText()).isEqualTo("ANH");
        JsonNode code = options.get(2);
        assertThat(code.path("kind").asText()).isEqualTo("COMBINATION");
        assertThat(code.path("code").asText()).isEqualTo("QD01");
        assertThat(code.path("branch").asText()).isEqualTo("KHTN");
        assertThat(code.path("subjects").get(0).path("code").asText()).isEqualTo("TOAN");
        assertThat(code.path("subjects").get(1).path("code").asText()).isEqualTo("LY");
        // How it is timed and scored, the omitted essay, the benefits.
        assertThat(detail.path("scoring").path("timing").asText()).isEqualTo("PER_PART");
        assertThat(detail.path("scoring").path("maxScore").decimalValue()).isEqualByComparingTo("150");
        assertThat(detail.path("scoring").path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(detail.path("omittedEssayGroups").get(0).path("name").asText()).isEqualTo("Tu luan");
        assertThat(detail.path("omittedEssayGroups").get(0).path("points").decimalValue()).isEqualByComparingTo("2.5");
        assertThat(detail.path("benefits").path("aiExplanation").asBoolean()).isTrue();
        assertThat(detail.path("benefits").path("strengthWeakness").asBoolean()).isFalse();
        assertThat(detail.path("benefits").path("practice").asBoolean()).isTrue();
        assertThat(detail.path("benefits").path("note").asText()).isEqualTo("Ghi chu");
        assertThat(detail.path("resultView").asText()).isEqualTo("SCORE_AND_ANSWERS");
    }

    @Test
    void responsesCarryNoForbiddenKeys() throws Exception {
        TestExam exam = exam().withParts(
                List.of(TestExam.part("p1", 1, "Toan", "TOAN", 50, 75, 50)),
                List.of(TestExam.path("default", 1, 50, 50, 75, "p1", "Toan")));
        exam.omitted.add(new OmittedGroup("Tu luan", new BigDecimal("2.5")));
        sell(exam);
        String ip = newClientIp();

        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(
                publicGet(BASE + "/filters", ip).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(
                publicGet(BASE + "/products?q=" + exam.code, ip).andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString());
        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(
                publicGet(BASE + "/products/" + exam.code, ip).andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString());
    }

    // -------------------------------------------------------- anonymous, cookies

    @Test
    void anonymousCallSucceeds() throws Exception {
        TestExam exam = exam();
        sell(exam);
        String ip = newClientIp();

        publicGet(BASE + "/filters", ip).andExpect(status().isOk());
        publicGet(BASE + "/products", ip).andExpect(status().isOk());
        publicGet(BASE + "/products/" + exam.code, ip).andExpect(status().isOk());
        // Anything else outside /api/v1/public still needs a login.
        publicGet("/api/v1/me", ip).andExpect(status().isUnauthorized());
    }

    @Test
    void staleAccessCookieIsIgnored() throws Exception {
        TestExam exam = exam();
        sell(exam);
        String ip = newClientIp();
        Cookie stale = new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE,
                "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4IiwiZXhwIjoxfQ.invalid-signature");

        publicGet(BASE + "/filters", ip).andReturn();
        mockMvc.perform(publicRequest(BASE + "/filters", ip).cookie(stale)).andExpect(status().isOk());
        mockMvc.perform(publicRequest(BASE + "/products", ip).cookie(stale)).andExpect(status().isOk());
        mockMvc.perform(publicRequest(BASE + "/products/" + exam.code, ip).cookie(stale)).andExpect(status().isOk());
        mockMvc.perform(publicRequest(BASE + "/products/" + exam.code, ip).cookie(new Cookie(
                AuthCookieService.ACCESS_TOKEN_COOKIE, "garbage"))).andExpect(status().isOk());
        // The same cookie is still refused where a login is needed: only the public prefix skips it.
        mockMvc.perform(publicRequest("/api/v1/me", ip).cookie(stale)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ cover, caching

    @Test
    void coverServesOnlyCurrentCovers() throws Exception {
        UUID current = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        port.cover(current, "image/png", new byte[] {9, 8, 7});
        port.cover(unrelated, "image/jpeg", new byte[] {1});
        port.cover(replacement, "image/webp", new byte[] {4, 5});
        TestExam exam = exam().cover(current);
        sell(exam);
        String ip = newClientIp();

        MvcResult served = publicGet(BASE + "/covers/" + current, ip).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)).andReturn();
        assertThat(served.getResponse().getContentAsByteArray()).containsExactly(9, 8, 7);
        // An image the exam module has but that is no cover of the storefront stays private; so does a made-up id.
        publicGet(BASE + "/covers/" + unrelated, ip).andExpect(status().isNotFound());
        publicGet(BASE + "/covers/" + UUID.randomUUID(), ip).andExpect(status().isNotFound());

        // A new version with another cover: the old image is no longer served, the new one is.
        sell(exam.asVersion(2, exam.name, exam.listPrice).cover(replacement));
        publicGet(BASE + "/covers/" + current, ip).andExpect(status().isNotFound());
        publicGet(BASE + "/covers/" + replacement, ip).andExpect(status().isOk());
    }

    @Test
    void cacheHeadersAndConditionalGet() throws Exception {
        UUID cover = UUID.randomUUID();
        port.cover(cover, "image/png", new byte[] {1, 2, 3, 4});
        TestExam exam = exam().cover(cover);
        sell(exam);
        String ip = newClientIp();

        for (String path : List.of(BASE + "/filters", BASE + "/products?q=" + exam.code, BASE + "/products/" + exam.code,
                BASE + "/covers/" + cover)) {
            MvcResult first = publicGet(path, ip).andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=60, public"))
                    .andExpect(header().exists(HttpHeaders.ETAG)).andReturn();
            String etag = first.getResponse().getHeader(HttpHeaders.ETAG);

            MvcResult notModified = mockMvc.perform(publicRequest(path, ip).header(HttpHeaders.IF_NONE_MATCH, etag))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=60, public"))
                    .andExpect(header().string(HttpHeaders.ETAG, etag)).andReturn();
            assertThat(notModified.getResponse().getContentAsByteArray()).isEmpty();

            mockMvc.perform(publicRequest(path, ip).header(HttpHeaders.IF_NONE_MATCH, "\"something-else\""))
                    .andExpect(status().isOk());
        }
        // A change of the product changes the ETag of its detail.
        String before = publicGet(BASE + "/products/" + exam.code, ip).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        sell(exam.asVersion(2, exam.name, 250_000L));
        String after = publicGet(BASE + "/products/" + exam.code, ip).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(after).isNotEqualTo(before);
    }
}
