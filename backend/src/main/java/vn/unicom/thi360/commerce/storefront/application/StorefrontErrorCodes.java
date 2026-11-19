package vn.unicom.thi360.commerce.storefront.application;

/** Error codes of the public storefront API; the texts are in the three {@code messages*.properties}. */
public final class StorefrontErrorCodes {

    /** 404: no such product, or one that was never published (a draft looks exactly like an unknown code). */
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    /** 410: the product was on sale and has been discontinued. */
    public static final String PRODUCT_DISCONTINUED = "PRODUCT_DISCONTINUED";

    private StorefrontErrorCodes() { }
}
