package vn.unicom.thi360.exam.spi;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Knowledge criteria for question selection from an exam's current draft. */
public interface ExamKnowledgeScopes {
    /**
     * Returns the criteria for each draft block. Each selected MAPID represents its own set of
     * the selected node and its active descendants. Consumers must match ANY or ALL across these
     * sets, not flatten them before applying ALL; an empty criteria list is unrestricted.
     */
    List<BlockScope> findDraftScopes(UUID examId);

    record BlockScope(String blockId, String match, List<Set<UUID>> selectedSubtrees) {
        public BlockScope {
            selectedSubtrees = selectedSubtrees.stream().map(Set::copyOf).toList();
        }

        /** Whether a question's MAPIDs satisfy this block's knowledge criteria. */
        public boolean matches(Set<UUID> questionNodeIds) {
            if (selectedSubtrees.isEmpty()) {
                return true;
            }
            if (match == null) {
                return false;
            }
            return switch (match) {
                case "ANY" -> selectedSubtrees.stream()
                        .anyMatch(group -> group.stream().anyMatch(questionNodeIds::contains));
                case "ALL" -> selectedSubtrees.stream()
                        .allMatch(group -> group.stream().anyMatch(questionNodeIds::contains));
                default -> false;
            };
        }
    }
}
