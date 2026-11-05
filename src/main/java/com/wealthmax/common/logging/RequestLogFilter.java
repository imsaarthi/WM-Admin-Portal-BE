package com.wealthmax.common.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * One log line per request: method, route pattern, status, duration.
 *
 * It logs the route pattern (/api/v1/subscribers/{email}), never the real URL. Path values, the query string and
 * the body can hold emails, tokens, phone numbers and CVs, and the rules forbid personal data in logs
 * (finding SEC-29). A request no route matched is logged as UNMATCHED, so a probe such as /wp-admin?id=... cannot
 * write attacker-chosen text into the log either.
 *
 * It runs right after TraceIdFilter and before the Spring Security chain, so rejected requests are logged too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    private static final String UNMATCHED = "UNMATCHED";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; // stays 500 if the chain throws
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long millis = (System.nanoTime() - started) / 1_000_000;
            String route = routeOf(request);
            if (status < 400) {
                log.info("{} {} -> {} in {} ms", request.getMethod(), route, status, millis);
            } else {
                log.warn("{} {} -> {} in {} ms", request.getMethod(), route, status, millis);
            }
        }
    }

    private static String routeOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : UNMATCHED;
    }
}
