package vn.unicom.thi360.commerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

/**
 * CM-1 AC-23: with a set of published exams in the exam module, {@code products} returns every exam on sale and none that
 * was discontinued or is still a draft. This runs over the REAL {@code PublishedExamPort}, event handlers and API.
 *
 * <p>The DS builders ({@code exam/devdata}) are not merged yet, so the set is written by hand into the exam tables in the
 * shape EX-1 publishes (frozen config and parts). It reruns on the DS bundle once DS is merged (see the CM-1 report).
 */
class StorefrontDataSetIT extends AbstractIntegrationTest {

    private static final String PRODUCTS = "/api/v1/public/storefront/products";

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CatalogLookup catalog;
    @Autowired OutboxPublisher outbox;
    @Autowired OutboxRelay relay;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<UUID> examIds = new ArrayList<>();
    private final String tag = "ds" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @AfterEach
    void clean() {
        for (UUID id : examIds) {
            jdbc.update("DELETE FROM shared.outbox_event WHERE topic IN (?, ?) AND payload ->> 'examId' = ?",
                    ExamEventTopics.PUBLISHED, ExamEventTopics.DISCONTINUED, id.toString());
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", id);
            jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ?", id);
            jdbc.update("DELETE FROM exam.exam WHERE id = ?", id);
        }
    }

