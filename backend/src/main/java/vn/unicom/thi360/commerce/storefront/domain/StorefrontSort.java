package vn.unicom.thi360.commerce.storefront.domain;

/** E11: "Mới công bố" (default), "Giá tăng dần", "Giá giảm dần". CM-2 moves the price orders to the promotion price. */
public enum StorefrontSort {
    NEWEST,
    PRICE_ASC,
    PRICE_DESC
}
