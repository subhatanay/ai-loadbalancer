package com.bits.loadbalancer.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for loading and managing RL models trained in Python.
 * Provides thread-safe access to Q-table, action mappings, and state encoding logic.
 * Implements fallback mechanisms to ensure system stability.
 */
@Service
public class RLModelLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(RLModelLoader.class);
    
    @Value("${rl.models.enabled:false}")
    private boolean rlModelsEnabled;
    
    @Value("${rl.models.path:models/}")
    private String modelsPath;
    
    // Thread-safe model storage
    private volatile Map<String, Integer> actionMappings = new ConcurrentHashMap<>();
    private volatile Map<String, Map<String, Double>> qTable = new ConcurrentHashMap<>();
    private volatile boolean modelsLoaded = false;
    private volatile String loadError = null;
    
    private final ObjectMapper objectMapper;
    
    public RLModelLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void initializeModels() {
        if (!rlModelsEnabled) {
            logger.info("RL models disabled via configuration");
            return;
        }
        
        try {
            loadModels();
            logger.info("RL models loaded successfully. Q-table size: {}, Action mappings: {}", 
                       qTable.size(), actionMappings.size());
        } catch (Exception e) {
            logger.error("Failed to load RL models: {}", e.getMessage(), e);
            loadError = e.getMessage();
            // Don't throw exception - allow system to continue with fallback routing
        }
    }
    
    /**
     * Loads all RL models from the classpath resources
     */
    private void loadModels() throws IOException {
        logger.info("Loading RL models from path: {}", modelsPath);
        
        // Load action mappings (JSON format)
        loadActionMappings();
        
        // Load Q-table (simplified JSON format for Java compatibility)
        loadQTable();
        
        modelsLoaded = true;
        loadError = null;
    }
    
    /**
     * Loads action mappings from JSON file
     */
    private void loadActionMappings() throws IOException {
        try {
            ClassPathResource resource = new ClassPathResource(modelsPath + "action_mappings.json");
            if (!resource.exists()) {
                throw new FileNotFoundException("Action mappings file not found: " + modelsPath + "action_mappings.json");
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                TypeReference<Map<String, Integer>> typeRef = new TypeReference<Map<String, Integer>>() {};
                Map<String, Integer> loadedMappings = objectMapper.readValue(inputStream, typeRef);
                
                if (loadedMappings == null || loadedMappings.isEmpty()) {
                    throw new IllegalStateException("Action mappings file is empty or invalid");
                }
                
                actionMappings.clear();
                actionMappings.putAll(loadedMappings);
                
                logger.info("Loaded {} action mappings", actionMappings.size());
                logger.debug("Action mappings: {}", actionMappings);
            }
        } catch (Exception e) {
            logger.error("Failed to load action mappings", e);
            throw new IOException("Failed to load action mappings: " + e.getMessage(), e);
        }
    }
    
    /**
     * Loads Q-table from JSON file (real format from Python training)
     * Expected format: {"state_tuple": {"pod_name1": q_value1, "pod_name2": q_value2}, ...}
     */
    private void loadQTable() throws IOException {
        try {
            ClassPathResource resource = new ClassPathResource(modelsPath + "q_table.json");
            if (!resource.exists()) {
                logger.warn("Q-table JSON file not found, creating empty Q-table");
                qTable = new ConcurrentHashMap<>();
                return;
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                TypeReference<Map<String, Map<String, Double>>> typeRef = 
                    new TypeReference<Map<String, Map<String, Double>>>() {};
                Map<String, Map<String, Double>> loadedQTable = objectMapper.readValue(inputStream, typeRef);
                
                if (loadedQTable != null) {
                    qTable.clear();
                    qTable.putAll(loadedQTable);
                    logger.info("Loaded Q-table with {} states", qTable.size());
                    
                    // Log sample entries for debugging
                    if (!qTable.isEmpty()) {
                        String sampleState = qTable.keySet().iterator().next();
                        Map<String, Double> sampleActions = qTable.get(sampleState);
                        logger.debug("Sample Q-table entry - State: {}, Actions: {}", 
                                   sampleState, sampleActions.size());
                    }
                } else {
                    logger.warn("Q-table file is empty, using empty Q-table");
                    qTable = new ConcurrentHashMap<>();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load Q-table, using empty Q-table: {}", e.getMessage());
            qTable = new ConcurrentHashMap<>();
        }
    }
    
    /**
     * Gets the best pod name for a given state using the loaded Q-table
     * @param stateKey Encoded state key
     * @return Best pod name, or null if no action found
     */
    public String getBestPodName(String stateKey) {
        if (!isModelsReady()) {
            return null;
        }
        
        Map<String, Double> stateActions = qTable.get(stateKey);
        if (stateActions == null || stateActions.isEmpty()) {
            return null;
        }
        
        // Find pod name with highest Q-value
        return stateActions.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Gets the pod name for a given action index
     * @param actionIndex Action index from Q-table
     * @return Pod name, or null if not found
     */
    public String getActionToPodName(int actionIndex) {
        if (!isModelsReady()) {
            return null;
        }
        
        return actionMappings.entrySet().stream()
                .filter(entry -> entry.getValue().equals(actionIndex))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Gets all available pod names from action mappings
     * @return Set of pod names
     */
    public Set<String> getAvailablePods() {
        return new HashSet<>(actionMappings.keySet());
    }
    
    /**
     * Checks if models are loaded and ready for use
     * @return true if models are loaded successfully
     */
    public boolean isModelsReady() {
        return rlModelsEnabled && modelsLoaded && loadError == null;
    }
    
    /**
     * Gets the current load error if any
     * @return Error message or null if no error
     */
    public String getLoadError() {
        return loadError;
    }
    
    /**
     * Gets model statistics for monitoring
     * @return Map containing model statistics
     */
    public Map<String, Object> getModelStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", rlModelsEnabled);
        stats.put("loaded", modelsLoaded);
        stats.put("error", loadError);
        stats.put("actionMappingsSize", actionMappings.size());
        stats.put("qTableSize", qTable.size());
        stats.put("availablePods", getAvailablePods());
        return stats;
    }
    
    /**
     * Reloads models from disk (for runtime updates)
     */
    public void reloadModels() {
        logger.info("Reloading RL models...");
        try {
            loadModels();
            logger.info("RL models reloaded successfully");
        } catch (Exception e) {
            logger.error("Failed to reload RL models: {}", e.getMessage(), e);
            loadError = e.getMessage();
            modelsLoaded = false;
        }
    }
}
