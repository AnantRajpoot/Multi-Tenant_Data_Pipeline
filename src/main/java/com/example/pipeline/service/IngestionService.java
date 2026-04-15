package com.example.pipeline.service;

import com.example.pipeline.model.Pipeline;
import com.example.pipeline.model.SourceConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class IngestionService {
    @Autowired
    private TransformationService transformationService;
    @Autowired
    private LoadingService loadingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Entry point for pipeline ingestion. Routes by source type and fills execution metrics.
     */
    public IngestionMetrics startIngestion(Pipeline pipeline) throws Exception {
        IngestionMetrics metrics = new IngestionMetrics();
        metrics.startTime = new Date();

        SourceConfig sourceConfig = pipeline.getSource();
        String type = sourceConfig.getType();

        try {
            if ("csv".equalsIgnoreCase(type)) {
                ingestCSV(pipeline, metrics);
            } else if ("json".equalsIgnoreCase(type)) {
                ingestJSON(pipeline, metrics);
            } else {
                throw new IllegalArgumentException("Unsupported source type: " + type);
            }
            metrics.endTime = new Date();
            metrics.success = true;
        } catch (Exception e) {
            metrics.endTime = new Date();
            metrics.success = false;
            metrics.errorMessage = e.getMessage();
            throw e;
        }

        return metrics;
    }

    /**
     * Streams CSV rows, applies transformations, and infers schema from a sample window.
     */
    private void ingestCSV(Pipeline pipeline, IngestionMetrics metrics) throws Exception {
        SourceConfig sourceConfig = pipeline.getSource();
        Map<String, Object> config = sourceConfig.getConfig();
        String filePath = (String) config.get("file_path");
        String delimiter = (String) config.getOrDefault("delimiter", ",");
        String encoding = (String) config.getOrDefault("encoding", "UTF-8");
        boolean hasHeader = (boolean) config.getOrDefault("has_header", true);

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, Charset.forName(encoding)))) {
            String line;
            String[] headers = null;
            Map<String, String> inferredSchema = new HashMap<>();

            if (hasHeader) {
                String headerLine = reader.readLine();
                if (headerLine != null) {
                    headers = headerLine.replace("\uFEFF", "").split(delimiter);
                    // Initialize schema inference
                    for (String header : headers) {
                        inferredSchema.put(header.trim().toLowerCase(), "unknown");
                    }
                }
            }

            // Capture up to 100 rows for lightweight schema inference.
            List<String[]> sampleRows = new ArrayList<>();
            int sampleCount = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] values = line.split(delimiter, -1);
                metrics.recordsRead++;

                // Skip duplicate header lines
                if (headers != null && values.length == headers.length) {
                    boolean isHeader = true;
                    for (int i = 0; i < headers.length; i++) {
                        if (!values[i].equalsIgnoreCase(headers[i])) {
                            isHeader = false;
                            break;
                        }
                    }
                    if (isHeader) continue;
                }

                // Collect samples for schema inference.
                if (sampleCount < 100) {
                    sampleRows.add(values);
                    sampleCount++;
                }

                try {
                    Map<String, Object> rowData = createRowMap(headers, values);
                    processRow(pipeline, rowData, metrics);
                } catch (Exception e) {
                    // Log row-level failures but continue the stream.
                    metrics.recordsFailed++;
                    metrics.errorLogs.add("Row " + metrics.recordsRead + ": " + e.getMessage());
                    // Continue processing other rows
                }
            }

            // Persist inferred schema on source config for downstream use/inspection.
            if (!sampleRows.isEmpty() && headers != null) {
                inferSchema(headers, sampleRows, inferredSchema);
                sourceConfig.setSchema(inferredSchema);
            }
        }
    }

    /**
     * Ingests JSON in object, array, or JSONL format.
     */
    private void ingestJSON(Pipeline pipeline, IngestionMetrics metrics) throws Exception {
        SourceConfig sourceConfig = pipeline.getSource();
        Map<String, Object> config = sourceConfig.getConfig();
        String filePath = (String) config.get("file_path");
        String format = (String) config.getOrDefault("format", "array"); // "object", "array", "jsonl"

        String content = new String(Files.readAllBytes(Paths.get(filePath)));

        if ("jsonl".equalsIgnoreCase(format)) {
            // JSON Lines format - one JSON object per line
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    metrics.recordsRead++;
                    try {
                        JsonNode jsonNode = objectMapper.readTree(line);
                        Map<String, Object> rowData = objectMapper.convertValue(jsonNode, Map.class);
                        processRow(pipeline, rowData, metrics);
                    } catch (Exception e) {
                        metrics.recordsFailed++;
                        metrics.errorLogs.add("Line " + metrics.recordsRead + ": " + e.getMessage());
                    }
                }
            }
        } else if ("array".equalsIgnoreCase(format)) {
            // JSON array of objects
            JsonNode rootNode = objectMapper.readTree(content);
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    metrics.recordsRead++;
                    try {
                        Map<String, Object> rowData = objectMapper.convertValue(node, Map.class);
                        processRow(pipeline, rowData, metrics);
                    } catch (Exception e) {
                        metrics.recordsFailed++;
                        metrics.errorLogs.add("Record " + metrics.recordsRead + ": " + e.getMessage());
                    }
                }
            }
        } else if ("object".equalsIgnoreCase(format)) {
            // Single JSON object
            metrics.recordsRead++;
            try {
                JsonNode rootNode = objectMapper.readTree(content);
                Map<String, Object> rowData = objectMapper.convertValue(rootNode, Map.class);
                processRow(pipeline, rowData, metrics);
            } catch (Exception e) {
                metrics.recordsFailed++;
                metrics.errorLogs.add("JSON object: " + e.getMessage());
            }
        }
    }

    /**
     * Applies transformations, then routes accepted rows to destination loading.
     */
    private void processRow(Pipeline pipeline, Map<String, Object> rowData, IngestionMetrics metrics) {
        Map<String, Object> transformedData = transformationService.applyTransformations(
            rowData, pipeline.getTransformations(), metrics);

        if (transformedData != null) {
            loadingService.load(transformedData, pipeline.getDestination());
            metrics.recordsWritten++;
        } else {
            metrics.recordsFiltered++;
        }
    }

    /**
     * Normalizes a raw row into a key-value map using headers when available.
     */
    private Map<String, Object> createRowMap(String[] headers, String[] values) {
        Map<String, Object> rowMap = new HashMap<>();
        if (headers != null && values.length <= headers.length) {
            for (int i = 0; i < values.length; i++) {
                rowMap.put(headers[i].toLowerCase().trim(), values[i].trim());
            }
        } else if (headers != null && values.length > headers.length) {
            for (int i = 0; i < headers.length; i++) {
                rowMap.put(headers[i].toLowerCase().trim(), values[i].trim());
            }
        } else {
            // Fallback for no headers
            for (int i = 0; i < values.length; i++) {
                rowMap.put("column_" + i, values[i].trim());
            }
        }
        return rowMap;
    }

    /**
     * Infers a type for each header column from sampled rows.
     */
    private void inferSchema(String[] headers, List<String[]> sampleRows, Map<String, String> schema) {
        for (int colIndex = 0; colIndex < headers.length; colIndex++) {
            String header = headers[colIndex].trim().toLowerCase();
            String inferredType = inferColumnType(colIndex, sampleRows);
            schema.put(header, inferredType);
        }
    }

    /**
     * Determines best-fit type using an 80% confidence threshold across samples.
     */
    private String inferColumnType(int colIndex, List<String[]> sampleRows) {
        int intCount = 0, decimalCount = 0, boolCount = 0, dateCount = 0, totalCount = 0;

        for (String[] row : sampleRows) {
            if (colIndex >= row.length) continue;
            String value = row[colIndex].trim();
            if (value.isEmpty()) continue;

            totalCount++;

            if (isInteger(value)) intCount++;
            if (isDecimal(value)) decimalCount++;
            if (isBoolean(value)) boolCount++;
            if (isDate(value)) dateCount++;
        }

        if (totalCount == 0) return "string";

        double threshold = 0.8;
        if ((double) intCount / totalCount >= threshold) return "integer";
        if ((double) decimalCount / totalCount >= threshold) return "decimal";
        if ((double) boolCount / totalCount >= threshold) return "boolean";
        if ((double) dateCount / totalCount >= threshold) return "datetime";

        return "string";
    }

    /**
     * Checks whether the value can be parsed as an integer.
     */
    private boolean isInteger(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Checks whether the value can be parsed as a decimal number.
     */
    private boolean isDecimal(String value) {
        try {
            Double.parseDouble(value);
            return value.contains(".");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Checks whether the value is a boolean literal.
     */
    private boolean isBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
    }

    /**
     * Checks common date/time patterns used by incoming data.
     */
    private boolean isDate(String value) {
        String[] patterns = {
            "yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy",
            "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"
        };

        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                LocalDateTime.parse(value, formatter);
                return true;
            } catch (DateTimeParseException e) {
                // Try next pattern
            }
        }
        return false;
    }

    /**
     * Execution counters collected during one ingestion run.
     */
    public static class IngestionMetrics {
        public Date startTime;
        public Date endTime;
        public long recordsRead = 0;
        public long recordsWritten = 0;
        public long recordsFiltered = 0;
        public long recordsFailed = 0;
        public boolean success = false;
        public String errorMessage = "";
        public List<String> errorLogs = new ArrayList<>();

        /**
         * Returns run duration in milliseconds when both timestamps are present.
         */
        public long getDurationMillis() {
            if (startTime != null && endTime != null) {
                return endTime.getTime() - startTime.getTime();
            }
            return 0;
        }
    }
}
