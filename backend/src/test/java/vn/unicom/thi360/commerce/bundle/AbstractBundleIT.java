package vn.unicom.thi360.commerce.bundle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.pricing.CommercePricingProperties;
import vn.unicom.thi360.commerce.storefront.FakePublishedExamPort;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.exam.spi.PublishedExamPort.BlockSummary;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Listing;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PartPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

/**
 * Shared integration base for {@code commerce/bundle} tests using a controllable {@link FakePublishedExamPort}.
 */
@Import(AbstractBundleIT.FakePortConfig.class)
abstract class AbstractBundleIT extends AbstractIntegrationTest {

    protected static final String ADMIN_BASE = "/api/v1/admin/bundles";
    protected static final String STOREFRONT_BASE = "/api/v1/public/storefront";

    @TestConfiguration
    static class FakePortConfig {
        @Bean
        @Primary
        FakePublishedExamPort fakePublishedExamPort() {
            return new FakePublishedExamPort();
        }
    }

    @Autowired protected FakePublishedExamPort port;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected CatalogLookup catalog;
    @Autowired protected OutboxPublisher outbox;
    @Autowired protected OutboxRelay relay;
    @Autowired protected PlatformTransactionManager transactionManager;
    @Autowired protected CommercePricingProperties pricingProperties;

