package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vn.unicom.thi360.exam.api.ExamPreviewController.*;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.spi.PaperOrdering;

/** Applies the same seeded presentation ordering used for attempts without losing authored order. */
final class PreviewPaperOrdering {
    static PreviewPaper order(PreviewPaper paper, ExamStructureDocument document, long seed, ObjectMapper mapper,
            Map<String, PreviewSnapshotStore.PartSnapshot> retained) {
        Map<String, Boolean> shuffle = new HashMap<>();
        for (var part : document.parts()) {
            collect(shuffle, part.sections());
            collect(shuffle, part.subjectPool());
            for (var option : part.options()) {
                collect(shuffle, option.sections());
                collectBlocks(shuffle, option.blocks());
            }
        }
        List<PaperOrdering.Part> parts = new ArrayList<>();
        for (PreviewPart part : paper.parts()) {
            List<PaperOrdering.Section> sections = new ArrayList<>();
            for (PreviewBlock block : part.blocks()) {
                List<PaperOrdering.Question> units = new ArrayList<>();
                for (PreviewItem item : block.items()) units.add(orderingQuestion(item, mapper));
                sections.add(new PaperOrdering.Section(block.blockId(), List.of(
                        new PaperOrdering.Block(block.blockId(), shuffle.getOrDefault(block.blockId(), true), units))));
            }
            parts.add(new PaperOrdering.Part(part.partId(), sections));
        }
        PaperOrdering.OrderedPaper ordered = PaperOrdering.order(new PaperOrdering.Paper(parts), seed);
        Map<String, List<String>> optionOrders = new HashMap<>(ordered.optionOrder());
        List<PreviewPart> output = new ArrayList<>();
        int next = 1;
        for (int p = 0; p < paper.parts().size(); p++) {
            PreviewPart part = paper.parts().get(p);
            int partFirst = next;
            PreviewPart savedPart = retained.containsKey(part.partId()) ? retained.get(part.partId()).presentation() : null;
            List<PreviewBlock> blocks = new ArrayList<>();
            for (int b = 0; b < part.blocks().size(); b++) {
                PreviewBlock block = part.blocks().get(b);
                PreviewBlock savedBlock = savedPart == null ? null : savedPart.blocks().stream()
                        .filter(value -> value.blockId().equals(block.blockId())).findFirst().orElseThrow();
                Map<String, PreviewItem> original = new HashMap<>();
                Map<String, Integer> positions = new HashMap<>();
                Map<String, Integer> spans = new HashMap<>();
                for (int i = 0; i < block.items().size(); i++) {
                    PreviewItem item = block.items().get(i);
                    String id = id(item);
                    original.put(id, item);
                    positions.put(id, i + 1);
                    int start = firstNumber(item);
                    int end = i + 1 == block.items().size() ? block.lastNumber() + 1
                            : firstNumber(block.items().get(i + 1));
                    spans.put(id, end - start);
                }
                int first = next;
                List<PreviewItem> items = new ArrayList<>();
                List<String> itemIds = savedBlock == null
                        ? ordered.paper().parts().get(p).sections().get(b).blocks().getFirst().units().stream()
                                .map(PaperOrdering.Question::id).toList()
                        : savedBlock.items().stream().map(PreviewPaperOrdering::id).toList();
                Map<String, PreviewItem> savedItems = new HashMap<>();
                if (savedBlock != null) {
                    for (PreviewItem saved : savedBlock.items()) {
                        savedItems.put(id(saved), saved);
                        if (saved.question() != null && saved.question().optionOrder() != null) {
                            optionOrders.put(saved.question().questionId(), saved.question().optionOrder());
                        }
                        if (saved.children() != null) for (PreviewQuestion child : saved.children()) {
                            if (child.optionOrder() != null) optionOrders.put(child.questionId(), child.optionOrder());
                        }
                    }
                }
                for (String itemId : itemIds) {
                    PreviewItem item = original.get(itemId);
                    if (item == null) throw new IllegalStateException("Ordered question missing from preview");
                    Integer originalOrder = savedBlock == null ? positions.get(itemId) : savedItems.get(itemId).originalOrder();
                    if (item.question() != null) {
                        PreviewQuestion question = question(item.question(), next, optionOrders);
                        items.add(new PreviewItem(item.kind(), question, item.setId(), item.code(), item.version(),
                                item.simulated(), item.passage(), item.children(), originalOrder, item.attachments()));
                    } else {
                        List<PreviewQuestion> children = new ArrayList<>();
                        int childNumber = next;
                        for (PreviewQuestion child : item.children()) {
                            children.add(question(child, childNumber++, optionOrders));
                        }
                        items.add(new PreviewItem(item.kind(), null, item.setId(), item.code(), item.version(),
                                item.simulated(), item.passage(), children, originalOrder, item.attachments()));
                    }
                    next += spans.get(itemId);
                }
                blocks.add(new PreviewBlock(block.blockId(), block.blockName(), block.subjectCode(), block.questionType(),
                        block.manualGrading(), block.pointsEach(), first, next - 1, block.questionCount(), block.setCount(),
                        block.setSize(), block.requirement(), block.allocation(), items));
            }
            output.add(new PreviewPart(part.partId(), part.name(), part.order(), part.durationMinutes(), partFirst,
                    next - 1, part.points(), part.maxPoints(), blocks, part.displaySegments()));
        }
        return new PreviewPaper(paper.totalQuestions(), paper.totalPoints(), paper.totalMinutes(), paper.sharedDuration(),
                paper.hasManualGrading(), output, paper.checks(), paper.knowledgeStats(), paper.subjects());
    }

