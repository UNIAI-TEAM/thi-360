package vn.unicom.thi360.commerce.storefront;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

/**
 * Base of the storefront ITs that drive the projection with a {@link FakePublishedExamPort}: the test says what the exam
 * module serves, publishes the events itself and relays them with {@code relayBatch()} (the relay worker is off in the
 * test profile). Rows, events and fake data the test made are removed afterwards, so the shared database stays clean.
 */
@Import(AbstractStorefrontIT.FakePortConfig.class)
abstract class AbstractStorefrontIT extends AbstractIntegrationTest {

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

    private final List<UUID> examIds = new ArrayList<>();

    @AfterEach
    void removeStorefrontData() {
        for (UUID examId : examIds) {
            port.withdraw(examId);
            port.recover(examId);
            jdbc.update("DELETE FROM shared.outbox_event WHERE topic IN (?, ?) AND payload ->> 'examId' = ?",
                    ExamEventTopics.PUBLISHED, ExamEventTopics.DISCONTINUED, examId.toString());
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        examIds.clear();
    }

    // ------------------------------------------------------------------ data

    protected UUID kindItem(String code) {
        return catalog.findActiveItem("EXAM", code).orElseThrow().id();
    }

    protected UUID subjectItem(String code) {
        return catalog.findActiveItem("SUBJECT", code).orElseThrow().id();
    }

    /** A FULL exam of the kind, not served yet. */
    protected TestExam exam(String kindCode) {
        TestExam exam = new TestExam();
        exam.kind(kindCode, kindItem(kindCode));
        return exam;
    }

    protected TestExam exam() {
        return exam("HSA");
    }

    /** From now on the exam module serves this; the data is removed after the test. */
    protected void serve(TestExam exam) {
        if (!examIds.contains(exam.examId)) {
            examIds.add(exam.examId);
        }
        port.serve(exam.build());
    }

    // ---------------------------------------------------------------- events

    /** Writes the event to the outbox (committed), without relaying it. */
    protected UUID publishEvent(String topic, UUID examId, int versionNo) {
        if (!examIds.contains(examId)) {
            examIds.add(examId);
        }
        UUID id = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> outbox.publish(id, topic, new ExamEventTopics.Payload(examId, versionNo)));
        return id;
    }

    /** Publishes the event and relays everything that is due. */
    protected UUID emit(String topic, UUID examId, int versionNo) {
        UUID id = publishEvent(topic, examId, versionNo);
        drain();
        return id;
    }

    /** Relays until nothing is due: events of other test classes may be ahead in the queue. */
    protected void drain() {
        for (int round = 0; round < 50; round++) {
            if (relay.relayBatch(500) == 0) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ rows

    protected boolean hasRow(UUID examId) {
        return jdbc.queryForObject("SELECT count(*) FROM commerce.storefront_product WHERE exam_id = ?", Long.class,
                examId) > 0;
    }

    /** The whole stored row as JSON, for "nothing changed" comparisons. */
    protected String rowJson(UUID examId) {
        return jdbc.queryForObject("SELECT to_jsonb(p)::text FROM commerce.storefront_product p WHERE exam_id = ?",
                String.class, examId);
    }

    protected Map<String, Object> row(UUID examId) {
        return jdbc.queryForMap("SELECT * FROM commerce.storefront_product WHERE exam_id = ?", examId);
    }

    protected JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }

    // ------------------------------------------------------------------- http

    /** A fresh client address per call, so tests never share a rate-limit bucket. */
    protected static String newClientIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "198.18." + random.nextInt(1, 255) + "." + random.nextInt(1, 255);
    }

    protected ResultActions publicGet(String path, String clientIp) throws Exception {
        return mockMvc.perform(publicRequest(path, clientIp));
    }

    protected MockHttpServletRequestBuilder publicRequest(String path, String clientIp) {
        return get(path).header("X-Forwarded-For", clientIp);
    }
}
