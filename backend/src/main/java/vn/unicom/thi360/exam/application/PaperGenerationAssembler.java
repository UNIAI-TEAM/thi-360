package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOption;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SubjectChoice;
import vn.unicom.thi360.exam.domain.solver.SubjectPick;
import vn.unicom.thi360.exam.domain.solver.SubjectWitness;
import vn.unicom.thi360.exam.spi.PaperSolverPort.DroppedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/**
 * Turns a VALID solver result into the port's paper: questions by part, section and group, plus the dropped ones.
 * It refuses to return a paper that does not hold the number of questions each section asks for.
 */
final class PaperGenerationAssembler {
    private PaperGenerationAssembler() {}

    private record BlockKey(String sectionId, String blockId) {}

    private record ShuffleLookup(
            Map<BlockKey, Boolean> bySectionBlock,
            Map<String, Boolean> byBlockFallback) {
        boolean shuffleUnits(String sectionId, String blockId) {
            Boolean exact = bySectionBlock.get(new BlockKey(sectionId, blockId));
            if (exact != null) {
                return exact;
            }
            return byBlockFallback.getOrDefault(blockId, true);
        }
    }

    static GeneratedPaper assemble(List<String> partIds, ExamStructureDocument document, List<SolvePart> solveParts,
            SolvePath actual, List<SubjectWitness> witnesses, List<QuestionPick> dropped,
            Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates) {
        ShuffleLookup shuffleLookup = collectShuffleUnits(document);
        Map<String, String> partOfSection = partOfSection(solveParts);
        Map<String, List<SectionOnPath>> sectionsByPart = new LinkedHashMap<>();
        for (String partId : partIds) sectionsByPart.put(partId, new ArrayList<>());
        for (SectionOnPath section : actual.sections()) place(sectionsByPart, partOfSection, section);
        for (List<SubjectPick> picks : actual.picks()) {
            for (SubjectPick pick : picks) {
                for (SectionOnPath section : pick.resolvedSections()) place(sectionsByPart, partOfSection, section);
            }
        }
        Map<String, GroupWitness> groups = new HashMap<>();
        for (SubjectWitness witness : witnesses) {
            for (GroupWitness group : witness.groups()) groups.put(group.groupId(), group);
        }

        List<GeneratedPart> parts = new ArrayList<>();
        for (var entry : sectionsByPart.entrySet()) {
            List<GeneratedSection> sections = new ArrayList<>();
            for (SectionOnPath section : entry.getValue()) {
                sections.add(section(section, groups, candidates, shuffleLookup));
            }
            parts.add(new GeneratedPart(entry.getKey(), sections));
        }

        List<SectionOnPath> trimmed = trimmedSections(actual);
        if (trimmed.size() != dropped.size()) {
            throw PaperGenerationPlan.failed("dropped questions do not match the trimmed sections");
        }
        List<DroppedQuestion> droppedQuestions = new ArrayList<>();
        for (int i = 0; i < dropped.size(); i++) {
            SectionOnPath section = trimmed.get(i);
            droppedQuestions.add(new DroppedQuestion(partOfSection.get(section.sectionId()), section.sectionId(),
                    section.subjectCode(), question(dropped.get(i))));
        }
        return new GeneratedPaper(parts, droppedQuestions);
    }

    private static void place(Map<String, List<SectionOnPath>> sectionsByPart, Map<String, String> partOfSection,
            SectionOnPath section) {
        List<SectionOnPath> list = sectionsByPart.get(partOfSection.get(section.sectionId()));
        if (list == null) throw PaperGenerationPlan.failed("section outside the requested parts: " + section.sectionId());
        list.add(section);
    }

