package vn.unicom.thi360.commerce.order.application;

import java.util.Objects;
import vn.unicom.thi360.shared.security.LoginChannel;

public final class PaymentReturnPath {

    public static final String WEB_PATH = "/checkout/result";
    public static final String MOBILE_PATH = "/checkout/result/app";

    private PaymentReturnPath() {
    }

    public static String of(LoginChannel channel) {
        Objects.requireNonNull(channel, "channel must not be null");
        return switch (channel) {
            case WEB -> WEB_PATH;
            case MOBILE -> MOBILE_PATH;
        };
    }
}
