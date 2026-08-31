package com.glv.gsysportal.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §8/§9): Correlation ID propagation + one Structured Log line per request.
 *
 * <ul>
 *   <li>Reads an incoming {@code X-Correlation-Id} request header if present
 *       and shaped safely (bounded length, restricted charset - never
 *       reflects an arbitrary client-supplied string into logs/response
 *       headers unchecked); otherwise generates a new random one.</li>
 *   <li>Exposes it as a request attribute ({@link #currentCorrelationId})
 *       for {@code GlobalExceptionHandler}'s Structured Error Response, and
 *       via MDC so every log line for the duration of the request - not
 *       just the one this Filter itself writes - carries it (see
 *       {@code logging.pattern.console} in application.yml).</li>
 *   <li>Logs exactly one summary line after the chain completes: HTTP
 *       method+path (operation), response status (result), and the
 *       technical error code {@code GlobalExceptionHandler} recorded, if
 *       any. Deliberately never logs request/response bodies, headers, or
 *       query parameters - Production Readiness Audit §9's explicit
 *       prohibition on Password/Secret/Supplier email body/Business
 *       payload in logs.</li>
 * </ul>
 *
 * Runs at {@link Ordered#HIGHEST_PRECEDENCE} so every request - including
 * ones Spring Security rejects with 401/403 - gets a correlation ID and a
 * log line.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String REQUEST_ATTRIBUTE = "correlationId";
    /** Set by GlobalExceptionHandler when it handles an exception; read back
     * here so the per-request summary line includes the technical error code
     * without the two classes needing a shared service/bean. */
    public static final String ERROR_CODE_ATTRIBUTE = "technicalErrorCode";

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";
    // Conservative allowlist for a client-supplied id - never reflect an
    // arbitrary header value verbatim into logs/response headers unchecked.
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    public static String currentCorrelationId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        return value != null ? value.toString() : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(CORRELATION_HEADER);
        String correlationId = (incoming != null && SAFE_ID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();

        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        long startedAtMillis = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = System.currentTimeMillis() - startedAtMillis;
            Object errorCode = request.getAttribute(ERROR_CODE_ATTRIBUTE);
            log.info("operation={} {} result={} errorCode={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    errorCode != null ? errorCode : "-", durationMillis);
            MDC.remove(MDC_KEY);
        }
    }
}
