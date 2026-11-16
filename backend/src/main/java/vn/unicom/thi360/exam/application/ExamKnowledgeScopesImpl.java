package vn.unicom.thi360.exam.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.infrastructure.ExamBlockRefRepository;
import vn.unicom.thi360.exam.spi.ExamKnowledgeScopes;

@Service
class ExamKnowledgeScopesImpl implements ExamKnowledgeScopes {
    private final ExamBlockRefRepository refs;
    private final CatalogLookup catalog;

    ExamKnowledgeScopesImpl(ExamBlockRefRepository refs, CatalogLookup catalog) {
        this.refs = refs;
        this.catalog = catalog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockScope> findDraftScopes(UUID examId) {
        List<CatalogItemView> activeNodes = catalog.listActiveGroupItems("KNOWLEDGE");
        Map<UUID, CatalogItemView> byId = activeNodes.stream()
                .collect(Collectors.toMap(CatalogItemView::id, item -> item));
        Map<UUID, Set<UUID>> subtrees = new HashMap<>();
        return refs.findByExamIdAndVersionNo(examId, null).stream().map(ref -> {
            List<UUID> selected = ref.getKnowledgeItemIds();
            List<Set<UUID>> groups = selected == null ? List.of() : selected.stream()
                    .map(id -> subtrees.computeIfAbsent(id, key -> {
                        CatalogItemView root = byId.get(key);
                        if (root == null) {
                            return Set.of();
                        }
                        return activeNodes.stream().filter(node -> node.path().startsWith(root.path()))
                                .map(CatalogItemView::id).collect(Collectors.toUnmodifiableSet());
                    })).toList();
            return new BlockScope(ref.getBlockId(), ref.getKnowledgeMode(), groups);
        }).toList();
    }
}
