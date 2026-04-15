package com.example.pipeline.service;

import com.example.pipeline.model.Pipeline;
import com.example.pipeline.model.SourceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IngestionServiceTest {

    @Autowired
    private IngestionService ingestionService;

    @Test
    void testCSVIngestionWithHeaders() throws Exception {
        // Create a temporary CSV file
        File tempFile = File.createTempFile("test", ".csv");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("id,name,age,tenant_id\n");
            writer.write("1,Alice,30,tenant1\n");
            writer.write("2,Bob,25,tenant1\n");
        }

        // Create pipeline
        Pipeline pipeline = new Pipeline();
        pipeline.setPipelineId("test_001");

        SourceConfig source = new SourceConfig();
        source.setType("csv");
        Map<String, Object> config = new HashMap<>();
        config.put("file_path", tempFile.getAbsolutePath());
        config.put("delimiter", ",");
        config.put("has_header", true);
        config.put("encoding", "UTF-8");
        source.setConfig(config);
        pipeline.setSource(source);

        // Test ingestion
        IngestionService.IngestionMetrics metrics = ingestionService.startIngestion(pipeline);

        assertNotNull(metrics);
        assertTrue(metrics.recordsRead >= 2, "Should read at least 2 records");
    }

    @Test
    void testSchemaInference() throws Exception {
        // Create CSV with different data types
        File tempFile = File.createTempFile("test_schema", ".csv");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("id,name,age,active,score\n");
            writer.write("1,Alice,30,true,95.5\n");
            writer.write("2,Bob,25,false,87.3\n");
            writer.write("3,Charlie,35,true,92.1\n");
        }

        Pipeline pipeline = new Pipeline();
        pipeline.setPipelineId("test_002");

        SourceConfig source = new SourceConfig();
        source.setType("csv");
        Map<String, Object> config = new HashMap<>();
        config.put("file_path", tempFile.getAbsolutePath());
        config.put("has_header", true);
        source.setConfig(config);
        pipeline.setSource(source);

        IngestionService.IngestionMetrics metrics = ingestionService.startIngestion(pipeline);

        assertNotNull(metrics);
        assertNotNull(source.getSchema(), "Schema should be inferred");

        // Verify schema types
        Map<String, String> schema = source.getSchema();
        assertEquals("integer", schema.get("id"), "ID should be inferred as integer");
        assertEquals("string", schema.get("name"), "Name should be inferred as string");
        assertEquals("integer", schema.get("age"), "Age should be inferred as integer");
    }

    @Test
    void testErrorHandlingForInvalidRows() throws Exception {
        // Create CSV with some invalid data
        File tempFile = File.createTempFile("test_errors", ".csv");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("id,name,age\n");
            writer.write("1,Alice,30\n");
            writer.write("2,Bob,invalid_age\n"); // Invalid age
            writer.write("3,Charlie,35\n");
        }

        Pipeline pipeline = new Pipeline();
        pipeline.setPipelineId("test_003");

        SourceConfig source = new SourceConfig();
        source.setType("csv");
        Map<String, Object> config = new HashMap<>();
        config.put("file_path", tempFile.getAbsolutePath());
        config.put("has_header", true);
        source.setConfig(config);
        pipeline.setSource(source);

        IngestionService.IngestionMetrics metrics = ingestionService.startIngestion(pipeline);

        assertNotNull(metrics);
        assertTrue(metrics.recordsRead >= 3, "Should attempt to read all records");
        // The service should continue processing despite errors
    }
}

