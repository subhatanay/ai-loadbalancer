package com.bits.loadbalancer.services;

import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BenchmarkController - Automates load balancer algorithm switching for performance comparison
 * 
 * This controller enables A/B testing by:
 * 1. Automatically switching between different routing algorithms (RL, round-robin, connection-aware)
 * 2. Tracking performance metrics for each algorithm during active periods
 * 3. Providing REST APIs to control and monitor benchmarking tests
 * 4. Ensuring fair comparison by giving each algorithm identical test conditions
 */
@RestController
@RequestMapping("/api/benchmark")
@Service
public class BenchmarkController {
    
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkController.class);
    
    // Available algorithms for testing
    private final String[] testAlgorithms = {"rl-agent", "round-robin", "least-connections"};
    
    // Benchmark state variables
    private volatile boolean benchmarkMode = false;
    private volatile String currentAlgorithm = "rl-agent";
    private volatile int currentTestPhase = 0;
    private volatile long testStartTime = 0;
    private volatile int testDurationMinutes = 60;
    
    
    // Performance tracking per algorithm
    private final Map<String, AlgorithmMetrics> algorithmMetrics = new ConcurrentHashMap<>();
    
    @Autowired
    private RoutingStrategyAlgorithm routingStrategyAlgorithm;
    
    /**
     * Container for algorithm-specific performance metrics
     */
    public static class AlgorithmMetrics {
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        private long startTime = System.currentTimeMillis();
        private long endTime = 0;
        
        public void recordRequest(long responseTimeMs, boolean isError) {
            requestCount.incrementAndGet();
            if (isError) {
                errorCount.incrementAndGet();
            }
            responseTimes.add(responseTimeMs);
        }
        
        public void endPhase() {
            this.endTime = System.currentTimeMillis();
        }
        
        // Getters
        public long getRequestCount() { return requestCount.get(); }
        public long getErrorCount() { return errorCount.get(); }
        public List<Long> getResponseTimes() { return new ArrayList<>(responseTimes); }
        public double getErrorRate() { 
            return requestCount.get() > 0 ? (double) errorCount.get() / requestCount.get() * 100 : 0; 
        }
        public double getAverageResponseTime() {
            return responseTimes.isEmpty() ? 0 : 
                responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        }
        public long getDurationMs() { 
            return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime; 
        }
    }
    
    /**
     * Start a benchmarking test
     * @param durationMinutes Total test duration in minutes
     * @param startAlgorithm Starting algorithm for the test
     * @return Test status and configuration
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startBenchmark(
            @RequestParam(value = "durationMinutes", defaultValue = "60") int durationMinutes,
            @RequestParam(value = "startAlgorithm", defaultValue = "rl-agent") String startAlgorithm) {
        
        if (benchmarkMode) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Benchmark already running"));
        }
        
        logger.info("Starting benchmark - Duration: {} minutes, Starting algorithm: {}", 
                   durationMinutes, startAlgorithm);
        
        // Initialize benchmark state
        this.testDurationMinutes = durationMinutes;
        this.currentAlgorithm = startAlgorithm;
        this.currentTestPhase = 0;
        this.testStartTime = System.currentTimeMillis();
        this.benchmarkMode = true;
        
        // Clear previous metrics
        algorithmMetrics.clear();
        for (String algorithm : testAlgorithms) {
            algorithmMetrics.put(algorithm, new AlgorithmMetrics());
        }
        
        // Set the load balancer to use the starting algorithm
        System.setProperty("loadbalancer.routing-strategy", currentAlgorithm);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("duration_minutes", durationMinutes);
        response.put("current_algorithm", currentAlgorithm);
        response.put("test_algorithms", testAlgorithms);
        response.put("start_time", testStartTime);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stop the current benchmarking test
     * @return Final test results
     */
    @PostMapping("/stop")
    public Map<String, Object> stopBenchmark() {
        if (!benchmarkMode) {
            return createErrorResponse("No benchmark currently running");
        }
        
        logger.info("Stopping benchmark test");
        
        // End current phase
        if (algorithmMetrics.containsKey(currentAlgorithm)) {
            algorithmMetrics.get(currentAlgorithm).endPhase();
        }
        
        benchmarkMode = false;
        
        // Generate final report
        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("totalDurationMs", System.currentTimeMillis() - testStartTime);
        response.put("results", generateBenchmarkReport());
        
        return response;
    }
    
    /**
     * Get current benchmark status
     * @return Current test status and metrics
     */
    @GetMapping("/status")
    public Map<String, Object> getBenchmarkStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("benchmarkActive", benchmarkMode);
        status.put("currentAlgorithm", currentAlgorithm);
        status.put("currentPhase", currentTestPhase + 1);
        status.put("totalPhases", testAlgorithms.length);
        status.put("testAlgorithms", testAlgorithms);
        
        if (benchmarkMode) {
            long elapsed = System.currentTimeMillis() - testStartTime;
            status.put("elapsedTimeMs", elapsed);
            status.put("elapsedTimeMinutes", elapsed / 60000.0);
        }
        
        // Current algorithm metrics
        AlgorithmMetrics currentMetrics = algorithmMetrics.get(currentAlgorithm);
        if (currentMetrics != null) {
            Map<String, Object> currentStats = new HashMap<>();
            currentStats.put("requestCount", currentMetrics.getRequestCount());
            currentStats.put("errorCount", currentMetrics.getErrorCount());
            currentStats.put("errorRate", currentMetrics.getErrorRate());
            currentStats.put("averageResponseTime", currentMetrics.getAverageResponseTime());
            status.put("currentAlgorithmStats", currentStats);
        }
        
        return status;
    }
    
    /**
     * Switch to a specific algorithm for fair benchmarking
     * @param algorithm Algorithm to switch to
     * @return Switch status
     */
    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchAlgorithm(@RequestParam("algorithm") String algorithm) {
        Map<String, Object> response = new HashMap<>();
        
        // Validate algorithm
        boolean validAlgorithm = false;
        for (String testAlgorithm : testAlgorithms) {
            if (testAlgorithm.equals(algorithm)) {
                validAlgorithm = true;
                break;
            }
        }
        
        if (!validAlgorithm) {
            response.put("error", "Invalid algorithm: " + algorithm);
            response.put("availableAlgorithms", testAlgorithms);
            return ResponseEntity.badRequest().body(response);
        }
        
        // Switch algorithm
        currentAlgorithm = algorithm;
        
        // Directly update the routing strategy in RoutingStrategyAlgorithm
        routingStrategyAlgorithm.setRoutingStrategy(algorithm);
        
        // Initialize metrics if not exists
        if (!algorithmMetrics.containsKey(algorithm)) {
            algorithmMetrics.put(algorithm, new AlgorithmMetrics());
        }
        
        logger.info("Manually switched to algorithm: {}", algorithm);
        
        response.put("status", "switched");
        response.put("currentAlgorithm", algorithm);
        response.put("message", "Successfully switched to " + algorithm);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reset algorithm metrics for fair benchmarking
     * @param algorithm Algorithm to reset metrics for (optional, resets current if not specified)
     * @return Reset status
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetAlgorithmMetrics(@RequestParam(value = "algorithm", required = false) String algorithm) {
        Map<String, Object> response = new HashMap<>();
        
        String targetAlgorithm = algorithm != null ? algorithm : currentAlgorithm;
        
        // Validate algorithm if specified
        if (algorithm != null) {
            boolean validAlgorithm = false;
            for (String testAlgorithm : testAlgorithms) {
                if (testAlgorithm.equals(algorithm)) {
                    validAlgorithm = true;
                    break;
                }
            }
            
            if (!validAlgorithm) {
                response.put("error", "Invalid algorithm: " + algorithm);
                response.put("availableAlgorithms", testAlgorithms);
                return ResponseEntity.badRequest().body(response);
            }
        }
        
        // Reset metrics for the target algorithm
        algorithmMetrics.put(targetAlgorithm, new AlgorithmMetrics());
        
        logger.info("Reset metrics for algorithm: {}", targetAlgorithm);
        
        response.put("status", "reset");
        response.put("algorithm", targetAlgorithm);
        response.put("message", "Successfully reset metrics for " + targetAlgorithm);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get detailed benchmark results
     * @return Comprehensive performance comparison
     */
    @GetMapping("/results")
    public Map<String, Object> getBenchmarkResults() {
        Map<String, Object> response = new HashMap<>();
        response.put("benchmarkActive", benchmarkMode);
        response.put("results", generateBenchmarkReport());
        return response;
    }
    
    
    
    /**
     * Record a request for the current algorithm (called by LoadBalancerController)
     * @param responseTimeMs Response time in milliseconds
     * @param isError Whether the request resulted in an error
     */
    public void recordRequest(long responseTimeMs, boolean isError, String requestPath) {
        if (requestPath != null && (requestPath.contains("/actuator/") || requestPath.contains("/health"))) {
            return;
        }
        if (benchmarkMode && algorithmMetrics.containsKey(currentAlgorithm)) {
            AlgorithmMetrics metrics = algorithmMetrics.get(currentAlgorithm);
            metrics.recordRequest(responseTimeMs, isError);
            
            // Log progress every 100 requests for monitoring
            if (metrics.getRequestCount() % 100 == 0) {
                logger.info("Algorithm: {} - Requests: {}, Avg Response: {:.2f}ms, Error Rate: {:.2f}%",
                    currentAlgorithm, metrics.getRequestCount(), 
                    metrics.getAverageResponseTime(), metrics.getErrorRate());
            }
        }
    }
    
    /**
     * Generate comprehensive benchmark report
     */
    private Map<String, Object> generateBenchmarkReport() {
        Map<String, Object> report = new HashMap<>();
        Map<String, Map<String, Object>> algorithmResults = new HashMap<>();
        
        for (Map.Entry<String, AlgorithmMetrics> entry : algorithmMetrics.entrySet()) {
            String algorithm = entry.getKey();
            AlgorithmMetrics metrics = entry.getValue();
            
            Map<String, Object> algorithmReport = new HashMap<>();
            algorithmReport.put("requestCount", metrics.getRequestCount());
            algorithmReport.put("errorCount", metrics.getErrorCount());
            algorithmReport.put("errorRate", metrics.getErrorRate());
            algorithmReport.put("averageResponseTime", metrics.getAverageResponseTime());
            algorithmReport.put("durationMs", metrics.getDurationMs());
            
            // Calculate percentiles if we have response times
            List<Long> responseTimes = metrics.getResponseTimes();
            if (!responseTimes.isEmpty()) {
                Collections.sort(responseTimes);
                int size = responseTimes.size();
                algorithmReport.put("p50ResponseTime", responseTimes.get(size / 2));
                algorithmReport.put("p95ResponseTime", responseTimes.get((int) (size * 0.95)));
                algorithmReport.put("p99ResponseTime", responseTimes.get((int) (size * 0.99)));
                algorithmReport.put("minResponseTime", responseTimes.get(0));
                algorithmReport.put("maxResponseTime", responseTimes.get(size - 1));
            }
            
            algorithmResults.put(algorithm, algorithmReport);
        }
        
        report.put("algorithmResults", algorithmResults);
        report.put("testConfiguration", Map.of(
            "algorithms", testAlgorithms,
            "durationMinutes", testDurationMinutes,
            "startTime", new Date(testStartTime)
        ));
        
        // Add performance comparison
        report.put("performanceComparison", generatePerformanceComparison(algorithmResults));
        
        return report;
    }
    
    /**
     * Generate performance comparison between algorithms
     */
    private Map<String, Object> generatePerformanceComparison(Map<String, Map<String, Object>> results) {
        Map<String, Object> comparison = new HashMap<>();
        
        if (results.size() < 2) {
            comparison.put("message", "Need at least 2 algorithms to compare");
            return comparison;
        }
        
        // Find best performing algorithm for each metric
        String bestResponseTime = findBestAlgorithm(results, "averageResponseTime", false);
        String bestThroughput = findBestAlgorithm(results, "requestCount", true);
        String bestErrorRate = findBestAlgorithm(results, "errorRate", false);
        
        comparison.put("bestResponseTime", bestResponseTime);
        comparison.put("bestThroughput", bestThroughput);
        comparison.put("lowestErrorRate", bestErrorRate);
        
        // Calculate improvement percentages between all algorithms
        Map<String, Object> improvements = new HashMap<>();
        
        // RL-Agent vs Round-Robin
        if (results.containsKey("rl-agent") && results.containsKey("round-robin")) {
            double rlResponseTime = (Double) results.get("rl-agent").get("averageResponseTime");
            double rrResponseTime = (Double) results.get("round-robin").get("averageResponseTime");
            double improvement = ((rrResponseTime - rlResponseTime) / rrResponseTime) * 100;
            improvements.put("rlVsRoundRobin", String.format("%.1f%%", improvement));
        }
        
        // RL-Agent vs Least-Connections
        if (results.containsKey("rl-agent") && results.containsKey("least-connections")) {
            double rlResponseTime = (Double) results.get("rl-agent").get("averageResponseTime");
            double lcResponseTime = (Double) results.get("least-connections").get("averageResponseTime");
            double improvement = ((lcResponseTime - rlResponseTime) / lcResponseTime) * 100;
            improvements.put("rlVsLeastConnections", String.format("%.1f%%", improvement));
        }
        
        // Round-Robin vs Least-Connections
        if (results.containsKey("round-robin") && results.containsKey("least-connections")) {
            double rrResponseTime = (Double) results.get("round-robin").get("averageResponseTime");
            double lcResponseTime = (Double) results.get("least-connections").get("averageResponseTime");
            double improvement = ((lcResponseTime - rrResponseTime) / lcResponseTime) * 100;
            improvements.put("roundRobinVsLeastConnections", String.format("%.1f%%", improvement));
        }
        
        comparison.put("performanceImprovements", improvements);
        
        return comparison;
    }
    
    /**
     * Find the best performing algorithm for a specific metric
     */
    private String findBestAlgorithm(Map<String, Map<String, Object>> results, String metric, boolean higherIsBetter) {
        String bestAlgorithm = null;
        double bestValue = higherIsBetter ? Double.MIN_VALUE : Double.MAX_VALUE;
        
        for (Map.Entry<String, Map<String, Object>> entry : results.entrySet()) {
            Object valueObj = entry.getValue().get(metric);
            if (valueObj instanceof Number) {
                double value = ((Number) valueObj).doubleValue();
                if ((higherIsBetter && value > bestValue) || (!higherIsBetter && value < bestValue)) {
                    bestValue = value;
                    bestAlgorithm = entry.getKey();
                }
            }
        }
        
        return bestAlgorithm;
    }
    
    /**
     * Create error response
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }
    
    // Getters for current state (used by other components)
    public boolean isBenchmarkMode() { return benchmarkMode; }
    public String getCurrentAlgorithm() { return currentAlgorithm; }
    public String[] getTestAlgorithms() { return testAlgorithms.clone(); }
}
