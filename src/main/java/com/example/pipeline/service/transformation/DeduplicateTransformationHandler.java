package com.example.pipeline.service.transformation;

import com.example.pipeline.service.IngestionService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deduplicate Transformation Handler
 * Removes duplicate rows based on specified key fields or entire row.
 * 
 * Configuration example:
 * {
 *   "type": "deduplicate",
 *   "config": {
 *     "key_fields": ["id", "email"],
 *     "keep": "first"
 *   }
 * }
 * 
 * keep options: "first" (keep first occurrence), "last" (keep last), "all" (no dedup)
 */
@Component
public class DeduplicateTransformationHandler implements TransformationHandler {
    
    @Override
    public String getType() {
        return "deduplicate";
    }
    
    @Override
    public Map<String, Object> transformRow(Map<String, Object> row,
                                            Map<String, Object> config,
                                            IngestionService.IngestionMetrics metrics) {
        // Deduplicate operates on batches, not individual rows
        return row;
    }
    
    @Override
    public List<Map<String, Object>> transformBatch(List<Map<String, Object>> rows,
                                                    Map<String, Object> config,
                                                    IngestionService.IngestionMetrics metrics) {
        if (rows == null || rows.isEmpty() || config == null) {
            return rows;
        }
        
        List<String> keyFields = (List<String>) config.get("key_fields");
        String keep = config.getOrDefault("keep", "first").toString().toLowerCase();
        
        if (keyFields == null || keyFields.isEmpty()) {
            // No key fields - deduplicate entire row
            return deduplicateByEntireRow(rows, keep);
        } else {
            // Deduplicate by specified key fields
            return deduplicateByFields(rows, keyFields, keep);
        }
    }
    
    @Override
    public boolean requiresBatchProcessing() {
        return true;
    }
    
    /**
     * Deduplicates by specific key fields.
     */
    private List<Map<String, Object>> deduplicateByFields(List<Map<String, Object>> rows,
                                                          List<String> keyFields,
                                                          String keep) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        
        // For "last" option, process in reverse
        if ("last".equals(keep)) {
            rows = new ArrayList<>(rows);
            Collections.reverse(rows);
        }
        
        for (Map<String, Object> row : rows) {
            String key = buildCompositeKey(row, keyFields);
            
            if (!seen.contains(key)) {
                seen.add(key);
                result.add(row);
            }
        }
        
        // Reverse back if we reversed for "last"
        if ("last".equals(keep)) {
            Collections.reverse(result);
        }
        
        return result;
    }
    
    /**
     * Deduplicates entire row (all fields).
     */
    private List<Map<String, Object>> deduplicateByEntireRow(List<Map<String, Object>> rows,
                                                             String keep) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        
        // For "last" option, process in reverse
        if ("last".equals(keep)) {
            rows = new ArrayList<>(rows);
            Collections.reverse(rows);
        }
        
        for (Map<String, Object> row : rows) {
            String key = rowToString(row);
            
            if (!seen.contains(key)) {
                seen.add(key);
                result.add(row);
            }
        }
        
        // Reverse back if we reversed for "last"
        if ("last".equals(keep)) {
            Collections.reverse(result);
        }
        
        return result;
    }
    
    /**
     * Builds a composite key from specified fields.
     */
    private String buildCompositeKey(Map<String, Object> row, List<String> fields) {
        StringBuilder key = new StringBuilder();
        for (String field : fields) {
            Object value = getValueIgnoreCase(row, field);
            key.append(value != null ? value.toString() : "null").append("|");
        }
        return key.toString();
    }
    
    /**
     * Converts entire row to string for comparison.
     */
    private String rowToString(Map<String, Object> row) {
        return row.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + (e.getValue() != null ? e.getValue().toString() : "null"))
            .collect(Collectors.joining("|"));
    }
    
    /**
     * Case-insensitive field lookup.
     */
    private Object getValueIgnoreCase(Map<String, Object> data, String targetKey) {
        for (String key : data.keySet()) {
            if (key.equalsIgnoreCase(targetKey)) {
                return data.get(key);
            }
        }
        return null;
    }
}
