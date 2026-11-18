package vn.unicom.thi360.exam.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Deterministic presentation order; question bodies and answer mappings are never modified. */
public final class PaperOrdering {
    private PaperOrdering() {}

    public record Question(String id, JsonNode body) {
        public Question { Objects.requireNonNull(id); Objects.requireNonNull(body); }
    }
    public record Block(String id, boolean shuffleUnits, List<Question> units) {
        public Block { units = List.copyOf(units); }
    }
    public record Section(String id, List<Block> blocks) {
        public Section { blocks = List.copyOf(blocks); }
    }
    public record Part(String id, List<Section> sections) {
        public Part { sections = List.copyOf(sections); }
    }
    public record Paper(List<Part> parts) {
        public Paper { parts = List.copyOf(parts); }
    }
    public record OrderedPaper(Paper paper, Map<String, List<String>> optionOrder) {
        public OrderedPaper { optionOrder = Collections.unmodifiableMap(new LinkedHashMap<>(optionOrder)); }
    }

    public static OrderedPaper order(Paper paper, long seed) {
        Random random = new Random(seed);
        Map<String, List<String>> options = new LinkedHashMap<>();
        List<Part> parts = new ArrayList<>();
        for (Part part : paper.parts()) {
            List<Section> sections = new ArrayList<>();
            for (Section section : part.sections()) {
                List<Block> blocks = new ArrayList<>();
                for (Block block : section.blocks()) {
                    List<Question> units = new ArrayList<>(block.units());
                    if (block.shuffleUnits()) Collections.shuffle(units, random);
                    for (Question unit : units) orderBody(unit.id(), unit.body(), random, options);
                    blocks.add(new Block(block.id(), block.shuffleUnits(), units));
                }
                sections.add(new Section(section.id(), blocks));
            }
            parts.add(new Part(part.id(), sections));
        }
        return new OrderedPaper(new Paper(parts), options);
    }

    private static void orderBody(String id, JsonNode body, Random random,
            Map<String, List<String>> output) {
        String type = body.path("type").asText();
        if ("QUESTION_SET".equals(type)) {
            for (JsonNode child : body.path("children")) {
                orderBody(child.path("id").asText(), child.path("body"), random, output);
            }
            return;
        }
        boolean choice = "SINGLE_CHOICE".equals(type) || "MULTIPLE_CHOICE".equals(type);
        boolean matching = "MATCHING".equals(type);
        if (!choice && !matching) return;
        List<String> ids = new ArrayList<>();
        for (JsonNode option : body.path(matching ? "right" : "options")) {
            ids.add(option.path("id").asText());
        }
        if (matching || body.path("shuffleOptions").asBoolean(true)) {
            Collections.shuffle(ids, random);
        } else {
            // Consume the same seeded shuffle so this flag does not reorder other questions.
            for (int i = ids.size(); i > 1; i--) random.nextInt(i);
        }
        if (output.putIfAbsent(id, List.copyOf(ids)) != null) {
            throw new IllegalArgumentException("duplicate question id: " + id);
        }
    }
}
