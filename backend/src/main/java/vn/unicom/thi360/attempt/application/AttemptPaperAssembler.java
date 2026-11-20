package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.exam.spi.PaperOrdering;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Pure assembler turning one {@link PaperSolverPort.GeneratedPart} and loaded question snapshots
 * into deterministically ordered {@link AssembledItem}s using {@link PaperOrdering} (LT F22, BS §5).
 */
@Component
public class AttemptPaperAssembler {

    public record AssembledItem(
            Integer parentIndex,
            int ord,
            Integer displayNo,
            int unitCount,
            UUID questionId,
            int questionVersionNo,
            QuestionType questionType,
            UUID subjectItemId,
            String sectionId,
            String componentCode,
            String blockId,
            BigDecimal pointsMax,
            List<String> optionOrder) {
    }

    public record AssembledPart(
            List<AssembledItem> items,
            int nextDisplayNo,
            BigDecimal totalMaxPoints,
            int totalUnitCount) {
        public AssembledPart {
            items = List.copyOf(items);
        }
    }

    /**
     * Orders questions and options of {@code generatedPart} via {@link PaperOrdering#order}, assigns
     * continuous {@code displayNo} starting at {@code startingDisplayNo}, and computes exact points
     * and {@code unitCount} per item.
     */
    public AssembledPart assemble(
            PaperSolverPort.GeneratedPart generatedPart,
            Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions,
            Map<String, Boolean> blockShuffleUnits,
            long partSeed,
            int startingDisplayNo) {
        Objects.requireNonNull(generatedPart, "generatedPart must not be null");
        Objects.requireNonNull(versions, "versions must not be null");
        if (startingDisplayNo < 1) {
            throw new IllegalArgumentException("startingDisplayNo must be >= 1");
        }
        Map<String, Boolean> shuffleMap = blockShuffleUnits == null ? Map.of() : blockShuffleUnits;

        Map<String, PaperSolverPort.GeneratedSection> sectionById = new HashMap<>();
        Map<String, PaperSolverPort.GeneratedQuestion> questionById = new HashMap<>();
        List<PaperOrdering.Section> orderingSections = new ArrayList<>();

        for (PaperSolverPort.GeneratedSection section : generatedPart.sections()) {
            sectionById.put(section.sectionId(), section);
            List<PaperOrdering.Block> orderingBlocks = new ArrayList<>();
            for (PaperSolverPort.GeneratedGroup group : section.groups()) {
                boolean shuffleUnits = shuffleMap.containsKey(group.groupId())
                        ? Boolean.TRUE.equals(shuffleMap.get(group.groupId()))
                        : group.shuffleUnits();
                List<PaperOrdering.Question> units = new ArrayList<>();
                for (PaperSolverPort.GeneratedQuestion gq : group.questions()) {
                    questionById.put(gq.questionId().toString(), gq);
                    QuestionSourcePort.Version rootVersion = requireVersion(
                            versions, new QuestionSourcePort.VersionKey(gq.questionId(), gq.versionNo()));
                    JsonNode body = rootVersion.snapshot().path("body");
                    units.add(new PaperOrdering.Question(gq.questionId().toString(), body));
                }
                orderingBlocks.add(new PaperOrdering.Block(group.groupId(), shuffleUnits, units));
            }
            orderingSections.add(new PaperOrdering.Section(section.sectionId(), orderingBlocks));
        }

        PaperOrdering.Paper paper = new PaperOrdering.Paper(
                List.of(new PaperOrdering.Part(generatedPart.partCode(), orderingSections)));
        PaperOrdering.OrderedPaper ordered = PaperOrdering.order(paper, partSeed);
        Map<String, List<String>> optionOrders = ordered.optionOrder();

        List<AssembledItem> items = new ArrayList<>();
        int nextDisplayNo = startingDisplayNo;
        int ord = 1;
        BigDecimal totalMaxPoints = BigDecimal.ZERO;
        int totalUnitCount = 0;

        for (PaperOrdering.Section orderedSection : ordered.paper().parts().getFirst().sections()) {
            PaperSolverPort.GeneratedSection genSection = sectionById.get(orderedSection.id());
            String componentCode = genSection.subjectCode();
            for (PaperOrdering.Block orderedBlock : orderedSection.blocks()) {
                String blockId = orderedBlock.id();
                for (PaperOrdering.Question orderedQuestion : orderedBlock.units()) {
                    PaperSolverPort.GeneratedQuestion gq = questionById.get(orderedQuestion.id());
                    QuestionSourcePort.Version rootVersion = requireVersion(
                            versions, new QuestionSourcePort.VersionKey(gq.questionId(), gq.versionNo()));
                    JsonNode snapshot = rootVersion.snapshot();
                    JsonNode body = snapshot.path("body");
                    QuestionType type = QuestionType.valueOf(body.path("type").asText());
                    UUID rootSubjectItemId = uuidOrNull(snapshot, "subjectItemId");

                    if (type == QuestionType.QUESTION_SET) {
                        JsonNode children = body.path("children");
                        List<AssembledItem> childItems = new ArrayList<>(children.size());
                        BigDecimal setPoints = BigDecimal.ZERO;
                        int rootIndex = items.size();
                        int rootOrd = ord++;
                        for (JsonNode childNode : children) {
                            UUID childId = UUID.fromString(childNode.path("id").asText());
                            QuestionSourcePort.Version childVersion = versions.get(
                                    new QuestionSourcePort.VersionKey(childId, gq.versionNo()));
                            JsonNode childSnap = childVersion != null ? childVersion.snapshot() : null;
                            QuestionType childType = resolveChildType(childSnap, childNode);
                            BigDecimal childPoints = resolveChildPoints(childSnap, childNode);
                            UUID childSubject = childSnap != null && childSnap.hasNonNull("subjectItemId")
                                    ? uuidOrNull(childSnap, "subjectItemId")
                                    : rootSubjectItemId;
                            List<String> childOptionOrder = needsOptionOrder(childType)
                                    ? optionOrders.get(childId.toString())
                                    : null;
                            int childDisplayNo = nextDisplayNo++;
                            setPoints = setPoints.add(childPoints);
                            totalMaxPoints = totalMaxPoints.add(childPoints);
                            totalUnitCount += 1;
                            childItems.add(new AssembledItem(
                                    rootIndex,
                                    ord++,
                                    childDisplayNo,
                                    1,
                                    childId,
                                    gq.versionNo(),
                                    childType,
                                    childSubject,
                                    orderedSection.id(),
                                    componentCode,
                                    blockId,
                                    childPoints,
                                    childOptionOrder));
                        }
                        items.add(new AssembledItem(
                                null,
                                rootOrd,
                                null,
                                0,
                                gq.questionId(),
                                gq.versionNo(),
                                QuestionType.QUESTION_SET,
                                rootSubjectItemId,
                                orderedSection.id(),
                                componentCode,
                                blockId,
                                setPoints,
                                null));
                        items.addAll(childItems);
                    } else if (type == QuestionType.CLOZE) {
                        JsonNode blanks = body.path("blanks");
                        int unitCount = blanks.size();
                        if (unitCount <= 0) {
                            throw new IllegalStateException("CLOZE question has no blanks: " + gq.questionId());
                        }
                        BigDecimal clozePoints = BigDecimal.ZERO;
                        for (JsonNode blank : blanks) {
                            clozePoints = clozePoints.add(blank.hasNonNull("points")
                                    ? blank.path("points").decimalValue()
                                    : BigDecimal.ONE);
                        }
                        int displayNo = nextDisplayNo;
                        nextDisplayNo += unitCount;
                        totalMaxPoints = totalMaxPoints.add(clozePoints);
                        totalUnitCount += unitCount;
                        items.add(new AssembledItem(
                                null,
                                ord++,
                                displayNo,
                                unitCount,
                                gq.questionId(),
                                gq.versionNo(),
                                QuestionType.CLOZE,
                                rootSubjectItemId,
                                orderedSection.id(),
                                componentCode,
                                blockId,
                                clozePoints,
                                null));
                    } else {
                        BigDecimal points = snapshot.hasNonNull("points")
                                ? snapshot.path("points").decimalValue()
                                : BigDecimal.ONE;
                        List<String> optionOrder = needsOptionOrder(type)
                                ? optionOrders.get(gq.questionId().toString())
                                : null;
                        int displayNo = nextDisplayNo++;
                        totalMaxPoints = totalMaxPoints.add(points);
                        totalUnitCount += 1;
                        items.add(new AssembledItem(
                                null,
                                ord++,
                                displayNo,
                                1,
                                gq.questionId(),
                                gq.versionNo(),
                                type,
                                rootSubjectItemId,
                                orderedSection.id(),
                                componentCode,
                                blockId,
                                points,
                                optionOrder));
                    }
                }
            }
        }
        return new AssembledPart(items, nextDisplayNo, totalMaxPoints, totalUnitCount);
    }

