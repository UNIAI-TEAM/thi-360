package vn.unicom.thi360.commerce.bundle.application;

/** Error codes emitted by bundle admin operations (TM 16.2, 16.4, AC-2..AC-8, AC-14). */
public final class BundleErrorCodes {

    public static final String BUNDLE_NOT_FOUND = "BUNDLE_NOT_FOUND";
    public static final String BUNDLE_CODE_TAKEN = "BUNDLE_CODE_TAKEN";
    public static final String BUNDLE_ITEMS_COUNT_INVALID = "BUNDLE_ITEMS_COUNT_INVALID";
    public static final String BUNDLE_ITEM_DUPLICATE = "BUNDLE_ITEM_DUPLICATE";
    public static final String BUNDLE_ITEM_INVALID_EXAM = "BUNDLE_ITEM_INVALID_EXAM";
    public static final String BUNDLE_ITEM_KIND_MISMATCH = "BUNDLE_ITEM_KIND_MISMATCH";
    public static final String BUNDLE_ITEM_FULL_SCOPE_NOT_ALLOWED = "BUNDLE_ITEM_FULL_SCOPE_NOT_ALLOWED";
    public static final String BUNDLE_ITEM_NOT_ON_SALE = "BUNDLE_ITEM_NOT_ON_SALE";
    public static final String BUNDLE_ATTEMPTS_INVALID = "BUNDLE_ATTEMPTS_INVALID";
    public static final String BUNDLE_DESCRIPTION_REQUIRED = "BUNDLE_DESCRIPTION_REQUIRED";
    public static final String BUNDLE_ALREADY_DISCONTINUED = "BUNDLE_ALREADY_DISCONTINUED";
    public static final String BUNDLE_NOT_PUBLISHED = "BUNDLE_NOT_PUBLISHED";
    public static final String BUNDLE_EXAM_KIND_INVALID = "BUNDLE_EXAM_KIND_INVALID";
    public static final String BUNDLE_RESULT_VIEW_INVALID = "BUNDLE_RESULT_VIEW_INVALID";
    public static final String BUNDLE_LIST_PRICE_NEGATIVE = "BUNDLE_LIST_PRICE_NEGATIVE";

    /** Reused from exam listing rules per TM 16.2 & AC-7. */
    public static final String LISTING_PRICE_MISSING = "LISTING_PRICE_MISSING";
    public static final String LISTING_PRICE_ZERO = "LISTING_PRICE_ZERO";
    public static final String LISTING_PRICE_BELOW_MIN = "LISTING_PRICE_BELOW_MIN";

    private BundleErrorCodes() {
    }
}
