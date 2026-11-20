package vn.unicom.thi360.commerce.promotion.domain;

/** State of one promotion redemption per order (TM 7.1, 7.2, 8.6). */
public enum RedemptionState {
    RESERVED,
    CONFIRMED,
    RELEASED
}
