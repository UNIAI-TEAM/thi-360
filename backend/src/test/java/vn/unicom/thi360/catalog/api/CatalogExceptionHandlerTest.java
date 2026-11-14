package vn.unicom.thi360.catalog.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import vn.unicom.thi360.catalog.application.CatalogAdminService;
import vn.unicom.thi360.catalog.application.CatalogExcelService;
import vn.unicom.thi360.shared.api.GlobalExceptionHandler;
import vn.unicom.thi360.catalog.application.CatalogConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.web.LocaleConfig;

class CatalogExceptionHandlerTest {

    private CatalogAdminService catalogAdminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(LocaleConfig.DEFAULT_LOCALE);

        LocaleResolver localeResolver = LocaleConfig.createLocaleResolver();
        GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler(messageSource, localeResolver);
        CatalogExceptionHandler catalogExceptionHandler =
                new CatalogExceptionHandler(messageSource, globalExceptionHandler, localeResolver);

        catalogAdminService = mock(CatalogAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminCatalogController(catalogAdminService, mock(CatalogExcelService.class)))
                .setControllerAdvice(catalogExceptionHandler, globalExceptionHandler)
                .build();
    }

    @Test
    void groupCodeUniqueConstraintViolation_localizesMessage_withInvariantCode() throws Exception {
        when(catalogAdminService.createGroup(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate group code",
                        new ConstraintViolationException(
                                "duplicate",
                                new SQLException(
                                        "ERROR: duplicate key value violates unique constraint \"uq_catalog_group_code_normalized\"",
                                        "23505"),
                                "catalog.uq_catalog_group_code_normalized")));

        String payload = "{\"code\":\"DUP_CODE\",\"nameVi\":\"Nhóm trùng\"}";

        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"))
                .andExpect(jsonPath("$.message").value("Catalog group or item code already exists"));

        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"))
                .andExpect(jsonPath("$.message").value("Mã danh mục hoặc giá trị đã tồn tại"));

        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"))
                .andExpect(jsonPath("$.message").value("Mã danh mục hoặc giá trị đã tồn tại"));
    }

    @Test
    void itemCodeSqlStateUniqueConstraintViolation_andNonCodeIntegrityError_areLocalized() throws Exception {
        when(catalogAdminService.createGroup(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate item code",
                        new SQLException(
                                "ERROR: duplicate key value violates unique constraint \"uq_catalog_item_group_code_normalized\"",
                                "23505")));

        String payload = "{\"code\":\"DUP_ITEM\",\"nameVi\":\"Giá trị trùng\"}";

        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"))
                .andExpect(jsonPath("$.message").value("Catalog group or item code already exists"));

        doThrow(new DataIntegrityViolationException("other constraint"))
                .when(catalogAdminService)
                .createGroup(any());

        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An internal system error occurred, please try again later"));
    }

    @Test
    void catalogConflictException_andMaxUploadSizeExceeded_areLocalized() throws Exception {
        UUID itemId = UUID.randomUUID();
        doThrow(new CatalogConflictException(
                "CATALOG_ITEM_IN_USE",
                List.of(new FieldError("SUBJECT_COMBINATION", "BLOCKING", "Subject combination (1)"))))
                .when(catalogAdminService)
                .deleteItem(any(), any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/catalog/items/{id}", itemId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .param("reason", "cleanup"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_IN_USE"))
                .andExpect(jsonPath("$.message").value("Cannot perform action because the catalog item is currently in use"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("SUBJECT_COMBINATION"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/catalog/items/{id}", itemId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                        .param("reason", "cleanup"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_IN_USE"))
                .andExpect(jsonPath("$.message").value("Không thể thực hiện thao tác vì giá trị danh mục đang được sử dụng"));

        doThrow(new MaxUploadSizeExceededException(5L * 1024L * 1024L))
                .when(catalogAdminService)
                .createGroup(any());

        String payload = "{\"code\":\"ANY\",\"nameVi\":\"Any\"}";
        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Excel file size exceeds the allowed limit (maximum 5 MB)"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Excel file size exceeds the allowed limit (maximum 5 MB)"));

        mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Dung lượng tệp Excel vượt quá giới hạn cho phép (tối đa 5 MB)"));
    }
}
