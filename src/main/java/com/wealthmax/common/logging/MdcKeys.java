package com.wealthmax.common.logging;

/** Names of the entries the application puts in the logging context (MDC). */
public final class MdcKeys {

    /** One id per request. It appears in every log line and in the traceId of every error body. */
    public static final String TRACE_ID = "traceId";

    private MdcKeys() {
    }
}
