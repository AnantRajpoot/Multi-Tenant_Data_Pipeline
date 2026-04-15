package com.example.pipeline.controller;

import com.example.pipeline.model.Pipeline;
import com.example.pipeline.model.SourceConfig;
import com.example.pipeline.model.DestinationConfig;
import com.example.pipeline.repository.PipelineRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PipelineRepository pipelineRepository;

    @Test
    void testCreatePipeline() throws Exception {
        Pipeline pipeline = createTestPipeline("test_create_001");

        mockMvc.perform(post("/pipelines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pipeline)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineId").value("test_create_001"))
                .andExpect(jsonPath("$.pipelineName").exists());
    }

    @Test
    void testGetAllPipelines() throws Exception {
        mockMvc.perform(get("/pipelines"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetPipelineById() throws Exception {
        // Create a test pipeline first
        Pipeline pipeline = createTestPipeline("test_get_001");
        pipelineRepository.save(pipeline);

        mockMvc.perform(get("/pipelines/test_get_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineId").value("test_get_001"));
    }

    @Test
    void testUpdatePipeline() throws Exception {
        // Create initial pipeline
        Pipeline pipeline = createTestPipeline("test_update_001");
        pipelineRepository.save(pipeline);

        // Update pipeline
        pipeline.setPipelineName("Updated Pipeline Name");

        mockMvc.perform(put("/pipelines/test_update_001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pipeline)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineName").value("Updated Pipeline Name"));
    }

    @Test
    void testDeletePipeline() throws Exception {
        // Create a test pipeline first
        Pipeline pipeline = createTestPipeline("test_delete_001");
        pipelineRepository.save(pipeline);

        // Delete it
        mockMvc.perform(delete("/pipelines/test_delete_001"))
                .andExpect(status().isOk());

        // Verify it's deleted
        mockMvc.perform(get("/pipelines/test_delete_001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetNonExistentPipeline() throws Exception {
        mockMvc.perform(get("/pipelines/non_existent_id"))
                .andExpect(status().isNotFound());
    }

    private Pipeline createTestPipeline(String id) {
        Pipeline pipeline = new Pipeline();
        pipeline.setPipelineId(id);
        pipeline.setPipelineName("Test Pipeline");
        pipeline.setTenantId("test_tenant");

        // Set source
        SourceConfig source = new SourceConfig();
        source.setType("csv");
        Map<String, Object> sourceConfig = new HashMap<>();
        sourceConfig.put("file_path", "test.csv");
        sourceConfig.put("delimiter", ",");
        sourceConfig.put("has_header", true);
        source.setConfig(sourceConfig);
        pipeline.setSource(source);

        // Set destination
        DestinationConfig destination = new DestinationConfig();
        destination.setType("database");
        Map<String, Object> destConfig = new HashMap<>();
        destConfig.put("table", "test_table");
        destination.setConfig(destConfig);
        pipeline.setDestination(destination);

        return pipeline;
    }
}

