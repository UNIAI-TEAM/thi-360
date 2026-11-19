package vn.unicom.thi360.exam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Working copy of the sale listing of an exam (TM 6.1); one row per exam, created by the first save. */
@Entity
@Table(name = "exam_listing", schema = "exam")
public class ExamListing {
    @Id
    @Column(name = "exam_id")
    private UUID examId;
    private String summary;
    private String objectives;
    private String audience;
    @Column(name = "list_price_vnd")
    private Long listPriceVnd;
    @Column(name = "attempts_granted")
    private Integer attemptsGranted;
    @Column(name = "benefit_ai_explanation", nullable = false)
    private boolean benefitAiExplanation;
    @Column(name = "benefit_strength_weakness", nullable = false)
    private boolean benefitStrengthWeakness;
    @Column(name = "benefit_practice", nullable = false)
    private boolean benefitPractice;
    @Column(name = "benefit_note")
    private String benefitNote;
    @Column(name = "result_view", nullable = false)
    private String resultView = ResultView.SCORE_AND_ANSWERS.name();
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** What a student may see after grading (TM C16). */
    public enum ResultView { SCORE, SCORE_AND_ANSWERS, FULL_SOLUTION }

    /** The editable columns, as one value so a save replaces them together. */
    public record Fields(String summary, String objectives, String audience, Long listPriceVnd,
                         Integer attemptsGranted, boolean benefitAiExplanation, boolean benefitStrengthWeakness,
                         boolean benefitPractice, String benefitNote, ResultView resultView) { }

    protected ExamListing() {
    }

    public ExamListing(UUID examId, Instant now) {
        this.examId = examId;
        this.updatedAt = now;
    }

    public void replace(Fields fields, UUID actorId, Instant now) {
        this.summary = fields.summary();
        this.objectives = fields.objectives();
        this.audience = fields.audience();
        this.listPriceVnd = fields.listPriceVnd();
        this.attemptsGranted = fields.attemptsGranted();
        this.benefitAiExplanation = fields.benefitAiExplanation();
        this.benefitStrengthWeakness = fields.benefitStrengthWeakness();
        this.benefitPractice = fields.benefitPractice();
        this.benefitNote = fields.benefitNote();
        this.resultView = fields.resultView().name();
        this.updatedBy = actorId;
        this.updatedAt = now;
    }

    /** A copy of another exam's listing (duplicate); the editable columns only. */
    public void copyFrom(ExamListing source, UUID actorId, Instant now) {
        replace(source.fields(), actorId, now);
    }

    public Fields fields() {
        return new Fields(summary, objectives, audience, listPriceVnd, attemptsGranted, benefitAiExplanation,
                benefitStrengthWeakness, benefitPractice, benefitNote, ResultView.valueOf(resultView));
    }

    public UUID getExamId() { return examId; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
