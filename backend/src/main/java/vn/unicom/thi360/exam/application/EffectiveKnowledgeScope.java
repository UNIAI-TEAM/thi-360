package vn.unicom.thi360.exam.application;

import vn.unicom.thi360.exam.domain.KnowledgeScope;

/**
 * Knowledge scope a block really uses: its own competency codes and MAPIDs layered over the section's.
 *
 * <p>The block editor writes {@code {nodeIds: [], competencyCodes: [...]}} and promises that the block keeps
 * inheriting the section's MAPIDs, so each part of the scope is inherited on its own:
 *
 * <table>
 *   <caption>Effective scope</caption>
 *   <tr><th>Block scope</th><th>nodeIds and everyChildInScope</th><th>competencyCodes</th></tr>
 *   <tr><td>null</td><td>section's</td><td>section's</td></tr>
 *   <tr><td>nodeIds empty</td><td>section's</td><td>block's when non-empty, else section's</td></tr>
 *   <tr><td>nodeIds non-empty</td><td>block's</td><td>block's when non-empty, else section's</td></tr>
 * </table>
 *
 * The check, the question usage lookup and the block reference projection all use this one rule.
 */
final class EffectiveKnowledgeScope {
    private EffectiveKnowledgeScope() { }

    /** @return the scope to filter with, or null when neither the block nor the section has one */
    static KnowledgeScope of(KnowledgeScope block, KnowledgeScope section) {
        if (block == null) {
            return section;
        }
        if (section == null) {
            return block;
        }
        boolean ownNodes = !block.nodeIds().isEmpty();
        return new KnowledgeScope(
                ownNodes ? block.nodeIds() : section.nodeIds(),
                block.competencyCodes().isEmpty() ? section.competencyCodes() : block.competencyCodes(),
                ownNodes ? block.everyChildInScope() : section.everyChildInScope());
    }

    /** True when the block's MAPIDs come from the section (no scope, or a scope without nodeIds). */
    static boolean inheritsNodes(KnowledgeScope block) {
        return block == null || block.nodeIds().isEmpty();
    }
}