    private final Set<UUID> trackedBundleIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> trackedExamIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> trackedActorIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanBundleFixtures() {
        for (UUID bundleId : trackedBundleIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE bundle_id = ?", bundleId);
            jdbc.update("DELETE FROM commerce.bundle_version WHERE bundle_id = ?", bundleId);
            jdbc.update("DELETE FROM commerce.bundle_item WHERE bundle_id = ?", bundleId);
            jdbc.update("DELETE FROM commerce.bundle WHERE id = ?", bundleId);
        }
        trackedBundleIds.clear();

        for (UUID examId : trackedExamIds) {
            port.withdraw(examId);
            port.recover(examId);
            jdbc.update("DELETE FROM shared.outbox_event WHERE topic IN (?, ?) AND payload ->> 'examId' = ?",
                    ExamEventTopics.PUBLISHED, ExamEventTopics.DISCONTINUED, examId.toString());
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        trackedExamIds.clear();

        for (UUID actorId : trackedActorIds) {
            jdbc.update("DELETE FROM shared.outbox_event WHERE topic = 'audit' AND payload ->> 'actorId' = ?",
                    actorId.toString());
        }
        trackedActorIds.clear();
    }

    protected UUID trackBundle(UUID bundleId) {
        trackedBundleIds.add(bundleId);
        return bundleId;
    }

    protected UUID trackActor() {
        UUID id = UUID.randomUUID();
        trackedActorIds.add(id);
        return id;
    }

    protected UUID kindItem(String code) {
        return catalog.findActiveItem("EXAM", code).orElseThrow().id();
    }

    protected UUID subjectItem(String code) {
        return catalog.findActiveItem("SUBJECT", code).orElseThrow().id();
    }

    protected BundleExamBuilder partExam(String kindCode, String subjectCode) {
        BundleExamBuilder b = new BundleExamBuilder();
        b.kindCode = kindCode;
        b.kindItemId = kindItem(kindCode);
        b.scope = "PART";
        b.partCode = "P" + b.partOrder;
        b.subjectCode = subjectCode;
        b.subjectItemId = null;
        return b;
    }

    protected BundleExamBuilder subjectExam(String kindCode, String subjectCode) {
        BundleExamBuilder b = new BundleExamBuilder();
        b.kindCode = kindCode;
        b.kindItemId = kindItem(kindCode);
        b.scope = "SUBJECT";
        b.partCode = null;
        b.subjectCode = subjectCode;
        b.subjectItemId = subjectItem(subjectCode);
        return b;
    }

    protected BundleExamBuilder fullExam(String kindCode, String subjectCode) {
        BundleExamBuilder b = new BundleExamBuilder();
        b.kindCode = kindCode;
        b.kindItemId = kindItem(kindCode);
        b.scope = "FULL";
        b.partCode = null;
        b.subjectCode = subjectCode;
        b.subjectItemId = null;
        return b;
    }

    protected void serve(BundleExamBuilder exam) {
        trackedExamIds.add(exam.examId);
        port.serve(exam.build(), List.of());
    }

    protected UUID emitExamEvent(String topic, UUID examId, int versionNo) {
        trackedExamIds.add(examId);
        UUID eventId = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(
                s -> outbox.publish(eventId, topic, new ExamEventTopics.Payload(examId, versionNo)));
        drainOutbox();
        return eventId;
    }

    protected void drainOutbox() {
        for (int i = 0; i < 50; i++) {
            if (relay.relayBatch(500) == 0) {
                return;
            }
        }
    }

    protected RequestPostProcessor bundleAdmin() {
        UUID actor = trackActor();
        return asUser(actor, "bundle.admin",
                Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH);
    }

    protected RequestPostProcessor bundleAdminAs(UUID actorId) {
        trackedActorIds.add(actorId);
        return asUser(actorId, "bundle.admin." + actorId.toString().substring(0, 6),
                Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH);
    }

    protected Map<String, Object> createPayload(String code, String name, UUID examKindItemId, List<UUID> examIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("summary", "Tóm tắt " + name);
        body.put("description", "Mô tả chi tiết cho bộ đề " + name);
        body.put("examKindItemId", examKindItemId);
        body.put("listPriceVnd", 150_000L);
        body.put("attemptsPerExam", 3);
        body.put("benefitAiExplanation", true);
        body.put("benefitStrengthWeakness", true);
        body.put("benefitPractice", false);
        body.put("benefitNote", "Hỗ trợ giải thích AI");
        body.put("resultView", "FULL_SOLUTION");
        body.put("examIds", examIds);
        return body;
    }

    protected JsonNode createBundle(Map<String, Object> payload, RequestPostProcessor auth) throws Exception {
        String response = mockMvc.perform(post(ADMIN_BASE)
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(response);
        trackBundle(UUID.fromString(json.path("id").asText()));
        return json;
    }

    protected ResultActions performCreate(Map<String, Object> payload, RequestPostProcessor auth) throws Exception {
        var result = mockMvc.perform(post(ADMIN_BASE)
                .with(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)));
        String body = result.andReturn().getResponse().getContentAsString();
        try {
            JsonNode json = mapper.readTree(body);
            if (json.hasNonNull("id") && result.andReturn().getResponse().getStatus() < 300) {
                trackBundle(UUID.fromString(json.path("id").asText()));
            }
        } catch (Exception ignored) {
            // error response
        }
        return result;
    }

    protected ResultActions performUpdate(UUID bundleId, Map<String, Object> payload, RequestPostProcessor auth)
            throws Exception {
        return mockMvc.perform(put(ADMIN_BASE + "/" + bundleId)
                .with(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload)));
    }

