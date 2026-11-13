package vn.unicom.thi360.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.shared.api.NotFoundException;

@ExtendWith(OutputCaptureExtension.class)
@Import(TraceIdFilterTests.TraceProbeController.class)
class TraceIdFilterTests extends AbstractIntegrationTest {

    static final AtomicReference<String> OBSERVED_MDC_TRACE_ID = new AtomicReference<>();

    @RestController
    @RequestMapping("/api/v1/trace-probe")
    static class TraceProbeController {

        @GetMapping("/ok")
        String ok() {
            OBSERVED_MDC_TRACE_ID.set(MDC.get(TraceIdFilter.MDC_TRACE_ID));
            return "ok";
        }

        @GetMapping("/fail")
        String fail() {
            OBSERVED_MDC_TRACE_ID.set(MDC.get(TraceIdFilter.MDC_TRACE_ID));
            throw new NotFoundException("Không tìm thấy");
        }
    }

    @Test
    void reusesIncomingRequestId_propagatesToMdcHeaderAndApiError_clearsMdc_andLogsAccessWithoutQuery(
            CapturedOutput output) throws Exception {
        OBSERVED_MDC_TRACE_ID.set(null);

        mockMvc.perform(get("/api/v1/trace-probe/fail?secret=123")
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "incoming-trace-42"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "incoming-trace-42"))
                .andExpect(jsonPath("$.traceId").value("incoming-trace-42"));

        assertThat(OBSERVED_MDC_TRACE_ID.get()).isEqualTo("incoming-trace-42");
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();

        String accessLine = output.getOut().lines()
                .filter(l -> l.contains("thi360.access") && l.contains("trace=incoming-trace-42"))
                .findFirst()
                .orElseThrow();

        assertThat(accessLine)
                .contains("GET /api/v1/trace-probe/fail")
                .doesNotContain("secret=123")
                .contains("method=\"GET\"")
                .contains("path=\"/api/v1/trace-probe/fail\"")
                .contains("status=\"404\"")
                .contains("durationMs=\"");
    }

    @Test
    void generatesRequestIdWhenAbsent_andSkipsAccessLogForActuator(CapturedOutput output) throws Exception {
        OBSERVED_MDC_TRACE_ID.set(null);

        var pingResult = mockMvc.perform(get("/api/v1/trace-probe/ok"))
                .andExpect(status().isOk())
                .andReturn();

        String generatedId = pingResult.getResponse().getHeader(TraceIdFilter.REQUEST_ID_HEADER);
        assertThat(generatedId).isNotBlank();
        assertThat(OBSERVED_MDC_TRACE_ID.get()).isEqualTo(generatedId);
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();

        mockMvc.perform(get("/actuator/health")
                        .header(TraceIdFilter.REQUEST_ID_HEADER, "actuator-trace-99"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "actuator-trace-99"));

        assertThat(output.getOut())
                .doesNotContain("GET /actuator/health")
                .doesNotContain("trace=actuator-trace-99");
    }
}