    @Test
    void productsAreTheOnSaleExamsOfTheDataSet() throws Exception {
        Map<String, UUID> onSale = new LinkedHashMap<>();
        onSale.put("hsaFull", exam("HSA", "FULL", null, null, "STANDARD", "PUBLISHED", 1, 150_000L));
        onSale.put("hsaPart", exam("HSA", "PART", "TOAN", null, "STANDARD", "PUBLISHED", 1, 90_000L));
        onSale.put("hsaReview", exam("HSA", "SUBJECT", null, "TOAN", "REVIEW", "PUBLISHED", 1, 50_000L));
        onSale.put("qdaFull", exam("QDA", "FULL", null, null, "STANDARD", "PUBLISHED", 1, 180_000L));
        onSale.put("sp2eSubject", exam("SP2E", "SUBJECT", null, "VAN", "STANDARD", "PUBLISHED", 1, 70_000L));
        UUID twoVersions = exam("BCA", "FULL", null, null, "STANDARD", "PUBLISHED", 2, 200_000L);
        onSale.put("bcaTwoVersions", twoVersions);
        UUID discontinuedHsa = exam("HSA", "FULL", null, null, "STANDARD", "DISCONTINUED", 1, 150_000L);
        UUID discontinuedBca = exam("BCA", "FULL", null, null, "STANDARD", "DISCONTINUED", 1, 160_000L);
        UUID draft = draft("HSA");

        // The way production does it: an event per exam, relayed to the handler.
        for (UUID id : examIds) {
            if (!id.equals(draft)) {
                String topic = id.equals(discontinuedHsa) || id.equals(discontinuedBca)
                        ? ExamEventTopics.DISCONTINUED : ExamEventTopics.PUBLISHED;
                new TransactionTemplate(transactionManager).executeWithoutResult(
                        status -> outbox.publish(topic, new ExamEventTopics.Payload(id, 1)));
            }
        }
        for (int round = 0; round < 50 && relay.relayBatch(500) > 0; round++) {
            // drain
        }

        JsonNode page = json(mockMvc.perform(get(PRODUCTS).param("q", tag).param("size", "48")
                        .header("X-Forwarded-For", "198.18." + (int) (Math.random() * 250 + 1) + ".7"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> listed = new ArrayList<>();
        page.path("items").forEach(item -> listed.add(item.path("code").asText()));

        List<String> expected = onSale.values().stream().map(this::codeOf).toList();
        assertThat(listed).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(listed).doesNotContain(codeOf(discontinuedHsa), codeOf(discontinuedBca), codeOf(draft));
        assertThat(page.path("totalItems").asLong()).isEqualTo(onSale.size());
        // The review exam keeps the label of the exam module; the exam with two versions is sold at the serving one.
        for (JsonNode item : page.path("items")) {
            if (item.path("code").asText().equals(codeOf(onSale.get("hsaReview")))) {
                assertThat(item.path("buildMode").asText()).isEqualTo("REVIEW");
                assertThat(item.path("format").asText()).isEqualTo("SUBJECT");
            }
            if (item.path("code").asText().equals(codeOf(twoVersions))) {
                assertThat(item.path("versionNo").asInt()).isEqualTo(2);
                assertThat(item.path("listPrice").asLong()).isEqualTo(200_000L);
            }
        }

        // The rebuild reproduces exactly this set and changes nothing.
        String before = jdbc.queryForObject("SELECT string_agg(to_jsonb(p)::text, ',' ORDER BY code) "
                + "FROM commerce.storefront_product p WHERE name LIKE ?", String.class, "%" + tag + "%");
        mockMvc.perform(post("/api/v1/admin/storefront/rebuild").with(asUser(Permissions.EXAM_PUBLISH)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.failed").value(0));
        String after = jdbc.queryForObject("SELECT string_agg(to_jsonb(p)::text, ',' ORDER BY code) "
                + "FROM commerce.storefront_product p WHERE name LIKE ?", String.class, "%" + tag + "%");
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.storefront_product WHERE exam_id = ?", Long.class,
                draft)).as("no row for a draft").isZero();
    }

    // ----------------------------------------------------------------- fixture

    private String codeOf(UUID examId) {
        return jdbc.queryForObject("SELECT code FROM exam.exam WHERE id = ?", String.class, examId);
    }

    private UUID kindItem(String code) {
        return catalog.findActiveItem("EXAM", code).orElseThrow().id();
    }

    private UUID draft(String kindCode) {
        UUID id = UUID.randomUUID();
        examIds.add(id);
        String code = (tag + "-draft").toUpperCase();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing, status) "
                + "VALUES (?, ?, ?, ?, ?, 'FULL', 'PER_PART', 'DRAFT')", id, code, code.toLowerCase(),
                "Ban nhap " + tag, kindItem(kindCode));
        return id;
    }

    /** An exam of the exam module with its frozen versions: the last one serves (or is discontinued). */
    private UUID exam(String kindCode, String scope, String partCode, String subjectCode, String buildMode, String status,
            int versions, long price) throws Exception {
        UUID id = UUID.randomUUID();
        examIds.add(id);
        String code = (tag + "-" + examIds.size()).toUpperCase();
        UUID kind = kindItem(kindCode);
        UUID subject = subjectCode == null ? null : catalog.findActiveItem("SUBJECT", subjectCode).orElseThrow().id();
        String name = "Bai " + kindCode + " " + scope + " " + tag;
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, part_code, "
                + "subject_item_id, timing, status, serving_version, build_mode) VALUES (?, ?, ?, ?, ?, ?, ?, ?, "
                + "'PER_PART', ?, ?, ?)", id, code, code.toLowerCase(), name, kind, scope, partCode, subject, status,
                versions, buildMode);
        for (int version = 1; version <= versions; version++) {
            boolean last = version == versions;
            long versionPrice = last ? price : price / 2;
            jdbc.update("INSERT INTO exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, "
                            + "serving_path_ids, check_run_id, state, status, published_at, published_by, "
                            + "discontinued_at, discontinued_by) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, "
                            + "'{default}'::text[], ?::text[], ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), id, version, config(code, name, kind, kindCode, scope, partCode, subject,
                            buildMode, versionPrice).toString(), parts(subjectCode == null ? "TOAN" : subjectCode)
                            .toString(),
                    last ? "{default}" : "{}", UUID.randomUUID(), last ? "SERVING" : "RETIRED",
                    last ? status : "PUBLISHED",
                    Timestamp.from(Instant.parse("2026-11-01T00:00:00Z").plusSeconds(examIds.size())),
                    UUID.randomUUID(), last && "DISCONTINUED".equals(status)
                            ? Timestamp.from(Instant.parse("2026-11-10T00:00:00Z")) : null,
                    last && "DISCONTINUED".equals(status) ? UUID.randomUUID() : null);
        }
        return id;
    }

    private ObjectNode config(String code, String name, UUID kind, String kindCode, String scope, String partCode,
            UUID subject, String buildMode, long price) {
        ObjectNode config = mapper.createObjectNode().put("code", code).put("name", name).put("kindCode", kindCode)
                .put("scope", scope).put("timing", "PER_PART").put("maxScore", 150).put("practiceScoring", "LINEAR")
                .put("buildMode", buildMode);
        config.put("kindItemId", kind.toString());
        if (partCode != null) {
            config.put("partCode", partCode);
        }
        if (subject != null) {
            config.put("subjectItemId", subject.toString());
        }
        config.putObject("listing").put("summary", "Tom tat").put("objectives", "Muc tieu").put("audience", "Doi tuong")
                .put("listPriceVnd", price).put("attemptsGranted", 3).put("benefitAiExplanation", true)
                .put("benefitStrengthWeakness", false).put("benefitPractice", true).put("resultView", "SCORE_AND_ANSWERS");
        config.putArray("combinations");
        config.putArray("omittedGroups");
        return config;
    }

    private ObjectNode parts(String subjectCode) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode parts = root.putArray("parts");
        ObjectNode part = parts.addObject().put("id", "p1").put("order", 1).put("name", "Phan 1")
                .put("durationMinutes", 75).put("questionCount", 50).put("questionCountOnPaper", 50)
                .put("questionCountRequired", 50).put("maxPoints", 50);
        part.putObject("participation").put("mode", "ALL");
        part.putArray("sections").addObject().put("id", "s1").put("subjectCode", subjectCode).put("questionCount", 50);
        part.putArray("paths").addObject().put("pathId", "default").put("questionCount", 50).put("maxPoints", 50)
                .put("durationMinutes", 75);
        return root;
    }

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }
}