    protected ResultActions performPublish(UUID bundleId, Long expectedRevision, RequestPostProcessor auth)
            throws Exception {
        MockHttpServletRequestBuilder req = post(ADMIN_BASE + "/" + bundleId + "/publish").with(auth);
        if (expectedRevision != null) {
            req.contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("expectedRevision", expectedRevision)));
        }
        return mockMvc.perform(req);
    }

    protected ResultActions performDiscontinue(UUID bundleId, Long expectedRevision, RequestPostProcessor auth)
            throws Exception {
        MockHttpServletRequestBuilder req = post(ADMIN_BASE + "/" + bundleId + "/discontinue").with(auth);
        if (expectedRevision != null) {
            req.contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("expectedRevision", expectedRevision)));
        }
        return mockMvc.perform(req);
    }

    protected static String newClientIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "198.19." + random.nextInt(1, 255) + "." + random.nextInt(1, 255);
    }

    protected static String shortToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /** Builder for fake served exams used in bundle integration tests. */
    static final class BundleExamBuilder {
        final UUID examId = UUID.randomUUID();
        final int partOrder = 5000 + ThreadLocalRandom.current().nextInt(100_000);
        String code = "EX-" + shortToken();
        String name = "Bài thi " + code;
        String kindCode = "HSA";
        UUID kindItemId;
        String scope = "PART";
        String partCode = "P1";
        String subjectCode = "TOAN";
        UUID subjectItemId;
        String buildMode = "STANDARD";
        int versionNo = 1;
        String status = "PUBLISHED";
        Long listPrice = 79_000L;
        Integer attempts = 3;
        int questionCount = 25;
        int durationMinutes = 40;
        int maxPoints = 50;
        Instant publishedAt = Instant.parse("2026-11-10T00:00:00Z");

        BundleExamBuilder code(String code) {
            this.code = code;
            return this;
        }

        BundleExamBuilder named(String name) {
            this.name = name;
            return this;
        }

        BundleExamBuilder review() {
            this.buildMode = "REVIEW";
            return this;
        }

        BundleExamBuilder version(int versionNo) {
            this.versionNo = versionNo;
            return this;
        }

        BundleExamBuilder price(Long listPrice) {
            this.listPrice = listPrice;
            return this;
        }

        BundleExamBuilder metrics(int questionCount, int durationMinutes, int maxPoints) {
            this.questionCount = questionCount;
            this.durationMinutes = durationMinutes;
            this.maxPoints = maxPoints;
            return this;
        }

        BundleExamBuilder discontinued() {
            this.status = "DISCONTINUED";
            return this;
        }

        BundleExamBuilder nextVersion(int versionNo, String name, int questionCount, int durationMinutes, int maxPoints) {
            this.versionNo = versionNo;
            this.name = name;
            this.questionCount = questionCount;
            this.durationMinutes = durationMinutes;
            this.maxPoints = maxPoints;
            return this;
        }

        ServingExam build() {
            Part part = new Part(
                    "p1",
                    partOrder,
                    "Phần " + partOrder,
                    durationMinutes,
                    questionCount,
                    questionCount,
                    questionCount,
                    "ALL",
                    null,
                    BigDecimal.valueOf(maxPoints),
                    List.of(),
                    List.of(new Section("s-p1", subjectCode, questionCount, "ANY")),
                    List.of(),
                    List.of(),
                    List.of(new PartPath("default", questionCount, BigDecimal.valueOf(maxPoints), durationMinutes)),
                    List.<BlockSummary>of());
            ServingPath path = new ServingPath(
                    "default",
                    versionNo,
                    List.of(),
                    questionCount,
                    BigDecimal.valueOf(maxPoints),
                    durationMinutes,
                    List.of(new ServingPathPart("p1", "Phần " + partOrder, questionCount,
                            BigDecimal.valueOf(maxPoints), durationMinutes)),
                    List.of());
            Listing listing = new Listing(
                    "Tóm tắt", "Mục tiêu", "Đối tượng", listPrice, attempts,
                    true, false, true, "Ghi chú", "SCORE_AND_ANSWERS", "Mô tả", null);
            PublishedVersion version = new PublishedVersion(
                    examId,
                    versionNo,
                    "SERVING",
                    status,
                    publishedAt,
                    code,
                    name,
                    kindItemId,
                    kindCode,
                    scope,
                    partCode,
                    subjectItemId,
                    buildMode,
                    "PER_PART",
                    null,
                    BigDecimal.valueOf(maxPoints),
                    null,
                    "LINEAR",
                    listing,
                    List.of(part),
                    List.of(),
                    List.of(),
                    List.of("default"),
                    List.of("default"));
            return new ServingExam(examId, status, version, List.of(path));
        }
    }
}
