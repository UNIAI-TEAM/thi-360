package vn.unicom.thi360.commerce.promotion.domain;

/** Audited change-log action on a promotion (BS §10). */
public enum PromotionChangeAction {
    CREATED,
    UPDATED,
    STOPPED,
    RESUMED,
    DELETED
}
