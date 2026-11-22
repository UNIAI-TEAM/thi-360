package vn.unicom.thi360.commerce.spi;

/**
 * Where an entitlement grant came from (TM 8.5: {@code PURCHASE} from an order,
 * {@code COMPENSATION} granted by an admin for a faulted attempt).
 */
public enum GrantSource {
    PURCHASE,
    COMPENSATION,
    CLASS
}
