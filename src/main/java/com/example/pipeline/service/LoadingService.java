package com.example.pipeline.service;

import com.example.pipeline.model.DestinationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

@Service
public class LoadingService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${pipeline.output.directory:./output}")
    private String outputDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<Map<String, Object>> batchBuffer = new ArrayList<>();
    private int batchSize = 100;
    private DestinationConfig currentDestination;

    /**
     * Routes a single transformed row to the configured destination type.
     */
    public void load(Map<String, Object> data, DestinationConfig destination) {
        String type = destination.getType();

        if ("database".equalsIgnoreCase(type)) {
            loadToDatabase(data, destination);
        } else if ("file".equalsIgnoreCase(type)) {
            loadToFile(data, destination);
        } else {
            System.out.println("Final Output: " + data);
        }
    }

    /**
     * Writes a batch of rows directly to the destination.
     */
    public void loadBatch(List<Map<String, Object>> dataList, DestinationConfig destination) {
        String type = destination.getType();

        if ("database".equalsIgnoreCase(type)) {
            batchInsertToDatabase(dataList, destination);
        } else if ("file".equalsIgnoreCase(type)) {
            batchWriteToFile(dataList, destination);
        }
    }

    /**
     * Flushes in-memory buffered rows to avoid leaving partial writes pending.
     */
    public void flushBatch() {
        if (!batchBuffer.isEmpty() && currentDestination != null) {
            loadBatch(new ArrayList<>(batchBuffer), currentDestination);
            batchBuffer.clear();
        }
    }

    /**
     * Buffers rows for batched database insert.
     */
    private void loadToDatabase(Map<String, Object> data, DestinationConfig destination) {
        // Add to batch buffer
        batchBuffer.add(data);
        currentDestination = destination;

        if (batchBuffer.size() >= batchSize) {
            flushBatch();
        }
    }

    /**
     * Executes batched insert/upsert/overwrite logic for database destinations.
     */
    private void batchInsertToDatabase(List<Map<String, Object>> dataList, DestinationConfig destination) {
        Map<String, Object> config = destination.getConfig();
        String tableName = (String) config.getOrDefault("table", "processed_customers");
        String writeMode = (String) config.getOrDefault("mode", "append");

        if (dataList.isEmpty()) return;

        // Handle write modes
        if ("overwrite".equalsIgnoreCase(writeMode)) {
            try {
                jdbcTemplate.execute("TRUNCATE TABLE " + tableName);
            } catch (Exception e) {
                jdbcTemplate.execute("DELETE FROM " + tableName);
            }
        }

        // Get all unique columns from the data
        Set<String> allColumns = new LinkedHashSet<>();
        for (Map<String, Object> row : dataList) {
            allColumns.addAll(row.keySet());
        }

        List<String> columns = new ArrayList<>(allColumns);
        String columnsList = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));

        String sql;
        if ("upsert".equalsIgnoreCase(writeMode)) {
            // MySQL specific upsert
            String updateClause = columns.stream()
                .map(col -> col + " = VALUES(" + col + ")")
                .collect(Collectors.joining(", "));
            sql = String.format("INSERT INTO %s (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s",
                tableName, columnsList, placeholders, updateClause);
        } else {
            sql = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columnsList, placeholders);
        }

        // Batch insert
        List<Object[]> batchArgs = new ArrayList<>();
        for (Map<String, Object> row : dataList) {
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                values[i] = row.get(columns.get(i));
            }
            batchArgs.add(values);
        }

        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    /**
     * Buffers rows for batched file writes.
     */
    private void loadToFile(Map<String, Object> data, DestinationConfig destination) {
        // Buffer for file writing
        batchBuffer.add(data);
        currentDestination = destination;

        if (batchBuffer.size() >= batchSize) {
            flushBatch();
        }
    }

    /**
     * Writes rows to CSV or JSON files with optional gzip compression.
     */
    private void batchWriteToFile(List<Map<String, Object>> dataList, DestinationConfig destination) {
        Map<String, Object> config = destination.getConfig();
        String format = (String) config.getOrDefault("format", "csv");
        String filePath = (String) config.get("file_path");
        boolean compress = (boolean) config.getOrDefault("compress", false);

        if (filePath == null) {
            filePath = outputDirectory + "/output_" + System.currentTimeMillis() + "." + format;
        }

        try {
            // Create output directory if needed
            Files.createDirectories(Paths.get(filePath).getParent());

            if ("csv".equalsIgnoreCase(format)) {
                writeToCSV(dataList, filePath, compress);
            } else if ("json".equalsIgnoreCase(format)) {
                writeToJSON(dataList, filePath, compress);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to file: " + e.getMessage(), e);
        }
    }

    /**
     * Writes rows as CSV and includes a header row derived from keys.
     */
    private void writeToCSV(List<Map<String, Object>> dataList, String filePath, boolean compress) throws IOException {
        if (dataList.isEmpty()) return;

        // Get all columns
        Set<String> allColumns = new LinkedHashSet<>();
        for (Map<String, Object> row : dataList) {
            allColumns.addAll(row.keySet());
        }

        String[] headers = allColumns.toArray(new String[0]);

        OutputStream outputStream;
        if (compress) {
            filePath += ".gz";
            outputStream = new GZIPOutputStream(new FileOutputStream(filePath, true));
        } else {
            outputStream = new FileOutputStream(filePath, true);
        }

        try (Writer writer = new OutputStreamWriter(outputStream);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers))) {

            for (Map<String, Object> row : dataList) {
                List<Object> values = new ArrayList<>();
                for (String header : headers) {
                    values.add(row.getOrDefault(header, ""));
                }
                csvPrinter.printRecord(values);
            }
            csvPrinter.flush();
        }
    }

    /**
     * Writes rows as JSON payload.
     */
    private void writeToJSON(List<Map<String, Object>> dataList, String filePath, boolean compress) throws IOException {
        if (dataList.isEmpty()) return;

        OutputStream outputStream;
        if (compress) {
            filePath += ".gz";
            outputStream = new GZIPOutputStream(new FileOutputStream(filePath, true));
        } else {
            outputStream = new FileOutputStream(filePath, true);
        }

        try (Writer writer = new OutputStreamWriter(outputStream)) {
            objectMapper.writeValue(writer, dataList);
        }
    }

    private Object getValueIgnoreCase(Map<String, Object> data, String targetKey) {
        for (String key : data.keySet()) {
            if (key.equalsIgnoreCase(targetKey)) {
                return data.get(key);
            }
        }
        return null;
    }
}