    private static GeneratedSection section(SectionOnPath section, Map<String, GroupWitness> witnesses,
            Map<QuestionSourcePort.VersionKey, QuestionSourcePort.Candidate> candidates,
            ShuffleLookup shuffleLookup) {
        // A block with passage slots is solved as one synthetic group per slot; the port speaks authored blocks, so the
        // slot picks join, in slot order, under the id of the block they came from (as the preview consolidates them).
        Map<String, List<GeneratedQuestion>> byBlock = new LinkedHashMap<>();
        long units = 0;
        for (GroupQuota group : section.groups()) {
            GroupWitness witness = witnesses.get(group.groupId());
            String blockId = group.slotParent() == null ? group.groupId() : group.slotParent().parentGroupId();
            List<GeneratedQuestion> questions = byBlock.computeIfAbsent(blockId, id -> new ArrayList<>());
            if (witness != null) {
                for (QuestionPick pick : witness.picks()) {
                    questions.add(question(pick));
                    var candidate = candidates.get(new QuestionSourcePort.VersionKey(
                            UUID.fromString(pick.questionId()), pick.versionNo()));
                    if (candidate == null) {
                        throw PaperGenerationPlan.failed("picked question has no candidate: " + pick.questionId());
                    }
                    units += candidate.units().size();
                }
            }
        }
        List<GeneratedGroup> groups = new ArrayList<>();
        byBlock.forEach((blockId, questions) ->
                groups.add(new GeneratedGroup(
                        blockId, questions, shuffleLookup.shuffleUnits(section.sectionId(), blockId))));
        if (section.questionCount() != null && units != section.questionCount()) {
            throw PaperGenerationPlan.failed("section " + section.sectionId() + " holds " + units
                    + " of " + section.questionCount() + " questions");
        }
        return new GeneratedSection(section.sectionId(), section.subjectCode(),
                section.questionCount() == null ? (int) units : section.questionCount(), groups);
    }

    private static ShuffleLookup collectShuffleUnits(ExamStructureDocument document) {
        Map<BlockKey, Boolean> bySectionBlock = new HashMap<>();
        Map<String, Boolean> byBlockFallback = new HashMap<>();
        if (document == null) {
            return new ShuffleLookup(bySectionBlock, byBlockFallback);
        }
        for (ExamPart part : document.parts()) {
            collectSections(bySectionBlock, part.sections());
            collectSections(bySectionBlock, part.subjectPool());
            for (ExamPartOption option : part.options()) {
                collectSections(bySectionBlock, option.sections());
                String syntheticSectionId = option.id() == null ? null : option.id() + ":blocks";
                for (QuestionBlock block : option.blocks()) {
                    boolean shuffle = Boolean.TRUE.equals(block.shuffleUnits());
                    if (syntheticSectionId != null) {
                        bySectionBlock.put(new BlockKey(syntheticSectionId, block.id()), shuffle);
                    }
                    byBlockFallback.putIfAbsent(block.id(), shuffle);
                }
            }
        }
        return new ShuffleLookup(bySectionBlock, byBlockFallback);
    }

    private static void collectSections(Map<BlockKey, Boolean> target, List<ExamSection> sections) {
        for (ExamSection section : sections) {
            for (QuestionBlock block : section.blocks()) {
                target.put(new BlockKey(section.id(), block.id()), Boolean.TRUE.equals(block.shuffleUnits()));
            }
        }
    }

    /** Sections whose count was cut below the full count of their subject choice, in pick order (HSA science). */
    private static List<SectionOnPath> trimmedSections(SolvePath path) {
        List<SectionOnPath> sections = new ArrayList<>();
        for (int i = 0; i < path.choices().size(); i++) {
            SubjectChoice choice = path.choices().get(i);
            if (choice.countsByPick().stream().distinct().count() < 2) continue;
            int fullCount = choice.countsByPick().stream().mapToInt(Integer::intValue).max().orElseThrow();
            SubjectPick pick = path.picks().get(i).getFirst();
            for (int j = 0; j < pick.counts().size(); j++) {
                if (pick.counts().get(j) < fullCount) sections.add(pick.sections().get(j));
            }
        }
        return sections;
    }

    private static Map<String, String> partOfSection(List<SolvePart> parts) {
        Map<String, String> result = new HashMap<>();
        for (SolvePart part : parts) {
            if (part instanceof SolvePart.Fixed fixed) {
                fixed.sections().forEach(section -> result.put(section.sectionId(), fixed.partId()));
            } else if (part instanceof SolvePart.Choice choice) {
                choice.commonSections().forEach(section -> result.put(section.sectionId(), choice.partId()));
                for (SolveOption option : choice.options()) {
                    List<SectionOnPath> sections = option instanceof SolveOption.Sections normal
                            ? normal.sections() : ((SubjectChoice) option).sections();
                    sections.forEach(section -> result.put(section.sectionId(), choice.partId()));
                }
            }
        }
        return result;
    }

    private static GeneratedQuestion question(QuestionPick pick) {
        return new GeneratedQuestion(UUID.fromString(pick.questionId()), pick.versionNo());
    }
}