    private static void collect(Map<String, Boolean> target, List<ExamSection> sections) {
        for (ExamSection section : sections) {
            if (section.isMixBlocks()) {
                target.put(section.id(), true);
            }
            collectBlocks(target, section.blocks());
        }
    }

    private static void collectBlocks(Map<String, Boolean> target, List<QuestionBlock> blocks) {
        for (QuestionBlock block : blocks) target.put(block.id(), Boolean.TRUE.equals(block.shuffleUnits()));
    }

    private static PaperOrdering.Question orderingQuestion(PreviewItem item, ObjectMapper mapper) {
        if (item.question() != null) {
            JsonNode body = item.question().body();
            return new PaperOrdering.Question(id(item), body == null ? mapper.createObjectNode() : body);
        }
        var body = mapper.createObjectNode();
        body.put("type", "QUESTION_SET");
        var children = body.putArray("children");
        for (PreviewQuestion child : item.children()) {
            var node = children.addObject().put("id", child.questionId());
            node.set("body", child.body() == null ? mapper.createObjectNode() : child.body());
        }
        return new PaperOrdering.Question(id(item), body);
    }

    private static String id(PreviewItem item) {
        return item.question() == null ? item.setId() : item.question().questionId();
    }

    private static int firstNumber(PreviewItem item) {
        return item.question() == null ? item.children().getFirst().number() : item.question().number();
    }

    private static PreviewQuestion question(PreviewQuestion question, int number, Map<String, List<String>> ordering) {
        List<String> order = ordering.get(question.questionId());
        List<String> original = null;
        List<PreviewOption> options = question.options();
        if (order != null) {
            original = new ArrayList<>();
            JsonNode body = question.body();
            JsonNode entries = body.path("MATCHING".equals(question.type()) ? "right" : "options");
            for (JsonNode entry : entries) original.add(entry.path("id").asText());
            if (options != null) {
                Map<String, PreviewOption> byId = new HashMap<>();
                for (PreviewOption option : options) byId.put(option.id(), option);
                options = order.stream().map(byId::get).toList();
            }
        }
        return new PreviewQuestion(number, question.questionId(), question.code(), question.version(), question.type(),
                question.simulated(), question.content(), question.gradeCode(), question.difficultyCode(), question.knowledge(),
                question.points(), options, question.answer(), question.solution(), question.rubric(), question.body(),
                order, original == null ? null : List.copyOf(original), question.number(), question.assignedTopicQuotaIds(),
                question.attachments());
    }
}
