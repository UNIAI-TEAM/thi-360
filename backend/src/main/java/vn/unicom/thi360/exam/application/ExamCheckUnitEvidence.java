package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.SubjectWitness;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** Reads proof-paper snapshots in a batch and reports only facts the snapshots establish. */
final class ExamCheckUnitEvidence {
    private ExamCheckUnitEvidence() { }
    private record BlockLocation(String partId, String sectionId, String subjectCode, String blockId) { }

    /** A fact and the subject witness (one unit's proof) whose group and question it describes. */
    record OwnedFact(ExamCheckRules.WitnessFact fact, SubjectWitness witness) { }

    static Map<Long, List<ExamCheckRules.WitnessFact>> witnessFacts(QuestionSourcePort source,
            QuestionAttachmentPort attachments, ExamStructureDocument document, List<PathResult> paths,
            Map<String, Map<String, List<String>>> combinationsByPart) {
        Map<Long, List<ExamCheckRules.WitnessFact>> result = new LinkedHashMap<>();
        ownedWitnessFacts(source, attachments, document, paths, combinationsByPart).forEach((path, owned) ->
                result.put(path, owned.stream().map(OwnedFact::fact).toList()));
        return result;
    }

    static Map<Long, List<OwnedFact>> ownedWitnessFacts(QuestionSourcePort source,
            QuestionAttachmentPort attachments, ExamStructureDocument document, List<PathResult> paths,
            Map<String, Map<String, List<String>>> combinationsByPart) {
        Map<String, BlockLocation> locations = new LinkedHashMap<>();
        for (ExamPart part : document.parts()) {
            addLocations(locations, part, part.sections());
            addLocations(locations, part, part.subjectPool());
            for (var option : part.options()) {
                addLocations(locations, part, option.sections());
                for (var block : option.blocks()) locations.put(block.id(),
                        new BlockLocation(part.id(), option.id() + ":blocks", option.code(), block.id()));
            }
        }
        Set<QuestionSourcePort.VersionKey> keys = new LinkedHashSet<>();
        for (PathResult path : paths) for (SubjectWitness witness : witnesses(path)) {
            for (var group : witness.groups()) for (var pick : group.picks()) {
                keys.add(new QuestionSourcePort.VersionKey(UUID.fromString(pick.questionId()), pick.versionNo()));
            }
        }
        if (keys.isEmpty()) return Map.of();
        Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions = new LinkedHashMap<>();
        for (var version : source.loadVersions(List.copyOf(keys))) versions.put(version.key(), version);
        Map<QuestionSourcePort.VersionKey, Boolean> readable = readable(attachments, versions);
        Map<Long, List<OwnedFact>> result = new LinkedHashMap<>();
        for (PathResult path : paths) {
            List<OwnedFact> facts = new ArrayList<>();
            String pathId = path.path().chosen().isEmpty() ? "default"
                    : String.join("+", path.path().chosen().stream().map(choice -> choice.optionId()).toList());
            for (SubjectWitness witness : witnesses(path)) for (var group : witness.groups()) {
                String groupId = group.groupId();
                Integer slot = null;
                int marker = groupId.lastIndexOf(":slot:");
                if (marker >= 0) {
                    try { slot = Integer.valueOf(groupId.substring(marker + 6)); groupId = groupId.substring(0, marker); }
                    catch (NumberFormatException ignored) { /* not a generated slot */ }
                }
                BlockLocation location = locations.get(groupId);
                if (location == null) continue;
                var affected = new ArrayList<String>();
                for (var choice : path.path().chosen()) {
                    String prefix = choice.partId() + ":";
                    if (choice.optionId().startsWith(prefix)) {
                        String code = choice.optionId().substring(prefix.length());
                        if (combinationsByPart.getOrDefault(choice.partId(), Map.of())
                                .getOrDefault(code, List.of()).contains(location.subjectCode())) affected.add(code);
                    }
                }
                var where = new ExamCheckRules.EvidenceLocation(location.partId(), location.sectionId(),
                        location.subjectCode(), location.blockId(), slot, null, pathId, affected);
                for (var pick : group.picks()) {
                    var key = new QuestionSourcePort.VersionKey(UUID.fromString(pick.questionId()), pick.versionNo());
                    var version = versions.get(key);
                    if (version == null || version.snapshot() == null || version.code() == null) continue;
                    List<ExamCheckRules.WitnessFact> made = new ArrayList<>();
                    addFacts(made, where, version.code(), version.snapshot().path("body"), readable.get(key), version.childCodes());
                    for (var fact : made) facts.add(new OwnedFact(fact, witness));
                }
            }
            result.put(path.path().pathIndex(), List.copyOf(facts));
        }
        return result;
    }

    private static void addLocations(Map<String, BlockLocation> locations, ExamPart part, List<ExamSection> sections) {
        for (var section : sections) for (var block : section.blocks()) {
            locations.put(block.id(), new BlockLocation(part.id(), section.id(), section.subjectCode(), block.id()));
        }
    }

