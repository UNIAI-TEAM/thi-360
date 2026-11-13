package vn.unicom.thi360.catalog.spi;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogLookup {

    Optional<CatalogItemView> findActiveItem(String groupCode, String itemCode);

    Optional<CatalogItemView> findItem(UUID id);

    List<UUID> findLeafIdsUnder(UUID nodeId);

    List<UUID> findLeafIds(String groupCode, Collection<UUID> refItemIds);
}
