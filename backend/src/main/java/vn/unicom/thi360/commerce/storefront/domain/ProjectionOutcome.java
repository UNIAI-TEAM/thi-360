package vn.unicom.thi360.commerce.storefront.domain;

/** What one projection of an exam did. */
public enum ProjectionOutcome {
    /** The row was inserted or updated. */
    WRITTEN,
    /** The row already holds this content, or holds a newer version (never written backwards). */
    UNCHANGED,
    /** The port has no serving version of the exam (a draft, or an unknown id): no row is made. */
    NOT_SERVED,
    /** The serving version has no listing price or attempts: it cannot be sold, so no row is made. */
    NOT_SELLABLE
}