    private static List<SubjectWitness> witnesses(PathResult path) {
        if (!path.witness().isEmpty()) return path.witness();
        List<SubjectWitness> result = new ArrayList<>();
        for (var selection : path.selections()) result.addAll(selection.witness());
        return result;
    }
    /**
     * True when every attachment of the root version is in the storage, false when one is missing; versions
     * without attachments are absent (null fact). Only versions whose snapshot lists attachments reach the port, so an
     * exam without attachments never touches the storage. A storage failure is not caught here (H48).
     */
    private static Map<QuestionSourcePort.VersionKey, Boolean> readable(QuestionAttachmentPort port,
            Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Version> versions) {
        List<QuestionSourcePort.VersionKey> withFiles = new ArrayList<>();
        for (var version : versions.values()) {
            if (version.snapshot() != null && version.snapshot().path("attachmentIds").size() > 0) withFiles.add(version.key());
        }
        if (withFiles.isEmpty()) return Map.of();
        Map<QuestionSourcePort.VersionKey, Boolean> result = new LinkedHashMap<>();
        port.attachments(withFiles, true).forEach((key, files) -> {
            if (!files.isEmpty()) result.put(key, files.stream().allMatch(file -> Boolean.TRUE.equals(file.readable())));
        });
        return result;
    }



    private static void addFacts(List<ExamCheckRules.WitnessFact> out, ExamCheckRules.EvidenceLocation where,
            String code, JsonNode body, Boolean attachments, Map<UUID, String> childCodes) {
        String type = body.path("type").asText("");
        if ("QUESTION_SET".equals(type)) {
            JsonNode children = body.path("children");
            if (!children.isArray() || children.isEmpty()) {
                out.add(new ExamCheckRules.WitnessFact(where, code, false, null, attachments));
                return;
            }
            // The files sit on the root question, so the set gets one fact of its own; the children carry none.
            if (attachments != null) out.add(new ExamCheckRules.WitnessFact(where, code, null, null, attachments));
            int position = 0;
            for (JsonNode child : children) {
                position++;
                String childCode = code;
                try { childCode = childCodes.getOrDefault(UUID.fromString(child.path("id").asText()), code); }
                catch (IllegalArgumentException ignored) { /* older snapshots can lack child IDs */ }
                var childWhere = new ExamCheckRules.EvidenceLocation(where.partId(), where.sectionId(),
                        where.subjectCode(), where.blockId(), where.slotIndex(), position, where.pathId(), where.affectedCodes());
                addFacts(out, childWhere, childCode, child.path("body"), null, Map.of());
            }
            return;
        }
        Boolean answer = null;
        Boolean scoring = null;
        switch (type) {
            case "SINGLE_CHOICE", "MULTIPLE_CHOICE" -> {
                JsonNode options = body.path("options");
                if (!options.isArray()) answer = false;
                else {
                    int correct = 0;
                    for (JsonNode option : options) if (option.path("correct").asBoolean(false)) correct++;
                    answer = options.size() >= 2 && ("SINGLE_CHOICE".equals(type) ? correct == 1 : correct >= 2);
                }
                if ("MULTIPLE_CHOICE".equals(type)) scoring = nonblank(body.path("scoringRule"));
            }
            case "SHORT_ANSWER" -> answer = nonemptyStrings(body.path("answers"));
            case "CLOZE" -> {
                JsonNode blanks = body.path("blanks");
                answer = blanks.isArray() && !blanks.isEmpty();
                if (answer) for (JsonNode blank : blanks) answer &= nonemptyStrings(blank.path("answers"));
            }
            case "TRUE_FALSE" -> {
                JsonNode statements = body.path("statements");
                answer = statements.isArray() && !statements.isEmpty();
                if (answer) for (JsonNode statement : statements) answer &= statement.path("answer").isBoolean();
                String method = body.path("scoringMethod").asText("");
                scoring = "PER_STATEMENT".equals(method) || "COUNT_TABLE".equals(method) && !body.path("scoreTable").isEmpty();
            }
            case "MATCHING" -> {
                JsonNode left = body.path("left"), pairs = body.path("pairs");
                answer = left.isArray() && !left.isEmpty() && pairs.isArray() && pairs.size() == left.size();
                scoring = answer;
            }
            case "ESSAY" -> scoring = nonblank(body.path("gradingGuide"));
            default -> answer = false;
        }
        out.add(new ExamCheckRules.WitnessFact(where, code, answer, scoring, attachments));
    }

    private static boolean nonblank(JsonNode value) { return value.isTextual() && !value.asText().isBlank(); }
    private static boolean nonemptyStrings(JsonNode values) {
        if (!values.isArray()) return false;
        for (JsonNode value : values) if (nonblank(value)) return true;
        return false;
    }
}
