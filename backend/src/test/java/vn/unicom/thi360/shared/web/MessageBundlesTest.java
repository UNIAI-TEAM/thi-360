package vn.unicom.thi360.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.unicom.thi360.identity.api.PermissionSourceResponse;
import vn.unicom.thi360.identity.api.PermissionUserResponse;
import vn.unicom.thi360.identity.application.PermissionUsersExportWriter;
import vn.unicom.thi360.identity.domain.UserStatus;

class MessageBundlesTest {

    private static final Pattern VIETNAMESE_DIACRITICS = Pattern.compile(
            "[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"
                    + "ÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)\\}");

    @Test
    void bundlesHaveIdenticalNonEmptyKeys_matchingPlaceholders_andNoVietnameseInEnglish() throws IOException {
        Properties base = loadUtf8Properties("messages.properties");
        Properties vi = loadUtf8Properties("messages_vi.properties");
        Properties en = loadUtf8Properties("messages_en.properties");

        Set<String> baseKeys = new TreeSet<>(base.stringPropertyNames());
        Set<String> viKeys = new TreeSet<>(vi.stringPropertyNames());
        Set<String> enKeys = new TreeSet<>(en.stringPropertyNames());

        assertThat(baseKeys).isNotEmpty();
        assertThat(viKeys).as("messages_vi.properties keys must match messages.properties").isEqualTo(baseKeys);
        assertThat(enKeys).as("messages_en.properties keys must match messages.properties").isEqualTo(baseKeys);

        for (String key : baseKeys) {
            String baseVal = base.getProperty(key);
            String viVal = vi.getProperty(key);
            String enVal = en.getProperty(key);

            assertThat(baseVal).as("messages.properties key '%s' must be non-blank", key).isNotBlank();
            assertThat(viVal).as("messages_vi.properties key '%s' must be non-blank", key).isNotBlank();
            assertThat(enVal).as("messages_en.properties key '%s' must be non-blank", key).isNotBlank();
            assertThat(viVal).as("messages_vi.properties key '%s' must match messages.properties", key).isEqualTo(baseVal);
            assertThat(VIETNAMESE_DIACRITICS.matcher(enVal).find())
                    .as("messages_en.properties key '%s' must not contain Vietnamese diacritics: %s", key, enVal)
                    .isFalse();
            assertThat(extractPlaceholders(enVal))
                    .as("Placeholders in messages_en.properties key '%s' must match messages.properties", key)
                    .isEqualTo(extractPlaceholders(baseVal));
        }
    }

    @Test
    void localeResolverSupportsOnlyViAndEn_neverFallsBackToJvmLocale() {
        Locale previousDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertResolves(null, "vi");
            assertResolves("", "vi");
            assertResolves("   ", "vi");
            assertResolves("fr", "vi");
            assertResolves("fr-FR", "vi");
            assertResolves("*", "vi");
            assertResolves("invalid;;;", "vi");
            assertResolves("en;q=0,vi;q=0.5", "vi");
            assertResolves("vi", "vi");
            assertResolves("vi-VN,vi;q=0.9,en;q=0.8", "vi");
            assertResolves("en", "en");
            assertResolves("en-US,en;q=0.9", "en");
            assertResolves("fr-FR,en;q=0.8", "en");

            Locale.setDefault(Locale.FRANCE);
            assertResolves(null, "vi");
            assertResolves("en", "en");

            AppMessages messages = AppMessages.defaultInstance();
            assertThat(messages.get(LocaleConfig.DEFAULT_LOCALE, "NOT_FOUND"))
                    .isEqualTo("Không tìm thấy dữ liệu yêu cầu");
            assertThat(messages.get(LocaleConfig.ENGLISH_LOCALE, "NOT_FOUND"))
                    .isEqualTo("Requested resource was not found");
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    void permissionUsersExportWriter_localizesHeadersStatusesAndSources() throws IOException {
        PermissionUsersExportWriter writer = new PermissionUsersExportWriter(AppMessages.defaultInstance());
        List<PermissionUserResponse> rows = List.of(
                new PermissionUserResponse(
                        UUID.randomUUID(),
                        "U0001",
                        "ops.admin",
                        "Ops Admin",
                        "ops@thi360.vn",
                        UserStatus.ACTIVE,
                        List.of(
                                PermissionSourceResponse.role(UUID.randomUUID(), "OPERATIONS", "Operations"),
                                PermissionSourceResponse.direct(UUID.randomUUID(), "sys.admin", Instant.now(), "Test"))),
                new PermissionUserResponse(
                        UUID.randomUUID(),
                        "U0002",
                        "pending.user",
                        "Pending",
                        null,
                        UserStatus.PROVISIONING,
                        List.of(PermissionSourceResponse.direct(null, null, Instant.now(), "Init"))));

        byte[] enBytes = writer.write("USER.VIEW", rows, LocaleConfig.ENGLISH_LOCALE);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(enBytes))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Username");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Full name");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Email");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Status");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Permission sources");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Active");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue())
                    .isEqualTo("Role: Operations; Direct grant (sys.admin)");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Provisioning");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("Direct grant");
        }

        byte[] viBytes = writer.write("USER.VIEW", rows, LocaleConfig.DEFAULT_LOCALE);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(viBytes))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Tên đăng nhập");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Trạng thái");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Hoạt động");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue())
                    .isEqualTo("Vai trò: Operations; Gán trực tiếp (sys.admin)");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Chờ kích hoạt");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("Gán trực tiếp");
        }
    }

    private static void assertResolves(String acceptLanguage, String expectedLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        assertThat(LocaleConfig.resolveLocale(request).getLanguage()).isEqualTo(expectedLanguage);
    }

    private static Properties loadUtf8Properties(String resourceName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourceName);
        assertThat(resource.exists()).as("Resource %s must exist", resourceName).isTrue();
        Properties props = new Properties();
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }

    private static Set<String> extractPlaceholders(String text) {
        Set<String> placeholders = new HashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }
}
