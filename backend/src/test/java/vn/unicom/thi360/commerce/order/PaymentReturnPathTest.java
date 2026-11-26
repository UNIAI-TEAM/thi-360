package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.unicom.thi360.commerce.order.application.PaymentReturnPath;
import vn.unicom.thi360.shared.api.UnauthenticatedException;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.LoginChannel;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;

class PaymentReturnPathTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsEveryLoginChannelToExpectedReturnPath() {
        assertThat(PaymentReturnPath.of(LoginChannel.WEB)).isEqualTo("/checkout/result");
        assertThat(PaymentReturnPath.of(LoginChannel.MOBILE)).isEqualTo("/checkout/result/app");
        for (LoginChannel channel : LoginChannel.values()) {
            assertThat(PaymentReturnPath.of(channel)).startsWith("/checkout/result");
        }
        assertThatThrownBy(() -> PaymentReturnPath.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void tokenDefaultsTwoArgConstructorToWebAndPreservesExplicitChannel() {
        CurrentUser user = new CurrentUser(
                UUID.randomUUID(), "learner.unit", Realm.APP, Access.EXAM, PermissionSet.empty());

        CurrentUserAuthenticationToken defaultToken = new CurrentUserAuthenticationToken(user, "sid-default");
        assertThat(defaultToken.getChannel()).isEqualTo(LoginChannel.WEB);

        CurrentUserAuthenticationToken mobileToken =
                new CurrentUserAuthenticationToken(user, "sid-mobile", LoginChannel.MOBILE);
        assertThat(mobileToken.getChannel()).isEqualTo(LoginChannel.MOBILE);

        assertThatThrownBy(() -> new CurrentUserAuthenticationToken(user, "sid-null", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void currentUserProviderRequiresAuthenticatedTokenChannel() {
        CurrentUserProvider provider = new CurrentUserProvider();
        CurrentUser user = new CurrentUser(
                UUID.randomUUID(), "learner.provider", Realm.APP, Access.EXAM, PermissionSet.empty());

        assertThatThrownBy(provider::requireLoginChannel)
                .isInstanceOf(UnauthenticatedException.class);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, ""));
        assertThatThrownBy(provider::requireLoginChannel)
                .isInstanceOf(UnauthenticatedException.class);

        SecurityContextHolder.getContext().setAuthentication(
                new CurrentUserAuthenticationToken(user, "sid-web", LoginChannel.WEB));
        assertThat(provider.requireLoginChannel()).isEqualTo(LoginChannel.WEB);

        SecurityContextHolder.getContext().setAuthentication(
                new CurrentUserAuthenticationToken(user, "sid-mobile", LoginChannel.MOBILE));
        assertThat(provider.requireLoginChannel()).isEqualTo(LoginChannel.MOBILE);
    }
}
