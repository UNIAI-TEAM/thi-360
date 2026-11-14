package vn.unicom.thi360.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import vn.unicom.thi360.identity.application.ImportBatchService;
import vn.unicom.thi360.identity.application.ImportExcelSchemaHelper;
import vn.unicom.thi360.shared.api.ApiError;
import vn.unicom.thi360.shared.web.LocaleConfig;

class ImportBatchLocaleTest {

    private ImportBatchService importBatchService;
    private ImportExcelSchemaHelper schemaHelper;
    private ImportBatchExceptionHandler exceptionHandler;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(LocaleConfig.DEFAULT_LOCALE);

        importBatchService = mock(ImportBatchService.class);
        schemaHelper = new ImportExcelSchemaHelper(messageSource);
        exceptionHandler = new ImportBatchExceptionHandler(messageSource, new com.fasterxml.jackson.databind.ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminImportBatchController(importBatchService))
                .setControllerAdvice(exceptionHandler)
                .setLocaleResolver(LocaleConfig.createLocaleResolver())
                .build();
    }

    @Test
    void templateControllerPassesAcceptLanguageToWorkbookBuilder() throws Exception {
        when(importBatchService.buildTemplate(any())).thenReturn(new byte[] {1});

        mockMvc.perform(get("/api/v1/admin/import-batches/template")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk());
        verify(importBatchService).buildTemplate(LocaleConfig.ENGLISH_LOCALE);

        mockMvc.perform(get("/api/v1/admin/import-batches/template"))
                .andExpect(status().isOk());
        verify(importBatchService).buildTemplate(LocaleConfig.DEFAULT_LOCALE);
    }

    @Test
    void workbookHeadersAndRowErrorsUseViAndEnBundles() {
        assertThat(schemaHelper.templateHeaders(LocaleConfig.DEFAULT_LOCALE).get(0)).isEqualTo("Tên đăng nhập*");
        assertThat(schemaHelper.templateHeaders(LocaleConfig.ENGLISH_LOCALE).get(0)).isEqualTo("Username*");
        assertThat(schemaHelper.rowErrorMessage(
                        "USERNAME_INVALID", "Tên đăng nhập", LocaleConfig.DEFAULT_LOCALE))
                .contains("Tên đăng nhập");
        assertThat(schemaHelper.rowErrorMessage(
                        "USERNAME_INVALID", "Username", LocaleConfig.ENGLISH_LOCALE))
                .contains("Username must be");
    }

    @Test
    void missingUploadErrorUsesRequestLocaleAndStableCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/admin/import-batches");
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en");

        ResponseEntity<ApiError> response = exceptionHandler.handleMissingPart(
                new MissingServletRequestPartException("file"), request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().message()).isEqualTo("The Excel file is empty or contains no valid data rows");
        assertThat(response.getBody().fieldErrors().getFirst().message())
                .isEqualTo("The Excel file is empty or contains no valid data rows");
    }
}
