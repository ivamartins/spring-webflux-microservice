package com.codesolutions.observability.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds a correlation/request ID to every request and exposes it as:
 *   - MDC entry (so structured logs include it)
 *   - Response header X-Request-Id
 *
 * If the client already provides X-Request-Id, we reuse it (preserves tracing
 * across services). Otherwise we generate a UUID.
 *
 * This is essential for distributed tracing and log correlation in production.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter implements Ordered {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(MDC_KEY, requestId);
            response.setHeader(HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    public int getOrder() {
        // Run before security filters
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
