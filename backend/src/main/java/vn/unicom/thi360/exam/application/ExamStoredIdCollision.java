package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;

/**
 * The checker, the solver and the preview name the section that holds the direct blocks of an option
 * {@code "<optionId>:blocks"} (the holder; it is no stored node). A draft written by older code may also hold a stored
 * node of any kind and depth (part, section, subject-pool section, option, a block of a section or of an option) with
 * that very id, for any option of the structure: the same id would then stand for two different things in a unit
 * proof, a path section or a preview. The check of such a draft refuses to certify anything (ADV:CR-4:21): one
 * exam-level, blocking structure issue, and no proof is read, written or stamped.
 *
 * <p>Applies to the live draft only, at the check boundary and its draft preview; a frozen published version and its
 * previews never run it. PUT keeps refusing the collision for options that have direct blocks
 * ({@code ExamStructureValidator}).
 */
final class ExamStoredIdCollision {
    static final String CODE = "STORED_ID_COLLISION";

    private ExamStoredIdCollision() { }

    /** The first stored node id, in document order, equal to {@code "<optionId>:blocks"} of any option; else empty. */
    static Optional<String> find(ExamStructureDocument document) {
        Set<String> reserved = new HashSet<>();
        for (ExamPart part : document.parts()) {
            for (ExamPartOption option : part.options()) reserved.add(option.id() + ":blocks");
        }
        if (reserved.isEmpty()) return Optional.empty();
        List<String> stored = new ArrayList<>();
        for (ExamPart part : document.parts()) {
            stored.add(part.id());
            sections(stored, part.sections());
            sections(stored, part.subjectPool());
            for (ExamPartOption option : part.options()) {
                stored.add(option.id());
                sections(stored, option.sections());
                option.blocks().forEach(block -> stored.add(block.id()));
            }
        }
        return stored.stream().filter(id -> id != null && reserved.contains(id)).findFirst();
    }

    private static void sections(List<String> stored, List<ExamSection> sections) {
        for (ExamSection section : sections) {
            stored.add(section.id());
            section.blocks().forEach(block -> stored.add(block.id()));
        }
    }

    /**
     * The issue: exam level (no part, section or option), group STRUCTURE, severity ERROR. The colliding stored id is
     * carried in {@code blockId} (and {@code location.blockId}); the web names it and says what to do.
     */
    static ObjectNode issue(ObjectMapper mapper, String storedId) {
        ExamCheckIssues factory = new ExamCheckIssues(mapper);
        ObjectNode issue = factory.create(CODE, "STRUCTURE", "ERROR");
        issue.put("blockId", storedId);
        return factory.key(issue);
    }
}
