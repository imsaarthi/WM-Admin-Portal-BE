package com.wealthmax.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("without an incoming id, a new one is made, logged for the request and returned in X-Trace-Id")
    void generatesAnIdWhenNoneIsSent() throws Exception {
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, chainThatCaptures(seenInsideChain));

        assertThat(seenInsideChain.get()).isNotBlank();
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(seenInsideChain.get());
    }

    @Test
    @DisplayName("a well-formed incoming X-Request-Id is reused, so one id follows a call across systems")
    void reusesAWellFormedIncomingId() throws Exception {
        request.addHeader("X-Request-Id", "abc-12345678");
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, chainThatCaptures(seenInsideChain));

        assertThat(seenInsideChain.get()).isEqualTo("abc-12345678");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("abc-12345678");
    }

    @Test
    @DisplayName("an incoming id that could forge log lines or is too long is replaced, never trusted")
    void replacesAnUnsafeIncomingId() throws Exception {
        for (String unsafe : new String[] {"short", "has spaces in it 123", "line1\nFAKE LOG ENTRY",
                "<script>alert(1)</script>", "a".repeat(65)}) {
            MockHttpServletResponse each = new MockHttpServletResponse();
            MockHttpServletRequest unsafeRequest = new MockHttpServletRequest("GET", "/anything");
            unsafeRequest.addHeader("X-Request-Id", unsafe);

            filter.doFilter(unsafeRequest, each, new MockFilterChain());

            assertThat(each.getHeader("X-Trace-Id")).isNotEqualTo(unsafe).matches("[A-Za-z0-9-]{8,64}");
        }
    }

    @Test
    @DisplayName("the id is removed from the logging context when the request ends")
    void clearsTheContextAfterTheRequest() throws Exception {
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("the id is removed even when the request fails, so it cannot leak into the next request on the thread")
    void clearsTheContextWhenTheChainThrows() {
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws ServletException {
                throw new ServletException("boom");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failing)).isInstanceOf(ServletException.class);
        assertThat(MDC.get(MdcKeys.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("it runs before the Spring Security filter chain (order -100), so rejected requests are logged with an id too")
    void runsBeforeTheSecurityChain() {
        int securityChainOrder = -100;

        assertThat(TraceIdFilter.class.getAnnotation(Order.class).value()).isLessThan(securityChainOrder);
    }

    private static MockFilterChain chainThatCaptures(AtomicReference<String> target) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                target.set(MDC.get(MdcKeys.TRACE_ID));
            }
        };
    }
}
