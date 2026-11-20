package vn.unicom.thi360.commerce.promotion.domain;

/** One field delta recorded inside {@link PromotionChangeLog} (BS §10). */
public record PromotionFieldChange(
        String field,
        Object oldValue,
        Object newValue
) {
}
