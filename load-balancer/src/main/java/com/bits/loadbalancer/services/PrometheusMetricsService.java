package com.bits.loadbalancer.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bits.loadbalancer.clients.PrometheusClient;
import com.bits.loadbalancer.model.InstanceMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PrometheusMetricsService {

    @Autowired
    private PrometheusClient prometheusClient;

    @Value("${prometheus.job-name}")
    private String jobName;

    @Value("${prometheus.namespace}")
    private String namespace;

    @Value("${prometheus.metrics-time-range}")
    private String metricsTimeRange;

    private String getServiceNameFromPod(String podName) {
        // Extract service name from pod name (e.g., cart-service-6cc9bf5787-gl5jt -> cart-service)
        return podName.replaceAll("-[a-z0-9]+-[a-z0-9]+$", "");
    }

    private String buildPodSpecificQuery(String metricName, String podName, String additionalLabels) {
        // Query for specific pod using the new pod discovery labels
        return String.format(
            "%s{pod_name=\"%s\"%s}",
            metricName, podName, additionalLabels
        );
    }

    private String buildFlexibleQuery(String metricName, String serviceName, String additionalLabels) {
        // Fallback: Try multiple label combinations to handle inconsistent labeling
        return String.format(
            "(%s{application=\"%s\"%s} or %s{job=\"%s\"%s} or %s{service=\"%s\"%s})",
            metricName, serviceName, additionalLabels,
            metricName, serviceName, additionalLabels,
            metricName, serviceName, additionalLabels
        );
    }

    public Map<String, InstanceMetrics> fetchMetricsForPods(List<String> podNames) {
        Map<String, InstanceMetrics> metricsMap = new HashMap<>();

        for (String podName : podNames) {
            String serviceName = getServiceNameFromPod(podName);

            // CPU usage as a percentage (0-1) - try pod-specific first, then fallback
            String cpuQuery = String.format(
                    "(%s or %s)",
                    buildPodSpecificQuery("process_cpu_usage", podName, ""),
                    buildFlexibleQuery("process_cpu_usage", serviceName, "")
            );

            // JVM Heap memory usage - try pod-specific first, then fallback
            String jvmMemoryUsedQuery = String.format(
                    "(%s or %s or %s)",
                    buildPodSpecificQuery("jvm_memory_used_bytes", podName, ", area=\"heap\""),
                    buildPodSpecificQuery("jvm_memory_used_bytes", podName, ""),
                    buildFlexibleQuery("jvm_memory_used_bytes", serviceName, ", area=\"heap\"")
            );

            String jvmMemoryMaxQuery = String.format(
                    "(%s or %s or %s)",
                    buildPodSpecificQuery("jvm_memory_max_bytes", podName, ", area=\"heap\""),
                    buildPodSpecificQuery("jvm_memory_max_bytes", podName, ""),
                    buildFlexibleQuery("jvm_memory_max_bytes", serviceName, ", area=\"heap\"")
            );

            // Uptime in seconds - try pod-specific first, then fallback
            String uptimeQuery = String.format(
                    "(%s or %s)",
                    buildPodSpecificQuery("process_uptime_seconds", podName, ""),
                    buildFlexibleQuery("process_uptime_seconds", serviceName, "")
            );

            // Request rate (requests per second) - use rate over 5m window for specific pod
            String requestCountQuery = String.format(
                    "(rate(http_server_requests_seconds_count{pod_name=\"%s\"}[5m]) or sum(rate(http_server_requests_seconds_count{application=\"%s\"}[5m])) or sum(rate(http_server_requests_seconds_count{job=\"%s\"}[5m])) or on() vector(0))",
                    podName, serviceName, serviceName
            );

            // Total request count for calculating averages - specific pod
            String totalRequestsQuery = String.format(
                    "(http_server_requests_seconds_count{pod_name=\"%s\"} or sum(http_server_requests_seconds_count{application=\"%s\"}) or sum(http_server_requests_seconds_count{job=\"%s\"}) or on() vector(0))",
                    podName, serviceName, serviceName
            );

            // Total request sum for calculating averages - specific pod
            String totalRequestSumQuery = String.format(
                    "(http_server_requests_seconds_sum{pod_name=\"%s\"} or sum(http_server_requests_seconds_sum{application=\"%s\"}) or sum(http_server_requests_seconds_sum{job=\"%s\"}) or on() vector(0))",
                    podName, serviceName, serviceName
            );

            // Error count (4xx and 5xx status codes) - specific pod
            String errorCountQuery = String.format(
                    "(http_server_requests_seconds_count{pod_name=\"%s\", status=~\"4..|5..\"} or sum(http_server_requests_seconds_count{application=\"%s\", status=~\"4..|5..\"}) or sum(http_server_requests_seconds_count{job=\"%s\", status=~\"4..|5..\"}) or on() vector(0))",
                    podName, serviceName, serviceName
            );

            Optional<Double> cpuUsageOpt = prometheusClient.queryInstantValue(cpuQuery);
            Optional<Double> jvmMemoryUsedOpt = prometheusClient.queryInstantValue(jvmMemoryUsedQuery);
            Optional<Double> jvmMemoryMaxOpt = prometheusClient.queryInstantValue(jvmMemoryMaxQuery);
            Optional<Double> uptimeOpt = prometheusClient.queryInstantValue(uptimeQuery);
            Optional<Double> requestCountOpt = prometheusClient.queryInstantValue(requestCountQuery);
            Optional<Double> totalRequestsOpt = prometheusClient.queryInstantValue(totalRequestsQuery);
            Optional<Double> totalRequestSumOpt = prometheusClient.queryInstantValue(totalRequestSumQuery);
            Optional<Double> errorCountOpt = prometheusClient.queryInstantValue(errorCountQuery);

            // Calculate metrics with proper fallbacks
            double cpuUsage = cpuUsageOpt.orElse(0.0);
            double jvmMemoryUsed = jvmMemoryUsedOpt.orElse(0.0);
            double jvmMemoryMax = jvmMemoryMaxOpt.orElse(1.0); // Avoid division by zero
            double uptime = uptimeOpt.orElse(0.0);
            double requestCount = requestCountOpt.orElse(0.0);
            double totalRequests = totalRequestsOpt.orElse(0.0);
            double totalRequestSum = totalRequestSumOpt.orElse(0.0);
            double errorCount = errorCountOpt.orElse(0.0);

            // Calculate derived metrics
            double memoryUsagePercent = (jvmMemoryUsed / jvmMemoryMax) * 100;
            double avgResponseTimeMs = totalRequests > 0 ? (totalRequestSum / totalRequests) * 1000 : 0.0; // Convert to milliseconds
            double errorRatePercent = totalRequests > 0 ? (errorCount / totalRequests) * 100 : 0.0;

            InstanceMetrics metrics = new InstanceMetrics(
                    cpuUsage * 100.0, // Convert to percentage
                    memoryUsagePercent,
                    uptime,
                    avgResponseTimeMs,
                    errorRatePercent,
                    requestCount // Use the rate per second directly
            );

            metricsMap.put(podName, metrics);
        }

        return metricsMap;
    }
}
