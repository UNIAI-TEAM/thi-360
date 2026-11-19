package vn.unicom.thi360.commerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import vn.unicom.thi360.exam.spi.PublishedExamPort.BlockSummary;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Combination;
import vn.unicom.thi360.exam.spi.PublishedExamPort.OmittedGroup;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;
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

        // A stored row that is neither on sale nor discontinued (UNAVAILABLE, a bundle state of CM-6) answers the same.
        TestExam stored = exam();
        sell(stored);
        jdbc.update("UPDATE commerce.storefront_product SET sale_state = 'UNAVAILABLE', product_type = 'BUNDLE', "
                + "exam_id = NULL, bundle_id = ? WHERE exam_id = ?", UUID.randomUUID(), stored.examId);
        try {
            ObjectNode storedBody = (ObjectNode) body(publicGet(BASE + "/products/" + stored.code, newClientIp())
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND")).andReturn());
            storedBody.remove("traceId");
            assertThat(storedBody).as("a stored non-sale row is indistinguishable from an unknown code")
                    .isEqualTo(unknownBody);
        } finally {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE code_normalized = ?", stored.code.toLowerCase());
        }
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
        TestExam exam = exam().named("Bai " + tag).scope("PART", "ZP" + tag.substring(3), null);
        sell(exam);
        String ip = newClientIp();
        assertThat(codes(body(publicGet(BASE + "/products?q=" + tag, ip).andReturn()))).containsExactly(exam.code);
        assertThat(body(publicGet(BASE + "/filters", ip).andReturn()).path("parts").toString())
                .contains(exam.partCode);

        sell(exam.asVersion(1, exam.name, exam.listPrice).discontinued());

        assertThat(codes(body(publicGet(BASE + "/products?q=" + tag, ip).andReturn()))).isEmpty();
        assertThat(body(publicGet(BASE + "/filters", ip).andReturn()).path("parts").toString())
                .doesNotContain(exam.partCode);
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
        TestExam exam = exam("HSA").scope("PART", "ZP" + token().substring(3), null);
        TestExam full = exam("HSA");
        sell(exam);
        sell(full);
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
            if (part.path("code").asText().equals(exam.partCode)) {
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

    // ------------------------------------------------- Phần facet (R1-4, AD-78)

    /** HSA shape: the FULL exam holds the Toan part and a PART exam stands for it; the Phần facet names PART exams only. */
    @Test
    void partFacetAndFilterCoverPartExamsOnly() throws Exception {
        String tag = token();
        String partCode = "ZP" + tag.substring(3);
        String toan = "Toan hoc va xu li so lieu";
        TestExam full = exam("HSA").named("HsaFull " + tag).withParts(
                List.of(TestExam.part("p1", 1, toan, "TOAN", 50, 75, 50),
                        TestExam.part("p2", 2, "Ngon ngu - Van hoc", "VAN", 50, 60, 50)),
                List.of(TestExam.path("default", 1, 100, 100, 135, "p1", toan)));
        TestExam part = exam("HSA").named("HsaPart " + tag).scope("PART", partCode, null).withParts(
                List.of(TestExam.part("p1", 1, toan, "TOAN", 50, 75, 50)),
                List.of(TestExam.path("default", 1, 50, 50, 75, "p1", toan)));
        sell(full);
        sell(part);
        String ip = newClientIp();
        String q = "&q=" + tag;

        JsonNode facet = body(publicGet(BASE + "/filters", ip).andExpect(status().isOk()).andReturn()).path("parts");
        List<String> facetKeys = new ArrayList<>();
        JsonNode own = null;
        for (JsonNode entry : facet) {
            facetKeys.add(entry.path("kindCode").asText() + ":" + entry.path("code").asText());
            if (entry.path("code").asText().equals(partCode)) {
                own = entry;
            }
        }
        assertThat(own).as("the PART exam's code is in the facet").isNotNull();
        assertThat(own.path("kindCode").asText()).isEqualTo("HSA");
        assertThat(own.path("name").asText()).isEqualTo(toan);
        assertThat(own.path("count").asLong()).isEqualTo(1);
        assertThat(facetKeys).as("the FULL exam adds no positional part code").doesNotContain("HSA:P1", "HSA:P2");

        assertThat(codes(body(publicGet(BASE + "/products?kind=HSA" + q, ip).andReturn())))
                .containsExactlyInAnyOrder(full.code, part.code);
        assertThat(codes(body(publicGet(BASE + "/products?kind=HSA&part=" + partCode + q, ip).andReturn())))
                .as("part= matches PART exams only").containsExactly(part.code);
        assertThat(codes(body(publicGet(BASE + "/products?kind=HSA&format=FULL&part=" + partCode + q, ip).andReturn())))
                .as("a FULL exam is not found through a part").isEmpty();
        assertThat(codes(body(publicGet(BASE + "/products?kind=HSA&part=P1" + q, ip).andReturn())))
                .as("a positional code names no part").isEmpty();
        assertThat(codes(body(publicGet(BASE + "/products?kind=HSA&format=FULL" + q, ip).andReturn())))
                .as("the FULL exam is found through the format").containsExactly(full.code);
        assertThat(codes(body(publicGet(BASE + "/products?kind=HSA&format=PART" + q, ip).andReturn())))
                .containsExactly(part.code);
    }

    // ---------------------------------------------------- answer types (R1-2, AD-78)

    private static List<String> typeRows(JsonNode types) {
        List<String> rows = new ArrayList<>();
        types.forEach(t -> rows.add(t.path("questionType").asText() + ":" + t.path("questionCount").asInt()
                + (t.path("childAnswerType").isNull() || t.path("childAnswerType").isMissingNode() ? ""
                        : ":" + t.path("childAnswerType").asText())));
        return rows;
    }

    /** HSA shape: part 1 is fixed; part 3 is science (2 of 2 subjects) or English; an option no path serves adds nothing. */
    @Test
    void answerTypesAreNeverAddedAcrossExclusiveOptions() throws Exception {
        Part p1 = TestExam.withBlocks(TestExam.part("p1", 1, "Toan hoc", "TOAN", 50, 75, 50), List.of(
                TestExam.block("s-p1", "SINGLE_CHOICE", 35), TestExam.block("s-p1", "SHORT_ANSWER", 15),
                TestExam.omittedBlock("s-p1", "ESSAY", 1)));
        Part p3 = TestExam.withBlocks(TestExam.choicePart("p3", 3, "Khoa hoc hoac Tieng Anh", 50, 60, List.of(
                TestExam.option("o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc", 2, List.of(25, 25),
                        List.of(TestExam.section("LY", 25), TestExam.section("HOA", 25))),
                TestExam.option("o-eng", "SUBJECT", "ANH", "Tieng Anh", null, null,
                        List.of(TestExam.section("ANH", 50))),
                TestExam.option("o-unserved", "SUBJECT", "VAN", "Khong phuc vu", null, null,
                        List.of(TestExam.section("VAN", 50))))), List.of(
                TestExam.optionBlock("o-sci", "sec-LY", "SINGLE_CHOICE", 22),
                TestExam.optionBlock("o-sci", "sec-LY", "SHORT_ANSWER", 3),
                TestExam.optionBlock("o-sci", "sec-HOA", "SINGLE_CHOICE", 25),
                TestExam.optionBlock("o-eng", "sec-ANH", "SINGLE_CHOICE", 40),
                new BlockSummary("QUESTION_SET", 10, "SINGLE_CHOICE", "o-eng", "sec-ANH", false),
                TestExam.optionBlock("o-unserved", "sec-VAN", "ESSAY", 5)));
        ServingPath science = TestExam.optionPath("o-sci", 1, 100, 100, 135, "p3", "Khoa hoc hoac Tieng Anh",
                new PathChoice("p3", "o-sci", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc"));
        ServingPath english = TestExam.optionPath("o-eng", 1, 100, 100, 135, "p3", "Khoa hoc hoac Tieng Anh",
                new PathChoice("p3", "o-eng", "SUBJECT", "ANH", "Tieng Anh"));
        TestExam exam = exam().withParts(List.of(p1, p3), List.of(science, english));
        sell(exam);

        JsonNode detail = body(publicGet(BASE + "/products/" + exam.code, newClientIp()).andExpect(status().isOk())
                .andReturn());

        JsonNode parts = detail.path("parts");
        assertThat(typeRows(parts.get(0).path("answerTypes"))).as("fixed part, omitted essay left out")
                .containsExactly("SINGLE_CHOICE:35", "SHORT_ANSWER:15");
        assertThat(typeRows(parts.get(1).path("answerTypes"))).as("exclusive options are not fixed content").isEmpty();
        JsonNode options = detail.path("choices").get(0).path("options");
        assertThat(options).as("the option no path serves is absent").hasSize(2);
        JsonNode sci = options.get(0);
        assertThat(sci.path("code").asText()).isEqualTo("SCIENCE");
        assertThat(typeRows(sci.path("answerTypes"))).isEmpty();
        assertThat(sci.path("subjectAnswerTypes")).hasSize(2);
        assertThat(sci.path("subjectAnswerTypes").get(0).path("subject").path("code").asText()).isEqualTo("LY");
        assertThat(typeRows(sci.path("subjectAnswerTypes").get(0).path("answerTypes")))
                .containsExactly("SINGLE_CHOICE:22", "SHORT_ANSWER:3");
        assertThat(sci.path("subjectAnswerTypes").get(1).path("subject").path("code").asText()).isEqualTo("HOA");
        assertThat(typeRows(sci.path("subjectAnswerTypes").get(1).path("answerTypes"))).containsExactly("SINGLE_CHOICE:25");
        JsonNode eng = options.get(1);
        assertThat(eng.path("subjectAnswerTypes")).hasSize(1);
        assertThat(typeRows(eng.path("subjectAnswerTypes").get(0).path("answerTypes")))
                .as("English alone makes the 50 questions of the path")
                .containsExactly("SINGLE_CHOICE:40", "QUESTION_SET:10:SINGLE_CHOICE");
        assertThat(detail.toString()).as("neither the omitted essay nor the unserved option's essay").doesNotContain("ESSAY");
    }

    /** QDA shape: a pool of subject sections; the combination code names its subjects, each with its own types. */
    @Test
    void poolAnswerTypesAreReportedPerSubjectOfTheCombination() throws Exception {
        Part pool = TestExam.poolPart("pool", 3, "Khoa hoc hoac Tieng Anh", 50, 60,
                List.of(TestExam.section("LY", 25), TestExam.section("HOA", 25), TestExam.section("ANH", 50)),
                List.of(TestExam.block("sec-LY", "SINGLE_CHOICE", 22), TestExam.block("sec-LY", "SHORT_ANSWER", 3),
                        TestExam.block("sec-HOA", "SINGLE_CHOICE", 25), TestExam.block("sec-ANH", "SINGLE_CHOICE", 50)));
        Combination combination = TestExam.combination("pool", "QD01", "Ma QD01", "KHTN", "LY", "HOA");
        ServingPath combined = new ServingPath("pool:QD01", 1,
                List.of(new PathChoice("pool", "pool:QD01", "COMBINATION", "QD01", "Ma QD01")), 50,
                BigDecimal.valueOf(50), 60, List.of(new ServingPathPart("pool", "Khoa hoc hoac Tieng Anh", 50,
                BigDecimal.valueOf(50), 60)), List.of(combination));
        TestExam exam = exam().withParts(List.of(pool), List.of(combined));
        sell(exam);

        JsonNode detail = body(publicGet(BASE + "/products/" + exam.code, newClientIp()).andExpect(status().isOk())
                .andReturn());

        assertThat(typeRows(detail.path("parts").get(0).path("answerTypes"))).as("pool candidates are not fixed").isEmpty();
        JsonNode code = detail.path("choices").get(0).path("options").get(0);
        assertThat(code.path("kind").asText()).isEqualTo("COMBINATION");
        assertThat(typeRows(code.path("answerTypes"))).isEmpty();
        assertThat(code.path("subjectAnswerTypes")).as("English is in the pool but not in this code").hasSize(2);
        assertThat(code.path("subjectAnswerTypes").get(0).path("subject").path("code").asText()).isEqualTo("LY");
        assertThat(typeRows(code.path("subjectAnswerTypes").get(0).path("answerTypes")))
                .containsExactly("SINGLE_CHOICE:22", "SHORT_ANSWER:3");
        assertThat(code.path("subjectAnswerTypes").get(1).path("subject").path("code").asText()).isEqualTo("HOA");
        assertThat(typeRows(code.path("subjectAnswerTypes").get(1).path("answerTypes"))).containsExactly("SINGLE_CHOICE:25");
    }

    // ------------------------------------------------ older serving versions (R1-5)

    /**
     * E24: v2 is the latest but v1 still serves a path. v1 froze a part, a fixed subject and a choice that v2 does not
     * have; the product keeps all of them, while name, price and version come from v2.
     */
    @Test
    void olderServingVersionKeepsItsPartsSubjectsAndChoices() throws Exception {
        Part p1 = TestExam.part("p1", 1, "Toan hoc", "TOAN", 50, 75, 50);
        Part p2 = TestExam.part("p2", 2, "Vat li", "LY", 30, 45, 30);
        Part p3 = TestExam.choicePart("p3", 3, "Mon tu chon", 20, 30, List.of(
                TestExam.option("o-su", "SUBJECT", "SU", "Lich su", null, null, List.of(TestExam.section("SU", 20)))));
        ServingPath latest = TestExam.path("default", 2, 50, 50, 75, "p1", "Toan hoc");
        ServingPath older = new ServingPath("o-su", 1,
                List.of(new PathChoice("p3", "o-su", "SUBJECT", "SU", "Lich su")), 100, BigDecimal.valueOf(100), 150,
                List.of(new ServingPathPart("p1", "Toan hoc", 50, BigDecimal.valueOf(50), 75),
                        new ServingPathPart("p2", "Vat li", 30, BigDecimal.valueOf(30), 45),
                        new ServingPathPart("p3", "Mon tu chon", 20, BigDecimal.valueOf(20), 30)), List.of());
        TestExam exam = exam().withParts(List.of(p1), List.of(latest, older)).retain(1, List.of(p1, p2, p3)).version(2)
                .named("Ten phien ban 2").price(300_000L);
        sell(exam);
        String ip = newClientIp();

        JsonNode detail = body(publicGet(BASE + "/products/" + exam.code, ip).andExpect(status().isOk()).andReturn());
        assertThat(detail.path("name").asText()).as("name, price and version come from the latest").isEqualTo("Ten phien ban 2");
        assertThat(detail.path("listPrice").asLong()).isEqualTo(300_000L);
        assertThat(detail.path("versionNo").asInt()).isEqualTo(2);
        List<String> partNames = new ArrayList<>();
        detail.path("parts").forEach(p -> partNames.add(p.path("name").asText()));
        assertThat(partNames).as("the part only v1 has is not lost").containsExactly("Toan hoc", "Vat li", "Mon tu chon");
        assertThat(detail.path("choices")).hasSize(1);
        assertThat(detail.path("choices").get(0).path("partName").asText()).isEqualTo("Mon tu chon");
        assertThat(detail.path("choices").get(0).path("options").get(0).path("code").asText()).isEqualTo("SU");
        for (String subject : List.of("TOAN", "LY", "SU")) {
            assertThat(codes(body(publicGet(BASE + "/products?subject=" + subject + "&q=" + exam.code, ip).andReturn())))
                    .as("subject %s of a retained version", subject).containsExactly(exam.code);
        }
    }

    @Test
    void retainedVersionContributesSubjectsToAnExistingChoice() throws Exception {
        Part newerPart = TestExam.withBlocks(TestExam.choicePart("p", 1, "Khoa hoc", 25, 30, List.of(
                TestExam.option("science", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc", 1, List.of(25),
                        List.of(TestExam.section("HOA", 25))))),
                List.of(TestExam.optionBlock("science", "sec-HOA", "SINGLE_CHOICE", 25)));
        Part olderPart = TestExam.withBlocks(TestExam.choicePart("p", 1, "Khoa hoc", 25, 30, List.of(
                TestExam.option("science", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc", 1, List.of(25),
                        List.of(TestExam.section("LY", 25))))),
                List.of(TestExam.optionBlock("science", "sec-LY", "SHORT_ANSWER", 25)));
        ServingPath newer = TestExam.optionPath("new", 2, 25, 25, 30, "p", "Khoa hoc",
                new PathChoice("p", "science", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc"));
        ServingPath older = TestExam.optionPath("old", 1, 25, 25, 30, "p", "Khoa hoc",
                new PathChoice("p", "science", "SUBJECT_CHOICE", "SCIENCE", "Khoa hoc"));
        TestExam exam = exam().withParts(List.of(newerPart), List.of(newer, older))
                .retain(1, List.of(olderPart)).version(2);
        sell(exam);

        JsonNode detail = body(publicGet(BASE + "/products/" + exam.code, newClientIp())
                .andExpect(status().isOk()).andReturn());
        JsonNode options = detail.path("choices").get(0).path("options");
        assertThat(options).hasSize(1);
        List<String> subjects = new ArrayList<>();
        options.get(0).path("subjects").forEach(s -> subjects.add(s.path("code").asText()));
        assertThat(subjects).containsExactly("HOA", "LY");
        JsonNode perSubject = options.get(0).path("subjectAnswerTypes");
        assertThat(perSubject).hasSize(2);
        assertThat(typeRows(perSubject.get(0).path("answerTypes"))).containsExactly("SINGLE_CHOICE:25");
        assertThat(typeRows(perSubject.get(1).path("answerTypes"))).containsExactly("SHORT_ANSWER:25");
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
        exam.withBlocks(List.of(
                TestExam.block("s-p1", "SINGLE_CHOICE", 32),
                TestExam.block("s-p1", "SHORT_ANSWER", 15),
                TestExam.setBlock("s-p1", 3, "SINGLE_CHOICE")));
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
        // The answer types of part 1, merged from its frozen blocks (TM 10.1); part 3 has none in this fixture.
        JsonNode answerTypes = parts.get(0).path("answerTypes");
        assertThat(answerTypes).hasSize(3);
        assertThat(answerTypes.get(0).path("questionType").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(answerTypes.get(0).path("questionCount").asInt()).isEqualTo(32);
        assertThat(answerTypes.get(1).path("questionType").asText()).isEqualTo("SHORT_ANSWER");
        assertThat(answerTypes.get(2).path("questionType").asText()).isEqualTo("QUESTION_SET");
        assertThat(answerTypes.get(2).path("childAnswerType").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(parts.get(1).path("answerTypes")).isEmpty();
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

    /** R1-10: only GET is open on the public prefix; any other method needs a login (401, not MVC's 405). */
    @Test
    void publicPrefixOpensGetOnly() throws Exception {
        String ip = newClientIp();
        String path = BASE + "/filters";

        publicGet(path, ip).andExpect(status().isOk());
        mockMvc.perform(post(path).header("X-Forwarded-For", ip)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(path).header("X-Forwarded-For", ip)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(path).header("X-Forwarded-For", ip)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(path).header("X-Forwarded-For", ip)).andExpect(status().isUnauthorized());
        mockMvc.perform(head(path).header("X-Forwarded-For", ip)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/products").header("X-Forwarded-For", ip)).andExpect(status().isUnauthorized());
        // The protected route stays protected, as before.
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
