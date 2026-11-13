package vn.unicom.thi360.shared.ids;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

public final class Ids {

    private Ids() {
    }

    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
