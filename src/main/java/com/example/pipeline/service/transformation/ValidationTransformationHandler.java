package com.example.pipeline.service.transformation;

import com.example.pipeline.service.IngestionService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validation Transformation Handler
 * Validates fields against rules (regex, min/max values, required fields, etc.).
 * Rows that fail validation are filtered out.
 * 
 * Configuration example:
 * {
 *   "type": "validation",
 *   "config": {
 *     "rules": {
 *       "email": {"pattern": "^[^@]+@[^@]+\\.[^@]+$"},
 *       "age": {"min": 0, "max": 150},
 *       "name": {"required": true, "min_length": 1}
 *     }
 *   }
 * }
 */
@Component
public class ValidationTransformationHandler implements TransformationHandler {
    
    @Override
    public String getType() {
        return "validation";
    }
    
    @Override
    public Map<String, Object> transformRow(Map<String, Object> row,
                                            Map<String, Object> config,
                                            IngestionService.IngestionMetrics metrics) {
        if (config == null) {
            return row;
        }
        
        Map<String, Map<String, Object>> rules = (Map<String, Map<String, Object>>) config.get("rules");
        
        if (rules == null || rules.isEmpty()) {
            return row;
        }
        
        // Validate each field against its rules
        for (Map.Entry<String, Map<String, Object>> entry : rules.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> fieldRules = entry.getValue();
            Object fieldValue = getValueIgnoreCase(row, fieldName);
            
            if (!validateField(fieldValue, fieldRules)) {
                // Row fails validation - return null to filter it out
                return null;
            }
        }
        
        return row;
    }
    
    @Override
    public List<Map<String, Object>> transformBatch(List<Map<String, Object>> rows,
                                                    Map<String, Object> config,
                                                    IngestionService.IngestionMetrics metrics) {
        // Validation can work on individual rows (row-by-row filtering)
        return rows;
    }
    
    @Override
    public boolean requiresBatchProcessing() {
        return false;
    }
    
    /**
     * Validates a single field against its rules.
     */
    private boolean validateField(Object fieldValue, Map<String, Object> rules) {
        // Check if field is required
        Boolean required = (Boolean) rules.get("required");
        if (required != null && required) {
            if (fieldValue == null || fieldValue.toString().isEmpty()) {
                return false;
            }
        }
        
        if (fieldValue == null) {
            return true; // Null is acceptable if not required
        }
        
        String stringValue = fieldValue.toString();
        
        // Check pattern (regex)
        String pattern = (String) rules.get("pattern");
        if (pattern != null) {
            try {
                if (!Pattern.matches(pattern, stringValue)) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        
        // Check string length
        Integer minLength = (Integer) rules.get("min_length");
        if (minLength != null && stringValue.length() < minLength) {
            return false;
        }
        
        Integer maxLength = (Integer) rules.get("max_length");
        if (maxLength != null && stringValue.length() > maxLength) {
            return false;
        }
        
        // Check numeric ranges
        try {
            Double numValue = Double.parseDouble(stringValue);
            
            Number minValue = (Number) rules.get("min");
            if (minValue != null && numValue < minValue.doubleValue()) {
                return false;
            }
            
            Number maxValue = (Number) rules.get("max");
            if (maxValue != null && numValue > maxValue.doubleValue()) {
                return false;
            }
        } catch (NumberFormatException e) {
            // Not a number - skip numeric validation
        }
        
        // Check allowed values (enum)
        List<String> allowedValues = (List<String>) rules.get("allowed_values");
        if (allowedValues != null && !allowedValues.isEmpty()) {
            if (!allowedValues.contains(stringValue)) {
                return false;
            }
        }
        
        // Check value not in list (blacklist)
        List<String> notAllowedValues = (List<String>) rules.get("not_allowed_values");
        if (notAllowedValues != null && !notAllowedValues.isEmpty()) {
            if (notAllowedValues.contains(stringValue)) {
                return false;
            }
        }
        
        return true;
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
