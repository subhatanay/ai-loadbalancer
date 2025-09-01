package com.bits.cartservice.config;

import com.bits.commomutil.tracing.TraceUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TraceInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(TraceInterceptor.class);
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Get trace ID from header or generate new one
        String traceId = TraceUtils.getOrGenerateTraceId(request.getHeader(TraceUtils.TRACE_ID_HEADER));
        TraceUtils.setTraceId(traceId);
        
        // Add trace ID to response header
        response.setHeader(TraceUtils.TRACE_ID_HEADER, traceId);
        
        logger.debug("Set trace ID: {} for request: {}", traceId, request.getRequestURI());
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Clean up MDC after request completion
        TraceUtils.clearTraceId();
    }
}
