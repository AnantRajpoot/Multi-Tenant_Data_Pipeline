package com.example.pipeline.service;

import com.example.pipeline.model.JobRun;
import com.example.pipeline.model.Pipeline;
import com.example.pipeline.model.TransformationConfig;
import com.example.pipeline.repository.PipelineRepository;
import com.example.pipeline.util.EnvVarResolver;
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

    private static final List<String> SUPPORTED_SOURCE_TYPES = Arrays.asList("csv", "json", "database", "api", "s3");
    private static final List<String> SUPPORTED_TRANSFORMATION_TYPES = Arrays.asList("filter", "map", "aggregate");
    private static final List<String> SUPPORTED_DESTINATION_TYPES = Arrays.asList("database", "file", "json");

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
        if (sourceConfig == null) {
            return false;
        }
        
        // Resolve environment variables in config for validation
        Map<String, Object> resolvedConfig = EnvVarResolver.resolveMap(sourceConfig);

        if ("csv".equalsIgnoreCase(sourceType) || "json".equalsIgnoreCase(sourceType)) {
            if (!resolvedConfig.containsKey("file_path")) {
                return false;
            }
            // Check if source file exists
            String filePath = (String) resolvedConfig.get("file_path");
            if (filePath != null && !new File(filePath).exists()) {
                return false;
            }
        } else if ("database".equalsIgnoreCase(sourceType)) {
            // For database, check for required query field
            if (!resolvedConfig.containsKey("query")) {
                return false;
            }
            
            // Database handler requires a 'connection' object
            if (resolvedConfig.containsKey("connection")) {
                Map<String, Object> conn = (Map<String, Object>) resolvedConfig.get("connection");
                if (conn == null) return false;
                
                // Connection requires either jdbc_url OR (driver, host, database)
                if (!conn.containsKey("jdbc_url") || conn.get("jdbc_url").toString().isEmpty()) {
                    if (!conn.containsKey("driver") || !conn.containsKey("host") || !conn.containsKey("database")) {
                        return false;
                    }
                }
            } else {
                // Connection object is missing
                return false;
            }
        } else if ("s3".equalsIgnoreCase(sourceType)) {
            // S3 ingestion requires bucket and object key.
            if (!resolvedConfig.containsKey("bucket") || !resolvedConfig.containsKey("key")) {
                return false;
            }
            Object bucket = resolvedConfig.get("bucket");
            Object key = resolvedConfig.get("key");
            if (bucket == null || bucket.toString().trim().isEmpty()) {
                return false;
            }
            if (key == null || key.toString().trim().isEmpty()) {
                return false;
            }
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
        } else if ("file".equalsIgnoreCase(destType) || "json".equalsIgnoreCase(destType)) {
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

        // Resolve environment variables in destination config
        Map<String, Object> resolvedConfig = EnvVarResolver.resolveMap(config);
        String filePath = null;
        if (resolvedConfig.containsKey("file_path") && resolvedConfig.get("file_path") != null) {
            filePath = resolvedConfig.get("file_path").toString();
        } else if (resolvedConfig.containsKey("path") && resolvedConfig.get("path") != null) {
            // Keep backward compatibility with sample payloads that use "path".
            filePath = resolvedConfig.get("path").toString();
        }
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