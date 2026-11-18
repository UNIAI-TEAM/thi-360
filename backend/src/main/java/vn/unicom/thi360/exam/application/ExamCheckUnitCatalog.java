package vn.unicom.thi360.exam.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogItemRefView;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;

/** Filters only the current part's broken code options while the strict builder visits each part. */
final class ExamCheckUnitCatalog implements CatalogLookup {
    private final CatalogLookup delegate;
    private final List<Set<String>> excludedByPart;
    private int partIndex;

    ExamCheckUnitCatalog(CatalogLookup delegate, List<Set<String>> excludedByPart) {
        this.delegate = delegate;
        this.excludedByPart = excludedByPart;
    }

    @Override public long revision() { return delegate.revision(); }
    @Override public List<CatalogItemView> listActiveGroupItems(String group) { return delegate.listActiveGroupItems(group); }
    @Override public List<CatalogItemView> listGroupItems(String group) { return delegate.listGroupItems(group); }
    @Override public Map<UUID, List<CatalogItemRefView>> findEffectiveItemRefs(Collection<UUID> ids) { return delegate.findEffectiveItemRefs(ids); }
    @Override public List<CatalogCombinationView> listCurrentCombinations(String group) { return delegate.listCurrentCombinations(group); }
    @Override public List<CatalogCombinationView> listCombinations(UUID examId) {
        Set<String> excluded = excludedByPart.get(partIndex++);
        return delegate.listCombinations(examId).stream().filter(item -> !excluded.contains(item.itemCode())).toList();
    }
    @Override public Optional<CatalogItemView> findActiveItem(String group, String code) { return delegate.findActiveItem(group, code); }
    @Override public Optional<CatalogItemView> findItem(UUID id) { return delegate.findItem(id); }
    @Override public List<UUID> findLeafIdsUnder(UUID id) { return delegate.findLeafIdsUnder(id); }
    @Override public List<UUID> findLeafIds(String group, Collection<UUID> ids) { return delegate.findLeafIds(group, ids); }
}
