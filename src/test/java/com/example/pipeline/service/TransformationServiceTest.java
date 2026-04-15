package com.example.pipeline.service;

import com.example.pipeline.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransformationServiceTest {

    @Autowired
    private TransformationService transformationService;

    private IngestionService.IngestionMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new IngestionService.IngestionMetrics();
        transformationService.resetAggregateBuffer();
    }

    @Test
    void testFilterGreaterThan() {
        // Test filter with > operator
        Map<String, Object> data = new HashMap<>();
        data.put("age", "30");
        data.put("name", "John");

        TransformationConfig filter = new TransformationConfig();
        filter.setType("filter");
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "age > 25");
        filter.setConfig(config);

        Map<String, Object> result = transformationService.applyTransformations(
            data, Collections.singletonList(filter), metrics);

        assertNotNull(result, "Row with age 30 should pass filter age > 25");
    }

    @Test
    void testFilterEqualsOperator() {
        // Test filter with == operator
        Map<String, Object> data = new HashMap<>();
        data.put("status", "active");
        data.put("id", "1");

        TransformationConfig filter = new TransformationConfig();
        filter.setType("filter");
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "status == active");
        filter.setConfig(config);

        Map<String, Object> result = transformationService.applyTransformations(
            data, Collections.singletonList(filter), metrics);

        assertNotNull(result, "Row with status 'active' should pass filter");
    }

    @Test
    void testFilterWithAndOperator() {
        // Test filter with AND operator
        Map<String, Object> data = new HashMap<>();
        data.put("age", "30");
        data.put("score", "75");

        TransformationConfig filter = new TransformationConfig();
        filter.setType("filter");
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "age > 25 AND score > 50");
        filter.setConfig(config);

        Map<String, Object> result = transformationService.applyTransformations(
            data, Collections.singletonList(filter), metrics);

        assertNotNull(result, "Row should pass both AND conditions");
    }

    @Test
    void testFilterWithOrOperator() {
        // Test filter with OR operator
        Map<String, Object> data = new HashMap<>();
        data.put("age", "20");
        data.put("score", "75");

        TransformationConfig filter = new TransformationConfig();
        filter.setType("filter");
        Map<String, Object> config = new HashMap<>();
        config.put("condition", "age > 25 OR score > 50");
        filter.setConfig(config);

        Map<String, Object> result = transformationService.applyTransformations(
            data, Collections.singletonList(filter), metrics);

        assertNotNull(result, "Row should pass OR condition (score > 50)");
    }

    @Test
    void testMapUpperFunction() {
        // Test map transformation with UPPER function
        Map<String, Object> data = new HashMap<>();
        data.put("name", "john doe");
        data.put("id", "1");

        TransformationConfig map = new TransformationConfig();
        map.setType("map");
        Map<String, Object> config = new HashMap<>();
        Map<String, String> operations = new HashMap<>();
        operations.put("name_upper", "UPPER(name)");
        config.put("operations", operations);
        map.setConfig(config);

        Map<String, Object> result = transformationService.applyTransformations(
            data, Collections.singletonList(map), metrics);

        assertNotNull(result);
        assertEquals("JOHN DOE", result.get("name_upper"), "Name should be converted to uppercase");
    }

    @Test
    void testMapLowerFunction() {
        // Test map transformation with LOWER function
        Map<String, Object> data = new HashMap<>();
        data.put("name", "JOHN DOE");

        TransformationConfig map = new TransformationConfig();
        map.setType("map");
        Map<String, Object> config = new HashMap<>();
        Map<String, String> operations = new HashMap<>();
        operations.put("name_lower", "LOWER(name)");
        config.put("operations", operations);
        map.setConfig(config);

        Map<String, Object> result = transformationService.applyTransformations(
            data, Collections.singletonList(map), metrics);

        assertNotNull(result);
        assertEquals("john doe", result.get("name_lower"), "Name should be converted to lowercase");
    }

    @Test
    void testMapMathAddition() {
        // Test map transformation with ADD function
        Map<String, Object> data = new HashMap<>();
        data.put("age", "30");
        data.put("bonus", "5");

        TransformationConfig map = new TransformationConfig();
        map.setType("map");
        Map<String, Object> config = new HashMap<>();
        Map<String, String> operations = new HashMap<>();
        operations.put("total_age", "ADD(age,bonus)");
        config.put("operations", operations);
        map.setConfig(config);

        Map<String, Object> result = transformationService.applyTransformations(
            data, Collections.singletonList(map), metrics);

        assertNotNull(result);
        assertEquals(35.0, result.get("total_age"), "Age + bonus should equal 35");
    }

    @Test
    void testCompleteTransformationChain() {
        // Test complete transformation chain: filter + map
        Map<String, Object> data = new HashMap<>();
        data.put("name", "alice");
        data.put("age", "28");
        data.put("score", "85");

        List<TransformationConfig> transformations = new ArrayList<>();

        // Add filter
        TransformationConfig filter = new TransformationConfig();
        filter.setType("filter");
        Map<String, Object> filterConfig = new HashMap<>();
        filterConfig.put("condition", "age > 25");
        filter.setConfig(filterConfig);
        transformations.add(filter);

        // Add map
        TransformationConfig map = new TransformationConfig();
        map.setType("map");
        Map<String, Object> mapConfig = new HashMap<>();
        Map<String, String> operations = new HashMap<>();
        operations.put("name_upper", "UPPER(name)");
        operations.put("age_doubled", "MULTIPLY(age,2)");
        mapConfig.put("operations", operations);
        map.setConfig(mapConfig);
        transformations.add(map);

        Map<String, Object> result = transformationService.applyTransformations(
            data, transformations, metrics);

        assertNotNull(result, "Row should pass filter");
        assertEquals("ALICE", result.get("name_upper"), "Name should be uppercase");
        assertEquals(56.0, result.get("age_doubled"), "Age should be doubled");
    }
}

