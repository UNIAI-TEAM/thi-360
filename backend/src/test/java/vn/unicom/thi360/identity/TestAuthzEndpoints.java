package vn.unicom.thi360.identity;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@TestConfiguration
public class TestAuthzEndpoints {

    @RestController
    @RequestMapping("/api/v1/admin/__test")
    public static class TestAuthzController {

        @GetMapping("/user-view")
        @RequirePermission(Permissions.USER_VIEW)
        public Map<String, String> userView() {
            return Map.of("ok", "user-view");
        }

        @PostMapping("/user-modify")
        @RequirePermission(Permissions.USER_MODIFY)
        public Map<String, String> userModify() {
            return Map.of("ok", "user-modify");
        }

        @GetMapping("/permission-users")
        @RequirePermission({Permissions.PERMISSION_VIEW, Permissions.USER_VIEW})
        public Map<String, String> permissionUsers() {
            return Map.of("ok", "permission-users");
        }
    }
}
