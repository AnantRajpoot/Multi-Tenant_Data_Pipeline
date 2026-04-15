package com.example.pipeline.service;

import com.example.pipeline.model.JobRun;
import com.example.pipeline.model.Pipeline;
import com.example.pipeline.model.TransformationConfig;
import com.example.pipeline.repository.PipelineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ValidationService {
    @Autowired
    private PipelineRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final List<String> SUPPORTED_SOURCE_TYPES = Arrays.asList("csv", "json");
    private static final List<String> SUPPORTED_TRANSFORMATION_TYPES = Arrays.asList("filter", "map", "aggregate");
    private static final List<String> SUPPORTED_DESTINATION_TYPES = Arrays.asList("database", "file");

    /**
     * Validates required pipeline fields, supported types, and destination accessibility.
     */
    public boolean validatePipeline(Pipeline pipeline) {
        // Check required fields
        if (pipeline.getPipelineId() == null || pipeline.getPipelineId().trim().isEmpty()) {
            return false;
        }
        if (pipeline.getPipelineName() == null || pipeline.getPipelineName().trim().isEmpty()) {
            return false;
        }
        if (pipeline.getSource() == null) {
            return false;
        }
        if (pipeline.getDestination() == null) {
            return false;
        }

        // Validate source type
        String sourceType = pipeline.getSource().getType();
        if (sourceType == null || !SUPPORTED_SOURCE_TYPES.contains(sourceType.toLowerCase())) {
            return false;
        }

        // Validate source configuration
        Map<String, Object> sourceConfig = pipeline.getSource().getConfig();
        if (sourceConfig == null || !sourceConfig.containsKey("file_path")) {
            return false;
        }

        // Check if source file exists
        String filePath = (String) sourceConfig.get("file_path");
        if (filePath != null && !new File(filePath).exists()) {
            System.err.println("Warning: Source file does not exist: " + filePath);
        }

        // Validate transformations
        if (pipeline.getTransformations() != null) {
            for (TransformationConfig transformation : pipeline.getTransformations()) {
                String transType = transformation.getType();
                if (transType == null || !SUPPORTED_TRANSFORMATION_TYPES.contains(transType.toLowerCase())) {
                    return false;
                }

                // Validate transformation configuration
                if (transformation.getConfig() == null) {
                    return false;
                }
            }
        }

        // Validate destination type
        String destType = pipeline.getDestination().getType();
        if (destType == null || !SUPPORTED_DESTINATION_TYPES.contains(destType.toLowerCase())) {
            return false;
        }

        // Validate destination accessibility
        if ("database".equalsIgnoreCase(destType)) {
            return validateDatabaseDestination(pipeline.getDestination().getConfig());
        } else if ("file".equalsIgnoreCase(destType)) {
            return validateFileDestination(pipeline.getDestination().getConfig());
        }

        return true;
    }

    /**
     * Verifies database destination is reachable with current datasource settings.
     */
    private boolean validateDatabaseDestination(Map<String, Object> config) {
        if (config == null) {
            return true; // Will use default configuration
        }

        // Test database connectivity
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            System.err.println("Database connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies output directory exists or can be created for file destinations.
     */
    private boolean validateFileDestination(Map<String, Object> config) {
        if (config == null) {
            return true; // Will use default output directory
        }

        String filePath = (String) config.get("file_path");
        if (filePath != null) {
            File file = new File(filePath);
            File parentDir = file.getParentFile();

            // Check if parent directory exists or can be created
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    System.err.println("Cannot create output directory: " + parentDir.getAbsolutePath());
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Creates a JobRun seed object with initial execution metadata.
     */
    public JobRun createJobRun(Pipeline pipeline, String status, String message) {
        JobRun jobRun = new JobRun();
        jobRun.setPipelineId(pipeline.getPipelineId());
        jobRun.setStatus(status);
        jobRun.setStartTime(new Date());
        jobRun.setMessage(message);
        return jobRun;
    }
}
