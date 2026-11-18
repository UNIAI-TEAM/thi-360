package vn.unicom.thi360.exam.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.QuestionBlock;

/** Maps stable section and solver group identifiers back to their owning part. */
final class PreviewPartIndex {
    private final Map<String, String> sections = new HashMap<>();
    private final Map<String, String> groups = new HashMap<>();

    PreviewPartIndex(ExamStructureDocument document) {
        for (ExamPart part : document.parts()) {
            indexSections(part.id(), part.sections());
            indexSections(part.id(), part.subjectPool());
            for (var option : part.options()) {
                indexSections(part.id(), option.sections());
                indexBlocks(part.id(), option.blocks());
                if (!option.blocks().isEmpty()) sections.put(option.id() + ":blocks", part.id());
            }
        }
    }

    String sectionPart(String sectionId) { return sections.get(sectionId); }
    String groupPart(String groupId) { return groups.get(groupId); }

    private void indexSections(String partId, List<ExamSection> values) {
        for (ExamSection section : values) {
            sections.put(section.id(), partId);
            indexBlocks(partId, section.blocks());
        }
    }

    private void indexBlocks(String partId, List<QuestionBlock> values) {
        for (QuestionBlock block : values) {
            if (block.slots().isEmpty()) {
                groups.put(block.id(), partId);
            } else if (block.setCount() != null) {
                for (int i = 1; i <= block.setCount(); i++) groups.put(block.id() + ":slot:" + i, partId);
            }
        }
    }
}
