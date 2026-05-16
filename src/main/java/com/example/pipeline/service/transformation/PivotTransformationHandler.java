package com.example.pipeline.service.transformation;

import com.example.pipeline.service.IngestionService;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pivot Transformation Handler
 * Pivots data from row-oriented to column-oriented format.
 * 
 * Configuration example:
 * {
 *   "type": "pivot",
 *   "config": {
 *     "index_fields": ["date", "region"],
 *     "pivot_field": "product",
 *     "value_field": "amount",
 *     "aggregation": "SUM"
 *   }
 * }
 */
@Component
public class PivotTransformationHandler implements TransformationHandler {
    
    @Override
    public String getType() {
        return "pivot";
    }
    
    @Override
    public Map<String, Object> transformRow(Map<String, Object> row,
                                            Map<String, Object> config,
                                            IngestionService.IngestionMetrics metrics) {
        // Pivot operates on batches, not individual rows
        return row;
    }
    
    @Override
    public List<Map<String, Object>> transformBatch(List<Map<String, Object>> rows,
                                                    Map<String, Object> config,
                                                    IngestionService.IngestionMetrics metrics) {
        if (rows == null || rows.isEmpty() || config == null) {
            return rows;
        }
        
        List<String> indexFields = (List<String>) config.get("index_fields");
        String pivotField = (String) config.get("pivot_field");
        String valueField = (String) config.get("value_field");
        String aggregation = ((String) config.getOrDefault("aggregation", "SUM")).toUpperCase();
        
        if (indexFields == null || indexFields.isEmpty() || pivotField == null || valueField == null) {
            return rows;
        }
        
        // Group by index fields
        Map<String, Map<String, List<Map<String, Object>>>> pivotedData = new HashMap<>();
        
        for (Map<String, Object> row : rows) {
            String indexKey = buildCompositeKey(row, indexFields);
            String pivotValue = getValueIgnoreCase(row, pivotField).toString();
            
            pivotedData.computeIfAbsent(indexKey, k -> new HashMap<>())
                      .computeIfAbsent(pivotValue, k -> new ArrayList<>())
                      .add(row);
        }
        
        // Collect all pivot values (future columns)
        Set<String> pivotValues = new HashSet<>();
        for (Map<String, List<Map<String, Object>>> pivots : pivotedData.values()) {
            pivotValues.addAll(pivots.keySet());
        }
        List<String> sortedPivotValues = new ArrayList<>(pivotValues);
        Collections.sort(sortedPivotValues);
        
        // Build result rows
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, List<Map<String, Object>>>> entry : pivotedData.entrySet()) {
            Map<String, Object> resultRow = new HashMap<>();
            List<Map<String, Object>> firstRows = entry.getValue().values().iterator().next();
            
            // Add index fields
            if (!firstRows.isEmpty()) {
                Map<String, Object> firstRow = firstRows.get(0);
                for (String indexField : indexFields) {
                    Object value = getValueIgnoreCase(firstRow, indexField);
                    resultRow.put(indexField.toLowerCase(), value);
                }
            }
            
            // Add pivoted values
            for (String pivotValue : sortedPivotValues) {
                List<Map<String, Object>> valueRows = entry.getValue().get(pivotValue);
                Object aggValue = aggregateValues(valueRows, valueField, aggregation);
                String columnName = pivotValue.toLowerCase() + "_" + valueField.toLowerCase();
                resultRow.put(columnName, aggValue);
            }
            
            result.add(resultRow);
        }
        
        return result;
    }
    
    @Override
    public boolean requiresBatchProcessing() {
        return true;
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
     * Aggregates values from multiple rows.
     */
    private Object aggregateValues(List<Map<String, Object>> rows, String valueField, String aggregation) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        
        List<Double> values = rows.stream()
            .map(row -> {
                Object val = getValueIgnoreCase(row, valueField);
                try {
                    return val != null ? Double.parseDouble(val.toString()) : 0.0;
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            })
            .collect(Collectors.toList());
        
        switch (aggregation) {
            case "SUM":
                return values.stream().mapToDouble(Double::doubleValue).sum();
            case "AVG":
                return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            case "COUNT":
                return values.size();
            case "MIN":
                return values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            case "MAX":
                return values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            default:
                return 0;
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
}
