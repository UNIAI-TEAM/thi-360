package vn.unicom.thi360.classroom.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import vn.unicom.thi360.classroom.application.ClassService;
import vn.unicom.thi360.shared.api.GlobalExceptionHandler;
import vn.unicom.thi360.shared.web.LocaleConfig;

class ClassExceptionHandlerTest {

    private ClassService classService;
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
        ClassExceptionHandler classExceptionHandler =
                new ClassExceptionHandler(messageSource, globalExceptionHandler, localeResolver);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        classService = mock(ClassService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminClassController(classService))
                .setControllerAdvice(classExceptionHandler, globalExceptionHandler)
                .setValidator(validator)
                .build();
    }

    @Test
    void classCodeUniqueConstraintViolation_localizesMessage_withInvariantCode() throws Exception {
        when(classService.createClass(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate class code",
                        new ConstraintViolationException(
                                "duplicate",
                                new SQLException(
                                        "ERROR: duplicate key value violates unique constraint \"uq_class_room_code_active\"",
                                        "23505"),
                                "classroom.uq_class_room_code_active")));

        String payload = "{\"code\":\"LOP-A\",\"name\":\"Class A\"}";

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_CODE_TAKEN"))
                .andExpect(jsonPath("$.message").value("Class code already exists"));

        mockMvc.perform(post("/api/v1/admin/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_CODE_TAKEN"))
                .andExpect(jsonPath("$.message").value("Mã lớp học đã tồn tại"));

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASS_CODE_TAKEN"))
                .andExpect(jsonPath("$.message").value("Mã lớp học đã tồn tại"));
    }

    @Test
    void nonCodeIntegrityViolation_fallsBackToLocalizedInternalError() throws Exception {
        doThrow(new DataIntegrityViolationException("other constraint"))
                .when(classService)
                .createClass(any());

        String payload = "{\"code\":\"LOP-A\",\"name\":\"Class A\"}";

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An internal system error occurred, please try again later"));
    }

    @Test
    void beanValidationErrors_localizeFieldMessages_withInvariantCode() throws Exception {
        String payload = "{\"code\":\"\",\"name\":\"\"}";

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid input data"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='code')].message").value("Class code must not be blank"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')].message").value("Class name must not be blank"));

        mockMvc.perform(post("/api/v1/admin/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='code')].message").value("Mã lớp không được để trống"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')].message").value("Tên lớp không được để trống"));
    }
}
