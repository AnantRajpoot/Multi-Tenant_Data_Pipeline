package com.example.pipeline.service.transformation;

import com.example.pipeline.service.IngestionService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Join Transformation Handler
 * Joins/merges rows within the dataset based on common key fields.
 * 
 * Configuration example:
 * {
 *   "type": "join",
 *   "config": {
 *     "key_fields": ["customer_id"],
 *     "aggregations": {
 *       "total_amount": "SUM(amount)",
 *       "order_count": "COUNT(*)"
 *     },
 *     "keep_fields": ["customer_id", "customer_name"]
 *   }
 * }
 */
@Component
public class JoinTransformationHandler implements TransformationHandler {
    
    @Override
    public String getType() {
        return "join";
    }
    
    @Override
    public Map<String, Object> transformRow(Map<String, Object> row,
                                            Map<String, Object> config,
                                            IngestionService.IngestionMetrics metrics) {
        // Join operates on batches, not individual rows
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
        Map<String, String> aggregations = (Map<String, String>) config.get("aggregations");
        List<String> keepFields = (List<String>) config.get("keep_fields");
        
        if (keyFields == null || keyFields.isEmpty()) {
            return rows;
        }
        
        // Group rows by key fields
        Map<String, List<Map<String, Object>>> groups = groupByFields(rows, keyFields);
        
        // Create joined result rows
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            Map<String, Object> joinedRow = new HashMap<>();
            List<Map<String, Object>> groupData = entry.getValue();
            
            // Add key fields from first row
            if (!groupData.isEmpty()) {
                Map<String, Object> firstRow = groupData.get(0);
                for (String keyField : keyFields) {
                    Object value = getValueIgnoreCase(firstRow, keyField);
                    joinedRow.put(keyField.toLowerCase(), value);
                }
                
                // Add keep fields if specified
                if (keepFields != null && !keepFields.isEmpty()) {
                    for (String field : keepFields) {
                        if (!keyFields.contains(field)) {
                            Object value = getValueIgnoreCase(firstRow, field);
                            joinedRow.put(field.toLowerCase(), value);
                        }
                    }
                }
            }
            
            // Perform aggregations if specified
            if (aggregations != null && !aggregations.isEmpty()) {
                for (Map.Entry<String, String> agg : aggregations.entrySet()) {
                    String resultKey = agg.getKey();
                    String aggFunction = agg.getValue().toUpperCase();
                    Object aggValue = performAggregation(groupData, aggFunction);
                    joinedRow.put(resultKey.toLowerCase(), aggValue);
                }
            }
            
            result.add(joinedRow);
        }
        
        return result;
    }
    
    @Override
    public boolean requiresBatchProcessing() {
        return true;
    }
    
    /**
     * Groups rows by specified key fields.
     */
    private Map<String, List<Map<String, Object>>> groupByFields(List<Map<String, Object>> rows,
                                                                 List<String> keyFields) {
        return rows.stream()
            .collect(Collectors.groupingBy(row -> buildCompositeKey(row, keyFields)));
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
     * Performs an aggregation function on a group of rows.
     */
    private Object performAggregation(List<Map<String, Object>> data, String aggFunction) {
        if (aggFunction.startsWith("COUNT(")) {
            return data.size();
        }
        
        String field = extractFunctionArg(aggFunction);
        
        if (aggFunction.startsWith("SUM(")) {
            return data.stream()
                .mapToDouble(row -> {
                    Object val = getValueIgnoreCase(row, field);
                    return val != null ? Double.parseDouble(val.toString()) : 0;
                })
                .sum();
        }
        
        if (aggFunction.startsWith("AVG(")) {
            return data.stream()
                .mapToDouble(row -> {
                    Object val = getValueIgnoreCase(row, field);
                    return val != null ? Double.parseDouble(val.toString()) : 0;
                })
                .average()
                .orElse(0.0);
        }
        
        if (aggFunction.startsWith("MIN(")) {
            return data.stream()
                .mapToDouble(row -> {
                    Object val = getValueIgnoreCase(row, field);
                    return val != null ? Double.parseDouble(val.toString()) : Double.MAX_VALUE;
                })
                .min()
                .orElse(0.0);
        }
        
        if (aggFunction.startsWith("MAX(")) {
            return data.stream()
                .mapToDouble(row -> {
                    Object val = getValueIgnoreCase(row, field);
                    return val != null ? Double.parseDouble(val.toString()) : Double.MIN_VALUE;
                })
                .max()
                .orElse(0.0);
        }
        
        return 0;
    }
    
    /**
     * Extracts function argument from function string like SUM(amount).
     */
    private String extractFunctionArg(String expression) {
        int start = expression.indexOf("(") + 1;
        int end = expression.lastIndexOf(")");
        return expression.substring(start, end).trim();
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
