package com.wealthmax.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();
    private ListAppender<ILoggingEvent> logs;
    private Logger filterLogger;

    @BeforeEach
    void attachAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        logs = new ListAppender<>();
        logs.start();
        filterLogger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(logs);
    }

    @Test
    @DisplayName("one line per request: method, matched route pattern, status and duration")
    void logsMethodRouteStatusAndDuration() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs/42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainThatRoutesTo("/api/v1/jobs/{id}", 200));

        assertThat(logs.list).hasSize(1);
        String line = logs.list.get(0).getFormattedMessage();
        assertThat(line).contains("GET", "/api/v1/jobs/{id}", "200", "ms");
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("the real URL, the query string and the body never reach the log (they can hold emails, tokens and CVs)")
    void neverLogsUrlQueryOrBody() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/subscribers/jane.doe@example.test/confirm");
        request.setQueryString("email=jane.doe@example.test&token=s3cr3t-token");
        request.setContent("{\"email\":\"jane.doe@example.test\",\"phone\":\"+447700900123\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainThatRoutesTo("/api/v1/subscribers/{email}/confirm", 201));

        String everything = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);
        assertThat(everything)
                .contains("/api/v1/subscribers/{email}/confirm")
                .doesNotContain("jane.doe")
                .doesNotContain("example.test")
                .doesNotContain("s3cr3t-token")
                .doesNotContain("token=")
                .doesNotContain("+447700900123");
    }

    @Test
    @DisplayName("a request no route matched is logged as UNMATCHED, never with its raw path")
    void unmatchedRequestsHideTheirPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wp-admin/probe?id=jane@example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chainThatRoutesTo(null, 404));

        String line = logs.list.get(0).getFormattedMessage();
        assertThat(line).contains("UNMATCHED", "404").doesNotContain("wp-admin").doesNotContain("jane");
    }

    @Test
    @DisplayName("a 4xx or 5xx response is logged at WARN")
    void errorStatusIsLoggedAtWarn() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(),
                chainThatRoutesTo("/x", 403));

        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("a request that blows up is still logged, as a 500")
    void failingRequestIsStillLogged() {
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws ServletException {
                throw new ServletException("boom");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/x"),
                new MockHttpServletResponse(), failing)).isInstanceOf(ServletException.class);

        List<ILoggingEvent> events = logs.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage()).contains("500");
        assertThat(events.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("it runs right after TraceIdFilter and before the security chain, so rejected requests are logged too")
    void orderIsBetweenTraceIdAndSecurity() {
        int traceIdOrder = TraceIdFilter.class.getAnnotation(Order.class).value();
        int requestLogOrder = RequestLogFilter.class.getAnnotation(Order.class).value();

        assertThat(requestLogOrder).isGreaterThan(traceIdOrder).isLessThan(-100);
    }

    private static MockFilterChain chainThatRoutesTo(String routePattern, int status) {
        return new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                if (routePattern != null) {
                    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, routePattern);
                }
                ((HttpServletResponse) response).setStatus(status);
            }
        };
    }
}
