package com.myown.damai.common.observability;

import com.myown.damai.common.web.AuthenticatedUserHeader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes trace and business identifiers for each Servlet API request.
 */
public class TraceContextFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceContextFilter.class);
    private static final Pattern ORDER_PATH = Pattern.compile("/api/orders/(\\d+)(?:/.*)?$");
    private static final Pattern PROGRAM_PATH = Pattern.compile("/api/programs/(\\d+)(?:/.*)?$");

    /**
     * Adds request identifiers to MDC and returns the trace id to the caller.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = TraceContext.resolveOrCreateTraceId(request.getHeader(TraceContext.TRACE_ID_HEADER));
        long startedAt = System.nanoTime();
        try (TraceContext.Scope ignored = TraceContext.open(
                traceId,
                request.getHeader(AuthenticatedUserHeader.USER_ID),
                resolveIdentifier(request, TraceContext.ORDER_NUMBER_HEADER, ORDER_PATH),
                resolveIdentifier(request, TraceContext.PROGRAM_ID_HEADER, PROGRAM_PATH)
        )) {
            response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
            LOGGER.info(
                    "api request completed, method={}, path={}, status={}, durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    (System.nanoTime() - startedAt) / 1_000_000
            );
        }
    }

    /**
     * Limits trace logging to business APIs so Prometheus scraping does not flood application logs.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    /**
     * Resolves an identifier from a trusted internal header, request parameter, or REST path.
     */
    private String resolveIdentifier(HttpServletRequest request, String headerName, Pattern pathPattern) {
        String headerValue = request.getHeader(headerName);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        String parameterName = TraceContext.ORDER_NUMBER_HEADER.equals(headerName) ? "orderNumber" : "programId";
        String parameterValue = request.getParameter(parameterName);
        if (parameterValue != null && !parameterValue.isBlank()) {
            return parameterValue;
        }
        Matcher matcher = pathPattern.matcher(request.getRequestURI());
        return matcher.matches() ? matcher.group(1) : null;
    }
}
