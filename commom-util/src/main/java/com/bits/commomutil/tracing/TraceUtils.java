package com.bits.commomutil.tracing;

import org.slf4j.MDC;
import java.security.SecureRandom;

/**
 * OpenTelemetry-compatible trace ID utility using 32-digit hex format
 */
public class TraceUtils {
    
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC = "traceId";
    
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * Generate OpenTelemetry-compatible 32-digit hex trace ID
     * Format: 128-bit (16 bytes) represented as 32 hex characters
     */
    public static String generateTraceId() {
        byte[] bytes = new byte[16]; // 128 bits = 16 bytes
        random.nextBytes(bytes);
        return bytesToHex(bytes);
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_MDC, traceId);
        }
    }
    
    public static String getTraceId() {
        return MDC.get(TRACE_ID_MDC);
    }
    
    public static void clearTraceId() {
        MDC.remove(TRACE_ID_MDC);
    }
    
    public static String getOrGenerateTraceId(String existingTraceId) {
        if (existingTraceId != null && isValidTraceId(existingTraceId)) {
            return existingTraceId;
        }
        return generateTraceId();
    }
    
    /**
     * Validate if trace ID follows OpenTelemetry 32-digit hex format
     */
    public static boolean isValidTraceId(String traceId) {
        return traceId != null && 
               traceId.length() == 32 && 
               traceId.matches("[0-9a-f]{32}");
    }
}
