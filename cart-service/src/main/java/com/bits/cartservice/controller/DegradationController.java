package com.bits.cartservice.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Degradation Controller for simulating various types of performance degradation
 * Allows controlled testing of RL agent adaptation to pod performance issues
 */
@RestController
@RequestMapping("/api/degradation")
@EnableAsync
public class DegradationController {

    private final AtomicBoolean cpuStressActive = new AtomicBoolean(false);
    private final AtomicBoolean memoryStressActive = new AtomicBoolean(false);
    private final AtomicBoolean latencyStressActive = new AtomicBoolean(false);
    private final AtomicBoolean errorInjectionActive = new AtomicBoolean(false);
    private final AtomicInteger currentStressLevel = new AtomicInteger(0);
    private final AtomicInteger errorInjectionRate = new AtomicInteger(0);

    private  Integer intensityPercentCpuDefault = 50;
    private  Integer memoryPercentDefault = 70;
    private  Integer delayMsLatency = 300;
    private  Integer errorInjectionRateDefault = 5;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final List<CompletableFuture<Void>> activeTasks = new ArrayList<>();
    private final Random random = new Random();

    /**
     * Start CPU stress simulation
     */
    @PostMapping("/cpu/start")
    public ResponseEntity<Map<String, Object>> startCpuStress(
            @RequestParam(name="intensityPercent",defaultValue = "50") int intensityPercent,
            @RequestParam(name="durationSeconds",defaultValue = "300") int durationSeconds) {
        
        if (cpuStressActive.get()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "CPU stress already active",
                "status", "failed"
            ));
        }

        cpuStressActive.set(true);
        currentStressLevel.set(intensityPercent);
        intensityPercentCpuDefault = intensityPercent;

        // Start CPU stress threads based on intensity
        int threadCount = Math.max(1, (intensityPercent * Runtime.getRuntime().availableProcessors()) / 100);
        
        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
                while (System.currentTimeMillis() < endTime && cpuStressActive.get()) {
                    // CPU-intensive calculation
                    double result = 0;
                    for (int j = 0; j < 100000; j++) {
                        result += Math.sqrt(j) * Math.sin(j) * Math.cos(j);
                    }
                    
                    // Brief pause to control intensity
                    try {
                        Thread.sleep(intensityPercent > 80 ? 1 : 5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            activeTasks.add(task);
        }

        // Auto-stop after duration
        scheduler.schedule(() -> stopCpuStress(), durationSeconds, TimeUnit.SECONDS);

        return ResponseEntity.ok(Map.of(
            "message", "CPU stress started",
            "intensity", intensityPercent + "%",
            "duration", durationSeconds + "s",
            "threads", threadCount,
            "status", "active"
        ));
    }

    /**
     * Stop CPU stress simulation
     */
    @PostMapping("/cpu/stop")
    public ResponseEntity<Map<String, Object>> stopCpuStress() {
        cpuStressActive.set(false);
        currentStressLevel.set(0);
        
        // Cancel active tasks
        activeTasks.forEach(task -> task.cancel(true));
        activeTasks.clear();

        return ResponseEntity.ok(Map.of(
            "message", "CPU stress stopped",
            "status", "inactive"
        ));
    }

    /**
     * Start memory stress simulation (triggers GC pauses)
     */
    @PostMapping("/memory/start")
    public ResponseEntity<Map<String, Object>> startMemoryStress(
            @RequestParam(name= "memoryPercent", defaultValue = "70") int memoryPercent,
            @RequestParam(name= "durationSeconds", defaultValue = "300") int durationSeconds) {
        
        if (memoryStressActive.get()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Memory stress already active",
                "status", "failed"
            ));
        }

        memoryStressActive.set(true);
        memoryPercentDefault = memoryPercent;
        
        CompletableFuture<Void> memoryTask = CompletableFuture.runAsync(() -> {
            List<byte[]> memoryHog = new ArrayList<>();
            long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
            
            try {
                MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
                long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
                long targetMemory = (maxMemory * memoryPercent) / 100;
                
                while (System.currentTimeMillis() < endTime && memoryStressActive.get()) {
                    long currentUsed = memoryBean.getHeapMemoryUsage().getUsed();
                    
                    if (currentUsed < targetMemory) {
                        // Allocate memory in chunks to trigger GC
                        byte[] chunk = new byte[1024 * 1024]; // 1MB chunks
                        memoryHog.add(chunk);
                        
                        // Fill with random data to prevent optimization
                        random.nextBytes(chunk);
                    }
                    
                    // Periodically release some memory to create GC pressure
                    if (memoryHog.size() > 100 && random.nextInt(10) == 0) {
                        memoryHog.subList(0, 10).clear();
                    }
                    
                    Thread.sleep(50); // Control allocation rate
                }
            } catch (Exception e) {
                // Expected - out of memory or interruption
            } finally {
                memoryHog.clear(); // Release memory
                System.gc(); // Suggest garbage collection
            }
        });
        
        activeTasks.add(memoryTask);
        
        // Auto-stop after duration
        scheduler.schedule(() -> stopMemoryStress(), durationSeconds, TimeUnit.SECONDS);

        return ResponseEntity.ok(Map.of(
            "message", "Memory stress started",
            "targetMemory", memoryPercent + "%",
            "duration", durationSeconds + "s",
            "status", "active"
        ));
    }

    /**
     * Stop memory stress simulation
     */
    @PostMapping("/memory/stop")
    public ResponseEntity<Map<String, Object>> stopMemoryStress() {
        memoryStressActive.set(false);
        
        // Cancel memory tasks and trigger GC
        activeTasks.forEach(task -> task.cancel(true));
        activeTasks.clear();
        System.gc();

        return ResponseEntity.ok(Map.of(
            "message", "Memory stress stopped",
            "status", "inactive"
        ));
    }

    /**
     * Start latency simulation (artificial delays)
     */
    @PostMapping("/latency/start")
    public ResponseEntity<Map<String, Object>> startLatencyStress(
            @RequestParam(name = "delayMs", defaultValue = "100") int delayMs,
            @RequestParam(name = "durationSeconds",defaultValue = "300") int durationSeconds) {
        
        if (latencyStressActive.get()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Latency stress already active",
                "status", "failed"
            ));
        }

        latencyStressActive.set(true);
        delayMsLatency = delayMs;
        
        // Auto-stop after duration
        scheduler.schedule(() -> stopLatencyStress(), durationSeconds, TimeUnit.SECONDS);

        return ResponseEntity.ok(Map.of(
            "message", "Latency stress started",
            "delay", delayMs + "ms",
            "duration", durationSeconds + "s",
            "status", "active"
        ));
    }

    /**
     * Stop latency simulation
     */
    @PostMapping("/latency/stop")
    public ResponseEntity<Map<String, Object>> stopLatencyStress() {
        latencyStressActive.set(false);

        return ResponseEntity.ok(Map.of(
            "message", "Latency stress stopped",
            "status", "inactive"
        ));
    }

    /**
     * Start gradual degradation simulation
     */
    @PostMapping("/gradual/start")
    public ResponseEntity<Map<String, Object>> startGradualDegradation(
            @RequestParam(defaultValue = "600") int totalDurationSeconds,
            @RequestParam(defaultValue = "4") int phases) {
        
        if (cpuStressActive.get() || memoryStressActive.get() || latencyStressActive.get()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Other stress tests already active",
                "status", "failed"
            ));
        }

        int phaseDuration = totalDurationSeconds / phases;
        
        CompletableFuture<Void> gradualTask = CompletableFuture.runAsync(() -> {
            for (int phase = 1; phase <= phases; phase++) {
                if (Thread.currentThread().isInterrupted()) break;
                
                int cpuIntensity = (phase * 25); // 25%, 50%, 75%, 100%
                int memoryPercent = Math.min(80, phase * 20); // 20%, 40%, 60%, 80%
                
                // Start CPU stress for this phase
                cpuStressActive.set(true);
                currentStressLevel.set(cpuIntensity);
                
                // Start memory stress for this phase
                memoryStressActive.set(true);
                
                try {
                    Thread.sleep(phaseDuration * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // Clean up
            cpuStressActive.set(false);
            memoryStressActive.set(false);
            latencyStressActive.set(false);

        });
        
        activeTasks.add(gradualTask);

        return ResponseEntity.ok(Map.of(
            "message", "Gradual degradation started",
            "totalDuration", totalDurationSeconds + "s",
            "phases", phases,
            "phaseDuration", phaseDuration + "s",
            "status", "active"
        ));
    }

    /**
     * Get current degradation status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("cpuStressActive", cpuStressActive.get());
        status.put("memoryStressActive", memoryStressActive.get());
        status.put("latencyStressActive", latencyStressActive.get());
        status.put("currentStressLevel", currentStressLevel.get());
        status.put("activeTasks", activeTasks.size());
        
        // Add JVM metrics
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        status.put("heapUsed", memoryBean.getHeapMemoryUsage().getUsed());
        status.put("heapMax", memoryBean.getHeapMemoryUsage().getMax());
        status.put("heapUsagePercent", 
            (memoryBean.getHeapMemoryUsage().getUsed() * 100) / memoryBean.getHeapMemoryUsage().getMax());
        
        return ResponseEntity.ok(status);
    }

    /**
     * Stop all degradation simulations
     */
    @PostMapping("/stop-all")
    public ResponseEntity<Map<String, Object>> stopAllDegradation() {
        cpuStressActive.set(false);
        memoryStressActive.set(false);
        latencyStressActive.set(false);
        currentStressLevel.set(0);
        
        activeTasks.forEach(task -> task.cancel(true));
        activeTasks.clear();
        System.gc();

        return ResponseEntity.ok(Map.of(
            "message", "All degradation stopped",
            "status", "inactive"
        ));
    }

    /**
     * Start error injection (makes endpoints randomly return 500 errors)
     */
    @PostMapping("/errors/start")
    public ResponseEntity<Map<String, Object>> startErrorInjection(
            @RequestParam(defaultValue = "30") int errorRatePercent,
            @RequestParam(defaultValue = "300") int durationSeconds) {
        
        errorInjectionActive.set(true);
        errorInjectionRate.set(errorRatePercent);
        errorInjectionRateDefault = errorRatePercent;
        
        // Auto-stop after duration
        scheduler.schedule(() -> stopErrorInjection(), durationSeconds, TimeUnit.SECONDS);

        return ResponseEntity.ok(Map.of(
            "message", "Error injection started",
            "errorRate", errorRatePercent + "%",
            "duration", durationSeconds + "s",
            "status", "active"
        ));
    }

    /**
     * Stop error injection
     */
    @PostMapping("/errors/stop")
    public ResponseEntity<Map<String, Object>> stopErrorInjection() {
        errorInjectionActive.set(false);
        errorInjectionRate.set(0);

        return ResponseEntity.ok(Map.of(
            "message", "Error injection stopped",
            "status", "inactive"
        ));
    }

    /**
     * Inject artificial latency into responses (call this from other endpoints)
     */
    public void injectLatencyIfActive() {
        if (latencyStressActive.get()) {
            try {
                // Random delay between 50ms to 200ms
                int delay = 50 + random.nextInt(Math.min(8,delayMsLatency-500), delayMsLatency);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Inject errors into responses (call this from other endpoints)
     * @return true if error should be thrown, false otherwise
     */
    public boolean shouldInjectError() {
        if (errorInjectionActive.get()) {
            int threshold = errorInjectionRate.get();
            return random.nextInt(100) < threshold;
        }
        return false;
    }
}
