package com.example.pipeline.service.transformation;

import com.example.pipeline.service.IngestionService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sort Transformation Handler
 * Sorts rows by one or more fields in ascending or descending order.
 * 
 * Configuration example:
 * {
 *   "type": "sort",
 *   "config": {
 *     "fields": ["age DESC", "name ASC"],
 *     "limit": 100
 *   }
 * }
 */
@Component
public class SortTransformationHandler implements TransformationHandler {
    
    @Override
    public String getType() {
        return "sort";
    }
    
    @Override
    public Map<String, Object> transformRow(Map<String, Object> row,
                                            Map<String, Object> config,
                                            IngestionService.IngestionMetrics metrics) {
        // Sort operates on batches, not individual rows
        return row;
    }
    
    @Override
    public List<Map<String, Object>> transformBatch(List<Map<String, Object>> rows,
                                                    Map<String, Object> config,
                                                    IngestionService.IngestionMetrics metrics) {
        if (rows == null || rows.isEmpty() || config == null) {
            return rows;
        }
        
        List<String> sortFields = (List<String>) config.get("fields");
        Integer limit = (Integer) config.get("limit");
        
        if (sortFields == null || sortFields.isEmpty()) {
            return rows;
        }
        
        // Parse sort fields (e.g., "age DESC", "name ASC")
        List<SortField> parsedFields = sortFields.stream()
            .map(field -> {
                String[] parts = field.trim().split("\\s+");
                String fieldName = parts[0].toLowerCase();
                boolean descending = parts.length > 1 && parts[1].equalsIgnoreCase("DESC");
                return new SortField(fieldName, descending);
            })
            .collect(Collectors.toList());
        
        // Sort rows
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort((row1, row2) -> {
            for (SortField sortField : parsedFields) {
                Object val1 = getValueIgnoreCase(row1, sortField.fieldName);
                Object val2 = getValueIgnoreCase(row2, sortField.fieldName);
                
                int comparison = compareValues(val1, val2);
                if (comparison != 0) {
                    return sortField.descending ? -comparison : comparison;
                }
            }
            return 0;
        });
        
        // Apply limit if specified
        if (limit != null && limit > 0 && sorted.size() > limit) {
            sorted = sorted.subList(0, limit);
        }
        
        return sorted;
    }
    
    @Override
    public boolean requiresBatchProcessing() {
        return true;
    }
    
    /**
     * Compares two values for sorting (numeric or string comparison).
     */
    private int compareValues(Object val1, Object val2) {
        if (val1 == null && val2 == null) return 0;
        if (val1 == null) return -1;
        if (val2 == null) return 1;
        
        try {
            double num1 = Double.parseDouble(val1.toString());
            double num2 = Double.parseDouble(val2.toString());
            return Double.compare(num1, num2);
        } catch (NumberFormatException e) {
            return val1.toString().compareTo(val2.toString());
        }
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
    
    /**
     * Internal class to hold sort field metadata.
     */
    private static class SortField {
        String fieldName;
        boolean descending;
        
        SortField(String fieldName, boolean descending) {
            this.fieldName = fieldName;
            this.descending = descending;
        }
    }
}