    private static QuestionSourcePort.Version requireVersion(
            Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions,
            QuestionSourcePort.VersionKey key) {
        QuestionSourcePort.Version version = versions.get(key);
        if (version == null) {
            throw new IllegalStateException("Missing question version: " + key);
        }
        return version;
    }

    private static QuestionType resolveChildType(JsonNode childSnap, JsonNode childNode) {
        if (childSnap != null && childSnap.path("body").hasNonNull("type")) {
            return QuestionType.valueOf(childSnap.path("body").path("type").asText());
        }
        if (childNode.path("body").hasNonNull("type")) {
            return QuestionType.valueOf(childNode.path("body").path("type").asText());
        }
        return QuestionType.valueOf(childNode.path("type").asText());
    }

    private static BigDecimal resolveChildPoints(JsonNode childSnap, JsonNode childNode) {
        if (childSnap != null && childSnap.hasNonNull("points")) {
            return childSnap.path("points").decimalValue();
        }
        if (childNode.hasNonNull("points")) {
            return childNode.path("points").decimalValue();
        }
        return BigDecimal.ONE;
    }

    private static boolean needsOptionOrder(QuestionType type) {
        return type == QuestionType.SINGLE_CHOICE
                || type == QuestionType.MULTIPLE_CHOICE
                || type == QuestionType.MATCHING;
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return UUID.fromString(node.path(field).asText());
    }
}
