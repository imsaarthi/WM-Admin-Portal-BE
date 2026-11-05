package com.wealthmax.common.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request one id, puts it in the logging context and returns it in the X-Trace-Id header.
 *
 * It runs first, before the Spring Security chain (order -100), so a request that security rejects is
 * logged with an id as well (findings SEC-29, B14).
 *
 * A client may send its own X-Request-Id so one call can be followed across systems. It is used only if it is
 * 8 to 64 letters, digits or dashes. Anything else is replaced: an id with a line break would let a client
 * forge log lines.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String traceId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        MDC.put(MdcKeys.TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are reused. Without this the next request on this thread would log under this id.
            MDC.remove(MdcKeys.TRACE_ID);
        }
    }
}
