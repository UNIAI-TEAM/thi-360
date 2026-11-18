package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.api.ExamOverviewRequest;
import vn.unicom.thi360.exam.domain.AudioRequirement;
import vn.unicom.thi360.exam.domain.ChildCondition;
import vn.unicom.thi360.exam.domain.DisplaySegment;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ExamStructureOps;
import vn.unicom.thi360.exam.domain.ExamStructureValidator;
import vn.unicom.thi360.exam.domain.ItemShape;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * BE-16 row 4: ONE valid SUBJECT structure carrying every new field is passed through the stages in order,
 * each stage consuming the previous stage's output: {@code ExamOverview.apply}, {@code ExamStructureOps}, save
 * (PUT), read back, duplicate, check draft inputs and the preview target. After every stage the audio condition,
 * the typed child conditions (answerType, itemShape.options) and the display segments must equal the originals.
 */
@Transactional
class ExamStructureFieldChainIT extends AbstractIntegrationTest {
    private static final List<DisplaySegment> SEGMENTS = List.of(
            new DisplaySegment("P1", "Phần I", 1, 8, 8, new BigDecimal("80")),
            new DisplaySegment("P2", "Phần II", 9, 15, 7, new BigDecimal("70")));
    private static final List<ChildCondition> CONDITIONS = List.of(
            new ChildCondition(1, 2, List.of(), QuestionType.TRUE_FALSE, new ItemShape(4, null, null, null)),
            new ChildCondition(3, 8, List.of(), QuestionType.SINGLE_CHOICE, new ItemShape(null, null, null, 4)));
    private static final ItemShape BLOCK_SHAPE = new ItemShape(null, null, null, 4);

    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void oneValidSubjectStructureKeepsEveryNewFieldThroughEveryStage() throws Exception {
        ExamStructureCodec codec = new ExamStructureCodec(mapper);
        ExamStructureDocument start = structure();
        ExamStructureValidator.validate(start);
        assertIntact(start, "fixture", false);

        ExamStructureDocument overview = ExamOverview.apply(start, List.of(
                new ExamOverviewRequest.Row(start.parts().getFirst().id(), null, null, null, null, 90, null)));
        assertThat(overview.parts().getFirst().durationMinutes()).isEqualTo(90);
        assertIntact(overview, "overview", false);

        ExamStructureDocument ops = ExamStructureOps.restoreTemplateOwned(
                ExamStructureOps.assignMissingIds(overview), overview, null);
        assertIntact(ops, "structure ops", false);

        String examUrl = "/api/v1/admin/exams/" + createSubjectExam();
        long revision = load(examUrl).path("revision").asLong();
        ObjectNode request = mapper.createObjectNode().put("expectedRevision", revision);
        JsonNode written = mapper.readTree(codec.writeString(ops));
        request.set("parts", written.path("parts"));
        request.set("examQuotas", written.path("examQuotas"));
        String saved = mockMvc.perform(put(examUrl + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertIntact(readDocument(codec, mapper.readTree(saved)), "save response", false);

        ExamStructureDocument readBack = readDocument(codec, load(examUrl));
        assertIntact(readBack, "read back after save", false);

        verifyDownstreamStages(codec, examUrl, readBack);
    }

    /**
     * Row 4 entry point: a real create. A default SUBJECT template fixture carries the full structure; the POST
     * goes through {@code ExamService.createExam} where the template is loaded and the overview applied
     * ({@code ExamOverview.apply}), so a field dropped there would not reach the stored structure.
     */
    @Test
    void createdSubjectExamFromDefaultTemplateKeepsEveryNewFieldAfterTheOverview() throws Exception {
        ExamStructureCodec codec = new ExamStructureCodec(mapper);
        ExamStructureDocument fixture = structure();
        ExamStructureValidator.validate(fixture);
        String templateCode = "CHAIN_SPT_TOAN_" + UUID.randomUUID().toString().substring(0, 8);
        String partId = fixture.parts().getFirst().id();
        jdbc.update("UPDATE exam.exam_template SET is_default=false WHERE kind_code='SPT' "
                + "AND scope='SUBJECT' AND subject_code='TOAN' AND build_mode='STANDARD'");
        jdbc.update("""
                INSERT INTO exam.exam_template
                  (code, kind_code, scope, subject_code, timing, duration_minutes, structure,
                   active, build_mode, is_default)
                VALUES (?, 'SPT', 'SUBJECT', 'TOAN', 'PER_PART', 90, ?::jsonb, true, 'STANDARD', true)""",
                templateCode, codec.writeString(fixture));

        String body = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"SPT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\","
                                + "\"name\":\"Chuỗi trường mới tạo\",\"code\":\"CHAINCREATE-"
                                + UUID.randomUUID().toString().substring(0, 8) + "\","
                                + "\"overview\":[{\"partId\":\"" + partId
                                + "\",\"durationMinutes\":90}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode created = mapper.readTree(body);
        assertThat(created.path("templateCode").asText()).isEqualTo(templateCode);
        String examUrl = "/api/v1/admin/exams/" + created.path("id").asText();

        ExamStructureDocument readBack = readDocument(codec, load(examUrl));
        assertThat(readBack.parts().getFirst().durationMinutes())
                .as("overview duration applied at create").isEqualTo(90);
        assertIntact(readBack, "created from template + overview", false);
        verifyDownstreamStages(codec, examUrl, readBack);
    }

    private String createSubjectExam() throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"SPT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\","
                                + "\"name\":\"Chuỗi trường mới\",\"code\":\"CHAIN-" + UUID.randomUUID().toString()
                                .substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("id").asText();
    }

    private void verifyDownstreamStages(ExamStructureCodec codec, String examUrl,
            ExamStructureDocument readBack) throws Exception {
        String duplicate = mockMvc.perform(post(examUrl + "/duplicate").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bản sao\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        ExamStructureDocument copy = readDocument(codec,
                load("/api/v1/admin/exams/" + mapper.readTree(duplicate).path("id").asText()));
        assertThat(copy.parts().getFirst().id()).isNotEqualTo(readBack.parts().getFirst().id());
        assertIntact(copy, "duplicate", false);

        String listening = copy.parts().getFirst().sections().getFirst().id();
        String reading = copy.parts().getFirst().sections().get(1).id();
        assertIntact(ExamCheckDraftInputs.maskSections(copy, Set.of(reading)), "check input: other section masked",
                false);
        assertIntact(DefaultExamChecker.maskInvalidOptions(copy, Set.of()), "check input: options", false);
        PreviewSectionTarget.Selection target = PreviewSectionTarget.select(copy, listening, 8);
        assertThat(target.document().parts().getFirst().sections()).hasSize(1);
        assertIntact(target.document(), "preview target", true);
    }

    private JsonNode load(String examUrl) throws Exception {
        return mapper.readTree(mockMvc.perform(get(examUrl + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ExamStructureDocument readDocument(ExamStructureCodec codec, JsonNode response) {
        ObjectNode document = mapper.createObjectNode();
        document.set("parts", response.path("parts"));
        document.set("examQuotas", response.path("examQuotas"));
        return codec.read(document.toString());
    }

    /** {@code singleSection}: the preview target projects the part onto its selected (first) section. */
    private static void assertIntact(ExamStructureDocument document, String stage, boolean singleSection) {
        ExamPart part = document.parts().getFirst();
        // Points are compared by value: the API serialises 80 as 80.0, which is the same number.
        assertThat(part.displaySegments()).as(stage + ": displaySegments")
                .usingRecursiveFieldByFieldElementComparator(
                        org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration.builder()
                                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class).build())
                .isEqualTo(SEGMENTS);
        List<AudioRequirement> audio = part.sections().stream().map(ExamSection::audioRequirement).toList();
        assertThat(audio).as(stage + ": audioRequirement").isEqualTo(singleSection
                ? List.of(AudioRequirement.REQUIRED)
                : List.of(AudioRequirement.REQUIRED, AudioRequirement.FORBIDDEN));
        QuestionBlock set = part.sections().getFirst().blocks().getFirst();
        assertThat(set.childConditions()).as(stage + ": child conditions (answerType, itemShape)")
                .isEqualTo(CONDITIONS);
        assertThat(set.childConditions()).extracting(condition -> condition.itemShape().options())
                .as(stage + ": child itemShape.options").containsExactly(null, 4);
        assertThat(set.itemShape()).as(stage + ": block itemShape").isEqualTo(BLOCK_SHAPE);
        assertThat(set.childAnswerType()).as(stage + ": uniform child type stays blank").isNull();
    }

    private static ExamStructureDocument structure() {
        QuestionBlock set = new QuestionBlock(UUID.randomUUID().toString(), "Bài đọc dùng chung",
                QuestionType.QUESTION_SET, null, 1, 8, null, null, null, null, false, new BigDecimal("6"),
                List.of(), null, true, false, List.of(), CONDITIONS, BLOCK_SHAPE, List.of(), false, List.of(), null);
        QuestionBlock single = new QuestionBlock(UUID.randomUUID().toString(), "Câu đơn", QuestionType.SINGLE_CHOICE,
                7, null, null, null, null, null, null, false, null, List.of(), null, true, false, List.of(),
                List.of(), null, List.of(), false, List.of(), null);
        ExamSection listening = new ExamSection(UUID.randomUUID().toString(), "TOAN", 8, null, null, null, null,
                List.of(set), List.of(), AudioRequirement.REQUIRED);
        ExamSection reading = new ExamSection(UUID.randomUUID().toString(), "TOAN", 7, null, null, null, null,
                List.of(single), List.of(), AudioRequirement.FORBIDDEN);
        return new ExamStructureDocument(List.of(new ExamPart(UUID.randomUUID().toString(), 1, "Phần I", 60, 15,
                null, null, null, new BigDecimal("150"), List.of(listening, reading), List.of(), List.of(), null,
                SEGMENTS)));
    }
}
