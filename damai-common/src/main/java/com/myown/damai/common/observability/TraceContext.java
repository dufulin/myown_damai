package com.myown.damai.common.observability;

import com.myown.damai.common.web.AuthenticatedUserHeader;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/**
 * Defines trace headers and manages structured diagnostic fields in the logging MDC.
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String ORDER_NUMBER_HEADER = "X-Damai-Order-Number";
    public static final String PROGRAM_ID_HEADER = "X-Damai-Program-Id";
    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String ORDER_NUMBER = "orderNumber";
    public static final String PROGRAM_ID = "programId";

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    /**
     * Prevents construction of the static trace context utility.
     */
    private TraceContext() {
    }

    /**
     * Returns a safe incoming trace id or creates a new one.
     */
    public static String resolveOrCreateTraceId(String candidate) {
        if (StringUtils.hasText(candidate) && SAFE_TRACE_ID.matcher(candidate.trim()).matches()) {
            return candidate.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Returns the current trace id or creates a standalone value when no request context exists.
     */
    public static String currentOrCreateTraceId() {
        String traceId = MDC.get(TRACE_ID);
        return StringUtils.hasText(traceId) ? traceId : resolveOrCreateTraceId(null);
    }

    /**
     * Adds a trace id to the current logging context.
     */
    public static void putTraceId(Object value) {
        put(TRACE_ID, value);
    }

    /**
     * Adds a user id to the current logging context.
     */
    public static void putUserId(Object value) {
        put(USER_ID, value);
    }

    /**
     * Adds an order number to the current logging context.
     */
    public static void putOrderNumber(Object value) {
        put(ORDER_NUMBER, value);
    }

    /**
     * Adds a program id to the current logging context.
     */
    public static void putProgramId(Object value) {
        put(PROGRAM_ID, value);
    }

    /**
     * Opens an isolated MDC scope for asynchronous work such as Kafka consumption.
     */
    public static Scope open(String traceId, Object userId, Object orderNumber, Object programId) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        MDC.clear();
        putTraceId(resolveOrCreateTraceId(traceId));
        putUserId(userId);
        putOrderNumber(orderNumber);
        putProgramId(programId);
        return new Scope(previousContext);
    }

    /**
     * Copies current trace and business identifiers into an outgoing HTTP request.
     */
    public static void writeTo(HttpHeaders headers) {
        writeHeader(headers, TRACE_ID_HEADER, currentOrCreateTraceId());
        writeHeader(headers, AuthenticatedUserHeader.USER_ID, MDC.get(USER_ID));
        writeHeader(headers, ORDER_NUMBER_HEADER, MDC.get(ORDER_NUMBER));
        writeHeader(headers, PROGRAM_ID_HEADER, MDC.get(PROGRAM_ID));
    }

    /**
     * Writes one nonblank outgoing request header.
     */
    private static void writeHeader(HttpHeaders headers, String name, String value) {
        if (StringUtils.hasText(value)) {
            headers.set(name, value);
        }
    }

    /**
     * Adds one nonblank value to the MDC.
     */
    private static void put(String key, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            MDC.put(key, String.valueOf(value));
        }
    }

    /**
     * Restores the logging context that existed before asynchronous work began.
     */
    public static final class Scope implements AutoCloseable {

        private final Map<String, String> previousContext;

        /**
         * Creates a scope with the MDC state that must be restored on close.
         */
        private Scope(Map<String, String> previousContext) {
            this.previousContext = previousContext;
        }

        /**
         * Restores the previous MDC values and prevents context leakage between worker threads.
         */
        @Override
        public void close() {
            MDC.clear();
            if (previousContext != null) {
                MDC.setContextMap(previousContext);
            }
        }
    }
}
