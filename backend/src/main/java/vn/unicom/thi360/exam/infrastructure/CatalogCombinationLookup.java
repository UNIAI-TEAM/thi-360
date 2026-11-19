package vn.unicom.thi360.exam.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.spi.CombinationLookup;

/** Exam-side adapter: Global List codes of an exam kind, with branch and ordered subjects. */
@Component
public class CatalogCombinationLookup implements CombinationLookup {
    private final CatalogLookup catalog;

    public CatalogCombinationLookup(CatalogLookup catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<CatalogCombinationView> listCombinations(UUID examItemId) {
        return catalog.listCombinations(examItemId);
    }
}
