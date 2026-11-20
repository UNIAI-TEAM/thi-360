package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Strips every LT §7.3 forbidden field from question snapshots before student delivery and rewrites
 * admin asset URLs to attempt-scoped asset URLs (AC-25, AC-27).
 */
@Component
public class AttemptQuestionSanitizer {

    public static final String ADMIN_ASSET_PREFIX = "/api/v1/admin/question-assets/";

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "correct",
            "answer",
            "answers",
            "pairs",
            "scoreTable",
            "trueFalseTable",
            "solution",
            "gradingGuide",
            "rubric",
            "sampleAnswer",
            "commonMistakes",
            "knowledge",
            "knowledgeItemIds",
            "mapId",
            "mapIds",
            "mapidIds",
            "levelItemId",
            "difficulty",
            "difficultyCode");

    private final ObjectMapper mapper;

    public AttemptQuestionSanitizer(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Sanitizes a full question snapshot (or child question node) for student rendering, keeping only
     * student-safe fields and applying {@code optionOrder} / {@code childOptionOrders}.
     */
    public ObjectNode sanitizeSnapshot(
            UUID attemptId,
            JsonNode snapshot,
            List<String> optionOrder,
            Map<String, List<String>> childOptionOrders) {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        ObjectNode out = mapper.createObjectNode();
        if (snapshot == null || snapshot.isMissingNode() || snapshot.isNull()) {
            return out;
        }
        if (snapshot.hasNonNull("id")) {
            out.set("id", snapshot.get("id"));
        }
        if (snapshot.hasNonNull("type")) {
            out.set("type", snapshot.get("type"));
        }
        if (snapshot.hasNonNull("content")) {
            out.put("content", rewriteAssetUrls(attemptId, snapshot.path("content").asText()));
        }
        if (snapshot.hasNonNull("points")) {
            out.set("points", snapshot.get("points"));
        }
        if (snapshot.hasNonNull("attachmentIds") && snapshot.get("attachmentIds").isArray()) {
            out.set("attachmentIds", snapshot.get("attachmentIds").deepCopy());
        }
        if (snapshot.hasNonNull("body") && snapshot.get("body").isObject()) {
            out.set("body", sanitizeBody(attemptId, snapshot.get("body"), optionOrder, childOptionOrders));
        }
        stripAnyForbiddenKeys(out, null);
        return out;
    }

    /** Sanitizes a question {@code body} object for student rendering. */
    public ObjectNode sanitizeBody(
            UUID attemptId,
            JsonNode body,
            List<String> optionOrder,
            Map<String, List<String>> childOptionOrders) {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        ObjectNode out = mapper.createObjectNode();
        if (body == null || body.isMissingNode() || body.isNull()) {
            return out;
        }
        String type = body.path("type").asText("");
        if (!type.isEmpty()) {
            out.put("type", type);
        }
        switch (type) {
            case "SINGLE_CHOICE", "MULTIPLE_CHOICE" ->
                    out.set("options", sanitizeOrderedIdContentArray(attemptId, body.path("options"), optionOrder));
            case "SHORT_ANSWER" -> {
                copyIfNonNull(body, out, "inputHint");
                copyIfNonNull(body, out, "answerFormat");
            }
            case "CLOZE" -> {
                if (body.hasNonNull("passage")) {
                    out.put("passage", rewriteAssetUrls(attemptId, body.path("passage").asText()));
                }
                ArrayNode blanksOut = mapper.createArrayNode();
                for (JsonNode blank : body.path("blanks")) {
                    ObjectNode b = mapper.createObjectNode();
                    if (blank.hasNonNull("id")) {
                        b.set("id", blank.get("id"));
                    }
                    blanksOut.add(b);
                }
                out.set("blanks", blanksOut);
            }
            case "TRUE_FALSE" ->
                    out.set("statements", sanitizeIdContentArray(attemptId, body.path("statements")));
            case "MATCHING" -> {
                out.set("left", sanitizeIdContentArray(attemptId, body.path("left")));
                out.set("right", sanitizeOrderedIdContentArray(attemptId, body.path("right"), optionOrder));
                copyIfNonNull(body, out, "reuseRight");
            }
            case "ESSAY" -> copyIfNonNull(body, out, "wordLimit");
            case "QUESTION_SET" -> {
                if (body.hasNonNull("passage")) {
                    out.put("passage", rewriteAssetUrls(attemptId, body.path("passage").asText()));
                }
                copyIfNonNull(body, out, "blankNumberStart");
                ArrayNode childrenOut = mapper.createArrayNode();
                Map<String, List<String>> childOrders = childOptionOrders == null ? Map.of() : childOptionOrders;
                for (JsonNode child : body.path("children")) {
                    String childId = child.path("id").asText("");
                    childrenOut.add(sanitizeSnapshot(
                            attemptId, child, childOrders.get(childId), Map.of()));
                }
                out.set("children", childrenOut);
            }
            default -> {
                // Unknown type: omit unallowlisted fields, preserving only safe text if present.
                if (body.hasNonNull("passage")) {
                    out.put("passage", rewriteAssetUrls(attemptId, body.path("passage").asText()));
                }
            }
        }
        stripAnyForbiddenKeys(out, null);
        return out;
    }

    /** Rewrites {@code /api/v1/admin/question-assets/{assetId}} image URLs to {@code /api/v1/me/attempts/{attemptId}/assets/{assetId}}. */
    public String rewriteAssetUrls(UUID attemptId, String html) {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        if (html == null || html.isEmpty()) {
            return html;
        }
        if (!html.contains("<img") && !html.contains(ADMIN_ASSET_PREFIX)) {
            return html;
        }
        String targetPrefix = "/api/v1/me/attempts/" + attemptId + "/assets/";
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            if (src.startsWith(ADMIN_ASSET_PREFIX)) {
                img.attr("src", targetPrefix + src.substring(ADMIN_ASSET_PREFIX.length()));
            } else if (src.isBlank() && img.hasAttr("data-asset-id")) {
                String assetId = img.attr("data-asset-id").trim();
                if (!assetId.isEmpty()) {
                    img.attr("src", targetPrefix + assetId);
                }
            }
        }
        String rewritten = doc.body().html();
        if (rewritten.contains(ADMIN_ASSET_PREFIX)) {
            rewritten = rewritten.replace(ADMIN_ASSET_PREFIX, targetPrefix);
        }
        return rewritten;
    }

    private ArrayNode sanitizeIdContentArray(UUID attemptId, JsonNode entries) {
        ArrayNode array = mapper.createArrayNode();
        if (!entries.isArray()) {
            return array;
        }
        for (JsonNode entry : entries) {
            array.add(idContentNode(attemptId, entry));
        }
        return array;
    }

    private ArrayNode sanitizeOrderedIdContentArray(UUID attemptId, JsonNode entries, List<String> order) {
        ArrayNode array = mapper.createArrayNode();
        if (!entries.isArray()) {
            return array;
        }
        if (order == null || order.isEmpty()) {
            return sanitizeIdContentArray(attemptId, entries);
        }
        Map<String, JsonNode> byId = new HashMap<>();
        for (JsonNode entry : entries) {
            byId.put(entry.path("id").asText(), entry);
        }
        Set<String> emitted = new HashSet<>();
        for (String id : order) {
            JsonNode match = byId.get(id);
            if (match != null && emitted.add(id)) {
                array.add(idContentNode(attemptId, match));
            }
        }
        for (JsonNode entry : entries) {
            String id = entry.path("id").asText();
            if (emitted.add(id)) {
                array.add(idContentNode(attemptId, entry));
            }
        }
        return array;
    }

    private ObjectNode idContentNode(UUID attemptId, JsonNode entry) {
        ObjectNode item = mapper.createObjectNode();
        if (entry.hasNonNull("id")) {
            item.set("id", entry.get("id"));
        }
        if (entry.hasNonNull("content")) {
            item.put("content", rewriteAssetUrls(attemptId, entry.path("content").asText()));
        }
        return item;
    }

    private static void copyIfNonNull(JsonNode source, ObjectNode target, String field) {
        if (source.hasNonNull(field)) {
            target.set(field, source.get(field).deepCopy());
        }
    }

    private static void stripAnyForbiddenKeys(JsonNode node, String owningArrayKey) {
        if (node instanceof ObjectNode obj) {
            List<String> toRemove = new java.util.ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : obj.properties()) {
                String key = entry.getKey();
                if (FORBIDDEN_KEYS.contains(key)
                        || ("statements".equals(owningArrayKey) && "points".equals(key))) {
                    toRemove.add(key);
                } else {
                    JsonNode value = entry.getValue();
                    if (value.isArray()) {
                        for (JsonNode element : value) {
                            stripAnyForbiddenKeys(element, key);
                        }
                    } else {
                        stripAnyForbiddenKeys(value, null);
                    }
                }
            }
            obj.remove(toRemove);
        } else if (node instanceof ArrayNode arr) {
            for (JsonNode element : arr) {
                stripAnyForbiddenKeys(element, owningArrayKey);
            }
        }
    }
}
