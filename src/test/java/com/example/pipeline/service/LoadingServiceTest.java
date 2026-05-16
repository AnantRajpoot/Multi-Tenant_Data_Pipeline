package com.example.pipeline.service;

import com.example.pipeline.model.DestinationConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class LoadingServiceTest {

    @Autowired
    private LoadingService loadingService;

    @Test
    void testJsonDestinationWritesOutputFile() throws Exception {
        File tempFile = File.createTempFile("loading-service-json", ".json");
        tempFile.deleteOnExit();

        DestinationConfig destination = new DestinationConfig();
        destination.setType("json");

        Map<String, Object> config = new HashMap<>();
        config.put("path", tempFile.getAbsolutePath());
        destination.setConfig(config);

        Map<String, Object> row = new HashMap<>();
        row.put("id", "1");
        row.put("name", "Alice");
        row.put("amount", "100");

        loadingService.load(row, destination);
        loadingService.flushBatch();

        assertTrue(tempFile.exists(), "Expected JSON output file to be created");
        assertTrue(tempFile.length() > 0, "Expected JSON output file to contain data");

        JsonNode json = new ObjectMapper().readTree(tempFile);
        assertTrue(json.isArray(), "Expected output to be a JSON array");
        assertEquals(1, json.size(), "Expected one output row");
        assertEquals("Alice", json.get(0).get("name").asText());
    }
}