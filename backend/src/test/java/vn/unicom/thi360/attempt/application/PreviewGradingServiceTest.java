package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import vn.unicom.thi360.attempt.api.PreviewGradeRequest;
import vn.unicom.thi360.attempt.api.PreviewGradeResponse;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicies;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicy;
import vn.unicom.thi360.exam.spi.PreviewPaperPort;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Block;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Item;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Part;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.PreviewPaper;
import vn.unicom.thi360.exam.spi.PreviewPaperPort.Question;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class PreviewGradingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void gradePassesExactPreviewKindCodeToGradingPoliciesForKind() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID hscaPreviewId = UUID.randomUUID();
        UUID vsatPreviewId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        PreviewPaperPort previewPaperPort = mock(PreviewPaperPort.class);
        QuestionSourcePort questionSourcePort = mock(QuestionSourcePort.class);
        AnswerValueValidator answerValueValidator = mock(AnswerValueValidator.class);

        JsonNode mcSnapshot = objectMapper.readTree("""
                {
                  "content": "<p>Multiple choice</p>",
                  "points": 2.0,
                  "body": {
                    "type": "MULTIPLE_CHOICE",
                    "scoringRule": "PARTIAL",
                    "options": [
                      {"id": "o1", "content": "A", "correct": true},
                      {"id": "o2", "content": "B", "correct": true},
                      {"id": "o3", "content": "C", "correct": false}
                    ]
                  }
                }
                """);
        VersionKey key = new VersionKey(questionId, 1);
        when(questionSourcePort.loadVersions(anyList()))
                .thenReturn(List.of(new Version(key, mcSnapshot)));

        AnswerValue.MultipleChoice submitted = new AnswerValue.MultipleChoice(List.of("o1"));
        when(answerValueValidator.validate(eq(QuestionType.MULTIPLE_CHOICE), eq(mcSnapshot), eq(submitted)))
                .thenReturn(AnswerValueValidator.ValidationResult.valid(submitted));

        PreviewPaper hscaPaper = previewPaper(examId, "HSCA", questionId);
        PreviewPaper vsatPaper = previewPaper(examId, "VSAT", questionId);
        when(previewPaperPort.load(examId, hscaPreviewId)).thenReturn(hscaPaper);
        when(previewPaperPort.load(examId, vsatPreviewId)).thenReturn(vsatPaper);

        PreviewGradingService service =
                new PreviewGradingService(previewPaperPort, questionSourcePort, answerValueValidator);
        PreviewGradeRequest request = new PreviewGradeRequest(Map.of(questionId, submitted));

        try (MockedStatic<GradingPolicies> policies = mockStatic(GradingPolicies.class)) {
            policies.when(() -> GradingPolicies.forKind(any())).thenAnswer(inv -> {
                String kind = inv.getArgument(0);
                if (kind == null || kind.isBlank()) {
                    throw new IllegalArgumentException("kindCode must be passed explicitly");
                }
                return GradingPolicy.standard();
            });

            PreviewGradeResponse hscaResponse = service.grade(examId, hscaPreviewId, request);
            PreviewGradeResponse vsatResponse = service.grade(examId, vsatPreviewId, request);

            policies.verify(() -> GradingPolicies.forKind("HSCA"));
            policies.verify(() -> GradingPolicies.forKind("VSAT"));
            policies.verifyNoMoreInteractions();

            assertThat(hscaResponse.kindCode()).isEqualTo("HSCA");
            assertThat(hscaResponse.rawScore()).isEqualByComparingTo("1.00");
            assertThat(hscaResponse.items()).hasSize(1);
            assertThat(hscaResponse.items().getFirst().outcome()).isEqualTo(ItemOutcome.PARTIAL);

            assertThat(vsatResponse.kindCode()).isEqualTo("VSAT");
            assertThat(vsatResponse.rawScore()).isEqualByComparingTo("1.00");
        }
    }

    private static PreviewPaper previewPaper(UUID examId, String kindCode, UUID questionId) {
        Question question = new Question(
                1,
                questionId,
                "Q-1",
                1,
                "MULTIPLE_CHOICE",
                BigDecimal.valueOf(2.0),
                List.of("o1", "o2", "o3"),
                List.of());
        Block block = new Block(
                "b1",
                "MC",
                "TOAN",
                "MULTIPLE_CHOICE",
                false,
                BigDecimal.valueOf(2.0),
                1,
                1,
                1,
                List.of(new Item(Item.Kind.QUESTION, question, null)));
        Part part = new Part(
                "p1",
                "Part 1",
                1,
                30,
                1,
                1,
                BigDecimal.valueOf(2.0),
                List.of(block),
                List.of());
        return new PreviewPaper(examId, 1, 1L, kindCode, "SHARED", 30, List.of(part));
    }
}
