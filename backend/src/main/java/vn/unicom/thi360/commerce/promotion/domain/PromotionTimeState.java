package vn.unicom.thi360.commerce.promotion.domain;

import java.time.Instant;

/** Derived time-window state of a promotion over {@code [starts_at, ends_at)} (TM 7.2, 7.3). */
public enum PromotionTimeState {
    UPCOMING,
    RUNNING,
    ENDED;

    public static PromotionTimeState at(Instant now, Instant startsAt, Instant endsAt) {
        if (now.isBefore(startsAt)) {
            return UPCOMING;
        }
        if (now.isBefore(endsAt)) {
            return RUNNING;
        }
        return ENDED;
    }
}
