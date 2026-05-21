package com.univsitdown.global.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class MdcTraceFilterTest {

    private final MdcTraceFilter filter = new MdcTraceFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void 요청_처리_중_MDC에_traceId가_저장되고_응답헤더에도_포함된다() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] capturedTraceId = {null};
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws IOException, ServletException {
                capturedTraceId[0] = MDC.get("traceId");
                super.doFilter(req, res);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(capturedTraceId[0]).isNotNull().hasSize(16);
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(capturedTraceId[0]);
    }

    @Test
    void 요청_완료_후_MDC가_비워진다() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void 요청_완료_후_HTTP_요청_로그가_남는다(CapturedOutput output) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(output).contains("[HTTP] method=GET, path=/api/users/me, status=200, elapsedMs=");
    }

    @Test
    void 필터_체인_예외_발생해도_MDC가_비워진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws IOException, ServletException {
                throw new ServletException("test error");
            }
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {}

        assertThat(MDC.get("traceId")).isNull();
    }
}
