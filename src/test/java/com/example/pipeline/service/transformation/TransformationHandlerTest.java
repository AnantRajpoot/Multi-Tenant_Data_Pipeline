package com.example.pipeline.service.transformation;

import com.example.pipeline.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransformationHandlerTest {
    
    private IngestionService.IngestionMetrics metrics;
    private SortTransformationHandler sortHandler;
    private DeduplicateTransformationHandler deduplicateHandler;
    private JoinTransformationHandler joinHandler;
    private PivotTransformationHandler pivotHandler;
    private ValidationTransformationHandler validationHandler;
    
    @BeforeEach
    void setUp() {
        metrics = new IngestionService.IngestionMetrics();
        sortHandler = new SortTransformationHandler();
        deduplicateHandler = new DeduplicateTransformationHandler();
        joinHandler = new JoinTransformationHandler();
        pivotHandler = new PivotTransformationHandler();
        validationHandler = new ValidationTransformationHandler();
    }
    
    // ==================== SORT TRANSFORMATION TESTS ====================
    
    @Test
    void testSortHandlerType() {
        assertEquals("sort", sortHandler.getType());
        assertTrue(sortHandler.requiresBatchProcessing());
    }
    
    @Test
    void testSortAscending() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(createRow("Alice", 30));
        rows.add(createRow("Bob", 25));
        rows.add(createRow("Charlie", 35));
        
        Map<String, Object> config = new HashMap<>();
        config.put("fields", Collections.singletonList("age ASC"));
        
        List<Map<String, Object>> result = sortHandler.transformBatch(rows, config, metrics);
        
        assertEquals(3, result.size());
        assertEquals(25.0, (Double) result.get(0).get("age"), 0.01);
        assertEquals(30.0, (Double) result.get(1).get("age"), 0.01);
        assertEquals(35.0, (Double) result.get(2).get("age"), 0.01);
    }
    
    @Test
    void testSortDescending() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(createRow("Alice", 30));
        rows.add(createRow("Bob", 25));
        rows.add(createRow("Charlie", 35));
        
        Map<String, Object> config = new HashMap<>();
        config.put("fields", Collections.singletonList("age DESC"));
        
        List<Map<String, Object>> result = sortHandler.transformBatch(rows, config, metrics);
        
        assertEquals(3, result.size());
        assertEquals(35.0, (Double) result.get(0).get("age"), 0.01);
        assertEquals(30.0, (Double) result.get(1).get("age"), 0.01);
        assertEquals(25.0, (Double) result.get(2).get("age"), 0.01);
    }
    
    @Test
    void testSortMultipleFields() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(createRowWithCity("Alice", 30, "NYC"));
        rows.add(createRowWithCity("Bob", 30, "LA"));
        rows.add(createRowWithCity("Charlie", 25, "NYC"));
        
        Map<String, Object> config = new HashMap<>();
        config.put("fields", Arrays.asList("age DESC", "name ASC"));
        
        List<Map<String, Object>> result = sortHandler.transformBatch(rows, config, metrics);
        
        assertEquals(3, result.size());
        assertEquals("Alice", result.get(0).get("name"));
        assertEquals("Bob", result.get(1).get("name"));
        assertEquals("Charlie", result.get(2).get("name"));
    }
    
    @Test
    void testSortWithLimit() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(createRow("Alice", 30));
        rows.add(createRow("Bob", 25));
        rows.add(createRow("Charlie", 35));
        
        Map<String, Object> config = new HashMap<>();
        config.put("fields", Collections.singletonList("age ASC"));
        config.put("limit", 2);
        
        List<Map<String, Object>> result = sortHandler.transformBatch(rows, config, metrics);
        
        assertEquals(2, result.size());
        assertEquals(25.0, (Double) result.get(0).get("age"), 0.01);
        assertEquals(30.0, (Double) result.get(1).get("age"), 0.01);
    }
    
    // ==================== DEDUPLICATE TRANSFORMATION TESTS ====================
    
    @Test
    void testDeduplicateHandlerType() {
        assertEquals("deduplicate", deduplicateHandler.getType());
        assertTrue(deduplicateHandler.requiresBatchProcessing());
    }
    
    @Test
    void testDeduplicateByKeyFields() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(createRowWithCity("Alice", 30, "NYC"));
        rows.add(createRowWithCity("Alice", 30, "NYC"));
        rows.add(createRowWithCity("Bob", 25, "LA"));
        
        Map<String, Object> config = new HashMap<>();
        config.put("key_fields", Arrays.asList("name", "city"));
        config.put("keep", "first");
        
        List<Map<String, Object>> result = deduplicateHandler.transformBatch(rows, config, metrics);
        
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).get("name"));
        assertEquals("Bob", result.get(1).get("name"));
    }
    
    @Test
    void testDeduplicateKeepLast() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(createRow("Alice", 30));
        rows.add(createRow("Alice", 31));
        rows.add(createRow("Bob", 25));
        
        Map<String, Object> config = new HashMap<>();
        config.put("key_fields", Collections.singletonList("name"));
        config.put("keep", "last");
        
        List<Map<String, Object>> result = deduplicateHandler.transformBatch(rows, config, metrics);
        
        assertEquals(2, result.size());
        // Last Alice should have age 31
        boolean foundAlice31 = result.stream()
            .anyMatch(r -> "Alice".equals(r.get("name")) && 31.0 == (Double) r.get("age"));
        assertTrue(foundAlice31);
    }
    
    // ==================== JOIN TRANSFORMATION TESTS ====================
    
    @Test
    void testJoinHandlerType() {
        assertEquals("join", joinHandler.getType());
        assertTrue(joinHandler.requiresBatchProcessing());
    }
    
    @Test
    void testJoinWithAggregation() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(createRowWithAmount("customer1", 100));
        rows.add(createRowWithAmount("customer1", 200));
        rows.add(createRowWithAmount("customer2", 150));
        
        Map<String, Object> config = new HashMap<>();
        config.put("key_fields", Collections.singletonList("customer_id"));
        Map<String, String> aggs = new HashMap<>();
        aggs.put("total_amount", "SUM(amount)");
        config.put("aggregations", aggs);
        
        List<Map<String, Object>> result = joinHandler.transformBatch(rows, config, metrics);
        
        assertEquals(2, result.size());
        assertEquals(300.0, (Double) result.stream()
            .filter(r -> "customer1".equals(r.get("customer_id")))
            .findFirst().get().get("total_amount"), 0.01);
    }
    
    // ==================== PIVOT TRANSFORMATION TESTS ====================
    
    @Test
    void testPivotHandlerType() {
        assertEquals("pivot", pivotHandler.getType());
        assertTrue(pivotHandler.requiresBatchProcessing());
    }
    
    @Test
    void testPivotBasic() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(createRowWithProductAmount("2025-01-01", "ProductA", 100));
        rows.add(createRowWithProductAmount("2025-01-01", "ProductB", 200));
        rows.add(createRowWithProductAmount("2025-01-02", "ProductA", 150));
        
        Map<String, Object> config = new HashMap<>();
        config.put("index_fields", Collections.singletonList("date"));
        config.put("pivot_field", "product");
        config.put("value_field", "amount");
        config.put("aggregation", "SUM");
        
        List<Map<String, Object>> result = pivotHandler.transformBatch(rows, config, metrics);
        
        assertEquals(2, result.size());
        assertTrue(result.get(0).containsKey("producta_amount"));
        assertTrue(result.get(0).containsKey("productb_amount"));
    }
    
    // ==================== VALIDATION TRANSFORMATION TESTS ====================
    
    @Test
    void testValidationHandlerType() {
        assertEquals("validation", validationHandler.getType());
        assertFalse(validationHandler.requiresBatchProcessing());
    }
    
    @Test
    void testValidationEmailPattern() {
        Map<String, Object> row = new HashMap<>();
        row.put("email", "test@example.com");
        
        Map<String, Object> emailRule = new HashMap<>();
        emailRule.put("pattern", "^[^@]+@[^@]+\\.[^@]+$");
        
        Map<String, Object> config = new HashMap<>();
        Map<String, Map<String, Object>> rules = new HashMap<>();
        rules.put("email", emailRule);
        config.put("rules", rules);
        
        Map<String, Object> result = validationHandler.transformRow(row, config, metrics);
        assertNotNull(result, "Valid email should pass");
    }
    
    @Test
    void testValidationEmailPatternFail() {
        Map<String, Object> row = new HashMap<>();
        row.put("email", "invalid-email");
        
        Map<String, Object> emailRule = new HashMap<>();
        emailRule.put("pattern", "^[^@]+@[^@]+\\.[^@]+$");
        
        Map<String, Object> config = new HashMap<>();
        Map<String, Map<String, Object>> rules = new HashMap<>();
        rules.put("email", emailRule);
        config.put("rules", rules);
        
        Map<String, Object> result = validationHandler.transformRow(row, config, metrics);
        assertNull(result, "Invalid email should be filtered");
    }
    
    @Test
    void testValidationRequiredField() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "");
        
        Map<String, Object> nameRule = new HashMap<>();
        nameRule.put("required", true);
        
        Map<String, Object> config = new HashMap<>();
        Map<String, Map<String, Object>> rules = new HashMap<>();
        rules.put("name", nameRule);
        config.put("rules", rules);
        
        Map<String, Object> result = validationHandler.transformRow(row, config, metrics);
        assertNull(result, "Empty required field should be filtered");
    }
    
    @Test
    void testValidationNumericRange() {
        Map<String, Object> row = new HashMap<>();
        row.put("age", 25);
        
        Map<String, Object> ageRule = new HashMap<>();
        ageRule.put("min", 0);
        ageRule.put("max", 150);
        
        Map<String, Object> config = new HashMap<>();
        Map<String, Map<String, Object>> rules = new HashMap<>();
        rules.put("age", ageRule);
        config.put("rules", rules);
        
        Map<String, Object> result = validationHandler.transformRow(row, config, metrics);
        assertNotNull(result, "Valid age should pass");
    }
    
    @Test
    void testValidationNumericRangeFail() {
        Map<String, Object> row = new HashMap<>();
        row.put("age", 200);
        
        Map<String, Object> ageRule = new HashMap<>();
        ageRule.put("min", 0);
        ageRule.put("max", 150);
        
        Map<String, Object> config = new HashMap<>();
        Map<String, Map<String, Object>> rules = new HashMap<>();
        rules.put("age", ageRule);
        config.put("rules", rules);
        
        Map<String, Object> result = validationHandler.transformRow(row, config, metrics);
        assertNull(result, "Out-of-range age should be filtered");
    }
    
    @Test
    void testValidationStringLength() {
        Map<String, Object> row = new HashMap<>();
        row.put("code", "AB123");
        
        Map<String, Object> codeRule = new HashMap<>();
        codeRule.put("min_length", 3);
        codeRule.put("max_length", 10);
        
        Map<String, Object> config = new HashMap<>();
        Map<String, Map<String, Object>> rules = new HashMap<>();
        rules.put("code", codeRule);
        config.put("rules", rules);
        
        Map<String, Object> result = validationHandler.transformRow(row, config, metrics);
        assertNotNull(result, "Valid code length should pass");
    }
    
    @Test
    void testValidationAllowedValues() {
        Map<String, Object> row = new HashMap<>();
        row.put("status", "active");
        
        Map<String, Object> statusRule = new HashMap<>();
        statusRule.put("allowed_values", Arrays.asList("active", "inactive", "pending"));
        
        Map<String, Object> config = new HashMap<>();
        Map<String, Map<String, Object>> rules = new HashMap<>();
        rules.put("status", statusRule);
        config.put("rules", rules);
        
        Map<String, Object> result = validationHandler.transformRow(row, config, metrics);
        assertNotNull(result, "Allowed status should pass");
    }
    
    @Test
    void testValidationAllowedValuesFail() {
        Map<String, Object> row = new HashMap<>();
        row.put("status", "unknown");
        
        Map<String, Object> statusRule = new HashMap<>();
        statusRule.put("allowed_values", Arrays.asList("active", "inactive", "pending"));
        
        Map<String, Object> config = new HashMap<>();
        Map<String, Map<String, Object>> rules = new HashMap<>();
        rules.put("status", statusRule);
        config.put("rules", rules);
        
        Map<String, Object> result = validationHandler.transformRow(row, config, metrics);
        assertNull(result, "Unknown status should be filtered");
    }
    
    // ==================== HELPER METHODS ====================
    
    private Map<String, Object> createRow(String name, double age) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("age", age);
        return row;
    }
    
    private Map<String, Object> createRowWithCity(String name, double age, String city) {
        Map<String, Object> row = createRow(name, age);
        row.put("city", city);
        return row;
    }
    
    private Map<String, Object> createRowWithAmount(String customerId, double amount) {
        Map<String, Object> row = new HashMap<>();
        row.put("customer_id", customerId);
        row.put("amount", amount);
        return row;
    }
    
    private Map<String, Object> createRowWithProductAmount(String date, String product, double amount) {
        Map<String, Object> row = new HashMap<>();
        row.put("date", date);
        row.put("product", product);
        row.put("amount", amount);
        return row;
    }
}
