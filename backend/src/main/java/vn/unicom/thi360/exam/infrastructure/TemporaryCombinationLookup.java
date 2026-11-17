package vn.unicom.thi360.exam.infrastructure;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.api.StructureCombination;
import vn.unicom.thi360.exam.spi.CombinationLookup;

/** Temporary exam-side adapter until catalog owns this structure lookup end-to-end. */
@Component
public class TemporaryCombinationLookup implements CombinationLookup {
    private final CatalogLookup catalog;

    public TemporaryCombinationLookup(CatalogLookup catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<StructureCombination> listCurrent() {
        return catalog.listCurrentCombinations("SUBJECT_COMBINATION").stream()
                .map(TemporaryCombinationLookup::toStructureCombination)
                .toList();
    }

    private static StructureCombination toStructureCombination(CatalogCombinationView value) {
        return new StructureCombination(value.itemCode(), value.itemNameVi(), value.itemNameEn(),
                value.examItemCode(), value.subjectCodes());
    }
}
