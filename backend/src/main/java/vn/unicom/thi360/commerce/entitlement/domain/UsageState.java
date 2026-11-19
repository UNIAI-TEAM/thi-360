package vn.unicom.thi360.commerce.entitlement.domain;

/** State of one attempt drawn from a grant (TM 8.5 {@code entitlement_usage.state}). */
public enum UsageState {
    RESERVED,
    USED,
    RELEASED
}
