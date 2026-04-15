package com.example.pipeline.service;

import com.example.pipeline.model.TransformationConfig;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransformationService {
    // Buffers rows when an aggregate stage is configured.
    private List<Map<String, Object>> aggregateBuffer = new ArrayList<>();

    /**
     * Applies transformation stages in order; returns null when a row is filtered or buffered for aggregate.
     */
    public Map<String, Object> applyTransformations(Map<String, Object> data,
            List<TransformationConfig> configs, IngestionService.IngestionMetrics metrics) {
        if (configs == null || configs.isEmpty()) {
            return data;
        }

        Map<String, Object> currentData = new HashMap<>(data);

        for (TransformationConfig config : configs) {
            String type = config.getType().toLowerCase();
            Map<String, Object> params = config.getConfig();

            switch (type) {
                case "filter":
                    if (!applyFilter(currentData, params)) {
                        return null; // Row filtered out
                    }
                    break;
                case "map":
                    currentData = applyMap(currentData, params);
                    break;
                case "aggregate":
                    // Aggregate is handled differently - buffer rows
                    aggregateBuffer.add(new HashMap<>(currentData));
                    return null; // Don't process individual rows
                default:
                    throw new IllegalArgumentException("Unsupported transformation: " + type);
            }
        }

        return currentData;
    }

    /**
     * Produces final aggregate rows from the buffered input rows.
     */
    public List<Map<String, Object>> finalizeAggregations(List<TransformationConfig> configs) {
        if (configs == null || aggregateBuffer.isEmpty()) {
            return Collections.emptyList();
        }

        for (TransformationConfig config : configs) {
            if ("aggregate".equalsIgnoreCase(config.getType())) {
                return applyAggregate(aggregateBuffer, config.getConfig());
            }
        }

        return Collections.emptyList();
    }

    /**
     * Clears aggregate buffer before a new execution.
     */
    public void resetAggregateBuffer() {
        aggregateBuffer.clear();
    }

    /**
     * Evaluates a filter condition from the transformation config.
     */
    private boolean applyFilter(Map<String, Object> row, Map<String, Object> config) {
        String condition = (String) config.get("condition");
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }

        return evaluateCondition(row, condition);
    }

    /**
     * Recursive evaluator for the filter DSL supporting AND/OR/NOT and comparison operators.
     */
    private boolean evaluateCondition(Map<String, Object> row, String condition) {
        condition = condition.trim();

        // Handle logical operators
        if (condition.toUpperCase().contains(" AND ")) {
            String[] parts = condition.split("(?i)\\s+AND\\s+", 2);
            return evaluateCondition(row, parts[0]) && evaluateCondition(row, parts[1]);
        }

        if (condition.toUpperCase().contains(" OR ")) {
            String[] parts = condition.split("(?i)\\s+OR\\s+", 2);
            return evaluateCondition(row, parts[0]) || evaluateCondition(row, parts[1]);
        }

        if (condition.toUpperCase().startsWith("NOT ")) {
            return !evaluateCondition(row, condition.substring(4));
        }

        // Handle comparison operators
        String[] operators = {">=", "<=", "!=", "==", ">", "<", "="};

        for (String op : operators) {
            if (condition.contains(op)) {
                String[] parts = condition.split(op, 2);
                if (parts.length == 2) {
                    String leftSide = parts[0].trim();
                    String rightSide = parts[1].trim();

                    Object leftValue = getValueIgnoreCase(row, leftSide);
                    Object rightValue = parseValue(rightSide);

                    return compareValues(leftValue, rightValue, op);
                }
            }
        }

        return true;
    }

    /**
     * Compares values numerically when possible, otherwise uses string comparison.
     */
    private boolean compareValues(Object left, Object right, String operator) {
        if (left == null || right == null) return false;

        try {
            double leftNum = Double.parseDouble(left.toString());
            double rightNum = Double.parseDouble(right.toString());

            switch (operator) {
                case ">": return leftNum > rightNum;
                case "<": return leftNum < rightNum;
                case ">=": return leftNum >= rightNum;
                case "<=": return leftNum <= rightNum;
                case "==":
                case "=": return Math.abs(leftNum - rightNum) < 0.0001;
                case "!=": return Math.abs(leftNum - rightNum) >= 0.0001;
                default: return false;
            }
        } catch (NumberFormatException e) {
            // String comparison
            String leftStr = left.toString();
            String rightStr = right.toString();

            switch (operator) {
                case "==":
                case "=": return leftStr.equalsIgnoreCase(rightStr);
                case "!=": return !leftStr.equalsIgnoreCase(rightStr);
                case ">": return leftStr.compareTo(rightStr) > 0;
                case "<": return leftStr.compareTo(rightStr) < 0;
                case ">=": return leftStr.compareTo(rightStr) >= 0;
                case "<=": return leftStr.compareTo(rightStr) <= 0;
                default: return false;
            }
        }
    }

    /**
     * Applies map operations to create or overwrite target fields.
     */
    private Map<String, Object> applyMap(Map<String, Object> row, Map<String, Object> config) {
        Map<String, String> operations = (Map<String, String>) config.get("operations");
        Map<String, Object> result = new HashMap<>(row);

        if (operations != null) {
            operations.forEach((targetKey, expression) -> {
                Object value = evaluateExpression(row, expression);
                result.put(targetKey.toLowerCase(), value);
            });
        }

        return result;
    }

    /**
     * Evaluates supported expression functions (string, math, date) or direct field references.
     */
    private Object evaluateExpression(Map<String, Object> row, String expression) {
        expression = expression.trim();

        // String functions
        if (expression.toUpperCase().startsWith("UPPER(")) {
            String field = extractFunctionArg(expression, "UPPER");
            Object value = getValueIgnoreCase(row, field);
            return value != null ? value.toString().toUpperCase() : "";
        }

        if (expression.toUpperCase().startsWith("LOWER(")) {
            String field = extractFunctionArg(expression, "LOWER");
            Object value = getValueIgnoreCase(row, field);
            return value != null ? value.toString().toLowerCase() : "";
        }

        if (expression.toUpperCase().startsWith("CONCAT(")) {
            String args = extractFunctionArg(expression, "CONCAT");
            String[] fields = args.split(",");
            StringBuilder result = new StringBuilder();
            for (String field : fields) {
                Object value = getValueIgnoreCase(row, field.trim());
                if (value != null) result.append(value);
            }
            return result.toString();
        }

        // Math functions
        if (expression.toUpperCase().startsWith("ADD(") || expression.contains("+")) {
            return evaluateMathExpression(row, expression, "ADD");
        }

        if (expression.toUpperCase().startsWith("SUBTRACT(") || expression.contains("-")) {
            return evaluateMathExpression(row, expression, "SUBTRACT");
        }

        if (expression.toUpperCase().startsWith("MULTIPLY(") || expression.contains("*")) {
            return evaluateMathExpression(row, expression, "MULTIPLY");
        }

        if (expression.toUpperCase().startsWith("DIVIDE(") || expression.contains("/")) {
            return evaluateMathExpression(row, expression, "DIVIDE");
        }

        // Date functions
        if (expression.toUpperCase().startsWith("DATE_FORMAT(")) {
            return evaluateDateFormat(row, expression);
        }

        if (expression.toUpperCase().startsWith("DATE_ADD(")) {
            return evaluateDateAdd(row, expression);
        }

        // Direct field reference
        Object value = getValueIgnoreCase(row, expression);
        return value != null ? value : expression;
    }

    /**
     * Extracts function argument content from strings like FUNC(arg1,arg2).
     */
    private String extractFunctionArg(String expression, String funcName) {
        int start = expression.toUpperCase().indexOf(funcName + "(") + funcName.length() + 1;
        int end = expression.lastIndexOf(")");
        return expression.substring(start, end).trim();
    }

    /**
     * Evaluates math expressions in function or infix form and returns numeric result.
     */
    private Object evaluateMathExpression(Map<String, Object> row, String expression, String operation) {
        try {
            String args;
            String operator;

            if (expression.toUpperCase().startsWith(operation + "(")) {
                args = extractFunctionArg(expression, operation);
                operator = operation;
            } else {
                // Handle infix notation
                if (expression.contains("+")) {
                    operator = "ADD";
                    args = expression.replace("+", ",");
                } else if (expression.contains("-")) {
                    operator = "SUBTRACT";
                    args = expression.replace("-", ",");
                } else if (expression.contains("*")) {
                    operator = "MULTIPLY";
                    args = expression.replace("*", ",");
                } else if (expression.contains("/")) {
                    operator = "DIVIDE";
                    args = expression.replace("/", ",");
                } else {
                    return 0;
                }
            }

            String[] operands = args.split(",");
            if (operands.length < 2) return 0;

            double left = parseDouble(row, operands[0].trim());
            double right = parseDouble(row, operands[1].trim());

            switch (operator) {
                case "ADD": return left + right;
                case "SUBTRACT": return left - right;
                case "MULTIPLY": return left * right;
                case "DIVIDE": return right != 0 ? left / right : 0;
                default: return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Resolves operand as field value first, then falls back to numeric literal parsing.
     */
    private double parseDouble(Map<String, Object> row, String value) {
        Object obj = getValueIgnoreCase(row, value);
        if (obj != null) {
            return Double.parseDouble(obj.toString());
        }
        return Double.parseDouble(value);
    }

    /**
     * Formats datetime fields according to DATE_FORMAT expression.
     */
    private Object evaluateDateFormat(Map<String, Object> row, String expression) {
        try {
            String args = extractFunctionArg(expression, "DATE_FORMAT");
            String[] parts = args.split(",");
            String field = parts[0].trim();
            String format = parts.length > 1 ? parts[1].trim().replace("'", "") : "yyyy-MM-dd";

            Object value = getValueIgnoreCase(row, field);
            if (value != null) {
                LocalDateTime date = LocalDateTime.parse(value.toString());
                return date.format(DateTimeFormatter.ofPattern(format));
            }
        } catch (Exception e) {
            // Return original value on error
        }
        return "";
    }

    /**
     * Adds days to date fields according to DATE_ADD expression.
     */
    private Object evaluateDateAdd(Map<String, Object> row, String expression) {
        try {
            String args = extractFunctionArg(expression, "DATE_ADD");
            String[] parts = args.split(",");
            String field = parts[0].trim();
            int days = Integer.parseInt(parts[1].trim());

            Object value = getValueIgnoreCase(row, field);
            if (value != null) {
                LocalDate date = LocalDate.parse(value.toString());
                return date.plusDays(days).toString();
            }
        } catch (Exception e) {
            // Return original value on error
        }
        return "";
    }

    /**
     * Groups rows and computes configured aggregate outputs.
     */
    private List<Map<String, Object>> applyAggregate(List<Map<String, Object>> data, Map<String, Object> config) {
        List<String> groupBy = (List<String>) config.get("group_by");
        Map<String, String> aggregations = (Map<String, String>) config.get("aggregations");

        if (groupBy == null || groupBy.isEmpty()) {
            // Aggregate all rows
            Map<String, Object> result = new HashMap<>();
            if (aggregations != null) {
                aggregations.forEach((resultKey, aggFunction) -> {
                    result.put(resultKey, performAggregation(data, aggFunction));
                });
            }
            return Collections.singletonList(result);
        }

        // Group by specified columns
        Map<String, List<Map<String, Object>>> groups = data.stream()
            .collect(Collectors.groupingBy(row -> {
                StringBuilder key = new StringBuilder();
                for (String field : groupBy) {
                    Object value = getValueIgnoreCase(row, field);
                    key.append(value != null ? value.toString() : "null").append("|");
                }
                return key.toString();
            }));

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            Map<String, Object> result = new HashMap<>();

            // Add group by fields
            List<Map<String, Object>> groupData = entry.getValue();
            if (!groupData.isEmpty()) {
                Map<String, Object> firstRow = groupData.get(0);
                for (String field : groupBy) {
                    result.put(field.toLowerCase(), getValueIgnoreCase(firstRow, field));
                }
            }

            // Calculate aggregations
            if (aggregations != null) {
                aggregations.forEach((resultKey, aggFunction) -> {
                    result.put(resultKey, performAggregation(groupData, aggFunction));
                });
            }

            results.add(result);
        }

        return results;
    }

    /**
     * Executes one aggregate function (COUNT/SUM/AVG/MIN/MAX).
     */
    private Object performAggregation(List<Map<String, Object>> data, String aggFunction) {
        aggFunction = aggFunction.trim().toUpperCase();

        if (aggFunction.startsWith("COUNT(")) {
            return data.size();
        }

        if (aggFunction.startsWith("SUM(")) {
            String field = extractFunctionArg(aggFunction, "SUM");
            return data.stream()
                .mapToDouble(row -> {
                    Object val = getValueIgnoreCase(row, field);
                    return val != null ? Double.parseDouble(val.toString()) : 0;
                })
                .sum();
        }

        if (aggFunction.startsWith("AVG(")) {
            String field = extractFunctionArg(aggFunction, "AVG");
            return data.stream()
                .mapToDouble(row -> {
                    Object val = getValueIgnoreCase(row, field);
                    return val != null ? Double.parseDouble(val.toString()) : 0;
                })
                .average()
                .orElse(0.0);
        }

        if (aggFunction.startsWith("MIN(")) {
            String field = extractFunctionArg(aggFunction, "MIN");
            return data.stream()
                .mapToDouble(row -> {
                    Object val = getValueIgnoreCase(row, field);
                    return val != null ? Double.parseDouble(val.toString()) : Double.MAX_VALUE;
                })
                .min()
                .orElse(0.0);
        }

        if (aggFunction.startsWith("MAX(")) {
            String field = extractFunctionArg(aggFunction, "MAX");
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
     * Case-insensitive key lookup helper.
     */
    private Object getValueIgnoreCase(Map<String, Object> data, String targetKey) {
        for (String key : data.keySet()) {
            if (key.equalsIgnoreCase(targetKey)) {
                return data.get(key);
            }
        }
        return null;
    }

    /**
     * Parses literal values used in filter expressions.
     */
    private Object parseValue(String value) {
        value = value.trim();
        if (value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
