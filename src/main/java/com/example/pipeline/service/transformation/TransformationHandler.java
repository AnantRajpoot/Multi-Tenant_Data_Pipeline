package com.example.pipeline.service.transformation;

import com.example.pipeline.model.TransformationConfig;
import com.example.pipeline.service.IngestionService;

import java.util.List;
import java.util.Map;

/**
 * Interface for pluggable transformation handlers.
 * Each transformation type (sort, deduplicate, join, pivot, validation) implements this interface.
 */
public interface TransformationHandler {
    
    /**
     * Gets the type identifier for this transformation handler.
     * @return transformation type (e.g., "sort", "deduplicate", "join", "pivot", "validation")
     */
    String getType();
    
    /**
     * Handles transformations for a single row (for row-level operations).
     * Some transformations work on individual rows, others require collection.
     * 
     * @param row Input row to transform
     * @param config Transformation configuration
     * @param metrics Metrics to track
     * @return transformed row, or null if row should be filtered/rejected
     */
    Map<String, Object> transformRow(Map<String, Object> row, 
                                      Map<String, Object> config,
                                      IngestionService.IngestionMetrics metrics);
    
    /**
     * Handles transformations for a collection of rows (for collection-level operations).
     * Used by transformations that need to see all data (sort, deduplicate, join, pivot).
     * 
     * @param rows Input rows to transform
     * @param config Transformation configuration
     * @param metrics Metrics to track
     * @return transformed rows
     */
    List<Map<String, Object>> transformBatch(List<Map<String, Object>> rows,
                                             Map<String, Object> config,
                                             IngestionService.IngestionMetrics metrics);
    
    /**
     * Indicates whether this transformation operates on rows individually or requires batch processing.
     * @return true if batch processing is required, false if row-by-row processing
     */
    default boolean requiresBatchProcessing() {
        return false;
    }
}
