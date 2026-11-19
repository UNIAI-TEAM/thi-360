package vn.unicom.thi360.commerce.storefront.application;

/**
 * The code names a product that was discontinued. The shared error body has no room for the product, and its
 * {@code code} is already the error code, so the public controller answers 410 with its own body carrying
 * {@code product: {code, name}} (plan B2, CM-1).
 */
public class ProductDiscontinuedException extends RuntimeException {

    private final String productCode;
    private final String productName;

    public ProductDiscontinuedException(String productCode, String productName) {
        super(StorefrontErrorCodes.PRODUCT_DISCONTINUED);
        this.productCode = productCode;
        this.productName = productName;
    }

    public String productCode() {
        return productCode;
    }

    public String productName() {
        return productName;
    }
}
