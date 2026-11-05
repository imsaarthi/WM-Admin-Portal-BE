package com.wealthmax.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Refuses to start a database test unless it would connect to a PostgreSQL database on this machine (ADR-007).
 *
 * It runs before the application starts, so before any connection and before Flyway can touch a schema. It reads
 * the datasource URL Spring actually resolved, so an override (an environment variable such as
 * SPRING_DATASOURCE_URL, a system property or an external config file) pointing at a shared database is caught too.
 * There is deliberately no switch to turn it off.
 *
 * Only the host names are written in the error message, never the whole URL, because a JDBC URL can carry a password.
 */
public class LocalDatabaseOnlyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String POSTGRES_PREFIX = "jdbc:postgresql://";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getProperty("spring.datasource.url");

        if (url == null || !url.regionMatches(true, 0, POSTGRES_PREFIX, 0, POSTGRES_PREFIX.length())) {
            throw refusal("spring.datasource.url is not a jdbc:postgresql://host/... URL");
        }

        String authority = authorityOf(url.substring(POSTGRES_PREFIX.length()));
        List<String> remoteHosts = new ArrayList<>();
        // a user-info part ("name@host") is never allowed: it is the classic way to disguise the real host
        if (authority.isEmpty() || authority.contains("@")) {
            remoteHosts.add("(unreadable host)");
        } else {
            for (String hostAndPort : authority.split(",")) {
                String host = hostOf(hostAndPort.trim());
                if (!LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                    remoteHosts.add(host.isEmpty() ? "(empty host)" : host);
                }
            }
        }

        if (!remoteHosts.isEmpty()) {
            throw refusal("spring.datasource.url points to " + String.join(", ", remoteHosts));
        }
    }

    // "host:5432/db?x=y" -> "host:5432"
    private static String authorityOf(String afterSlashes) {
        int end = afterSlashes.length();
        for (char stop : new char[] {'/', '?', '#'}) {
            int at = afterSlashes.indexOf(stop);
            if (at >= 0 && at < end) {
                end = at;
            }
        }
        return afterSlashes.substring(0, end);
    }

    // "host:5432" -> "host", "[::1]:5432" -> "[::1]"
    private static String hostOf(String hostAndPort) {
        if (hostAndPort.startsWith("[")) {
            int close = hostAndPort.indexOf(']');
            return close >= 0 ? hostAndPort.substring(0, close + 1) : hostAndPort;
        }
        int colon = hostAndPort.indexOf(':');
        return colon >= 0 ? hostAndPort.substring(0, colon) : hostAndPort;
    }

    private static IllegalStateException refusal(String reason) {
        return new IllegalStateException("Database tests only run against a PostgreSQL database on this machine "
                + "(localhost, 127.0.0.1 or ::1), but " + reason + ". Nothing was started. Check for a datasource "
                + "override such as SPRING_DATASOURCE_URL, a -Dspring.datasource.url option or an external config file.");
    }
}
