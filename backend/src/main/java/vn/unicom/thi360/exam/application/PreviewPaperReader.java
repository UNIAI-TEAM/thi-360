package vn.unicom.thi360.exam.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.exam.api.ExamPreviewController.PreviewAttachment;
import vn.unicom.thi360.exam.api.ExamPreviewController.PreviewBlock;
import vn.unicom.thi360.exam.api.ExamPreviewController.PreviewItem;
import vn.unicom.thi360.exam.api.ExamPreviewController.PreviewPart;
import vn.unicom.thi360.exam.api.ExamPreviewController.PreviewQuestion;
import vn.unicom.thi360.exam.domain.DisplaySegment;
import vn.unicom.thi360.exam.spi.PreviewPaperPort;

/**
 * Reads the paper stored by {@link ExamPreviewService} and gives it to other modules in {@code exam/spi} records.
 * Everything comes from the stored presentation, so the result does not move when the draft or template changes.
 */
@Component
class PreviewPaperReader implements PreviewPaperPort {
    /** The 30 minutes every preview lives and the margin a trial keeps after the exam time (BS 9). */
    private static final Duration LIFE = Duration.ofMinutes(30);

    private final PreviewSnapshotStore snapshots;
    private final Clock clock;

    PreviewPaperReader(PreviewSnapshotStore snapshots, Clock clock) {
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @Override
    public PreviewPaper load(UUID examId, UUID previewId) {
        return toPaper(require(examId, previewId));
    }

    @Override
    public Instant extendForTrial(UUID examId, UUID previewId) {
        PreviewSnapshotStore.Snapshot snapshot = require(examId, previewId);
        Instant expiry = snapshots.extendUntil(previewId,
                clock.instant().plus(Duration.ofMinutes(snapshot.durationMinutes())).plus(LIFE));
        if (expiry == null) throw new PreviewGoneException();
        return expiry;
    }

    private PreviewSnapshotStore.Snapshot require(UUID examId, UUID previewId) {
        PreviewSnapshotStore.Snapshot snapshot = snapshots.find(previewId);
        if (snapshot == null || !Objects.equals(examId, snapshot.examId())) throw new PreviewGoneException();
        return snapshot;
    }

    private static PreviewPaper toPaper(PreviewSnapshotStore.Snapshot snapshot) {
        // The store keeps parts in a map; the numbering of the paper gives their order.
        List<Part> parts = snapshot.parts().values().stream().map(PreviewSnapshotStore.PartSnapshot::presentation)
                .sorted(Comparator.comparingInt(PreviewPart::firstNumber)
                        .thenComparing(PreviewPart::order, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(PreviewPaperReader::part).toList();
        return new PreviewPaper(snapshot.examId(), snapshot.version(), snapshot.seed(), snapshot.kindCode(),
                snapshot.timing(), snapshot.durationMinutes(), parts);
    }

    private static Part part(PreviewPart part) {
        List<Segment> segments = part.displaySegments() == null ? List.of()
                : part.displaySegments().stream().map(PreviewPaperReader::segment).toList();
        return new Part(part.partId(), part.name(), part.order(), part.durationMinutes(), part.firstNumber(),
                part.lastNumber(), part.points(), part.blocks().stream().map(PreviewPaperReader::block).toList(),
                segments);
    }

    private static Segment segment(DisplaySegment segment) {
        return new Segment(segment.code(), segment.name(), segment.fromPosition(), segment.toPosition(),
                segment.commandCount(), segment.maxPoints());
    }

    private static Block block(PreviewBlock block) {
        return new Block(block.blockId(), block.blockName(), block.subjectCode(), block.questionType(),
                block.manualGrading(), block.pointsEach(), block.firstNumber(), block.lastNumber(),
                block.questionCount(), block.items().stream().map(PreviewPaperReader::item).toList());
    }

    private static Item item(PreviewItem item) {
        if ("SET".equals(item.kind())) {
            return new Item(Item.Kind.SET, null, new QuestionSet(UUID.fromString(item.setId()), item.code(),
                    item.version(), attachments(item.attachments()),
                    item.children().stream().map(PreviewPaperReader::question).toList()));
        }
        return new Item(Item.Kind.QUESTION, question(item.question()), null);
    }

    private static Question question(PreviewQuestion question) {
        return new Question(question.number(), UUID.fromString(question.questionId()), question.code(),
                question.version(), question.type(), question.points(),
                question.optionOrder() == null ? null : List.copyOf(question.optionOrder()),
                attachments(question.attachments()));
    }

    private static List<Attachment> attachments(List<PreviewAttachment> files) {
        return files == null ? List.of() : files.stream()
                .map(file -> new Attachment(UUID.fromString(file.id()), file.name(), file.mime())).toList();
    }
}
