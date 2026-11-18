package vn.unicom.thi360.exam.spi;

import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;

/** Reads the Global List codes of an exam kind; branch-aware via {@link CatalogCombinationView#branch()}. */
public interface CombinationLookup {
    List<CatalogCombinationView> listCombinations(UUID examItemId);
}
