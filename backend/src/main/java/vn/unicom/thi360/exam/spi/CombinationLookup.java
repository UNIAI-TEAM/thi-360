package vn.unicom.thi360.exam.spi;

import java.util.List;
import vn.unicom.thi360.exam.api.StructureCombination;

/** Reads the current Global List for the structure editor; replaceable by catalog's native adapter. */
public interface CombinationLookup {
    List<StructureCombination> listCurrent();
}
