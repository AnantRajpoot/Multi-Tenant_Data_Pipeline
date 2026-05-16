package com.example.pipeline.service.source;

import com.example.pipeline.model.Pipeline;
import com.example.pipeline.model.SourceConfig;
import com.example.pipeline.util.EnvVarResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * S3/MinIO/Object Storage source handler.
 * Supports reading from AWS S3, MinIO, and other S3-compatible storage.
 */
@Component
public class S3SourceHandler implements SourceHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public String getType() {
        return "s3";
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void fetchRowsAndProcess(Pipeline pipeline, Consumer<Map<String, Object>> rowConsumer) throws Exception {
        SourceConfig src = pipeline.getSource();
        Map<String, Object> rawConfig = src.getConfig();
        Map<String, Object> config = EnvVarResolver.resolveMap(rawConfig);
        
        // Extract S3 configuration
        String endpoint = (String) config.get("endpoint");
        String bucket = (String) config.get("bucket");
        String key = (String) config.get("key");
        String accessKey = (String) config.get("access_key");
        String secretKey = (String) config.get("secret_key");
        String region = (String) config.getOrDefault("region", "us-east-1");
        Object formatObj = config.getOrDefault("format", "csv");
        String format = (formatObj != null ? formatObj.toString() : "csv").toLowerCase(Locale.ROOT);
        
        // Validation
        if (bucket == null || bucket.isEmpty()) throw new IllegalArgumentException("S3 source requires 'bucket'");
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("S3 source requires 'key' (object key or prefix)");
        if (accessKey == null || secretKey == null) {
            throw new IllegalArgumentException("S3 source requires 'access_key' and 'secret_key'");
        }
        
        // Build S3 client
        S3Client s3Client = buildS3Client(endpoint, accessKey, secretKey, region);
        
        try {
            // Check if key is a prefix (multiple objects) or single object
            if (isPrefix(s3Client, bucket, key)) {
                // List and process multiple objects
                processObjectsByPrefix(s3Client, bucket, key, format, rowConsumer);
            } else {
                // Single object
                processSingleObject(s3Client, bucket, key, format, rowConsumer);
            }
        } finally {
            s3Client.close();
        }
    }
    
    /**
     * Build S3Client with optional endpoint customization for MinIO/local storage.
     */
    private S3Client buildS3Client(String endpoint, String accessKey, String secretKey, String region) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(region));
        
        // For MinIO or other S3-compatible storage, set custom endpoint
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(true);  // Required for MinIO
        }
        
        return builder.build();
    }
    
    /**
     * Check if the given key represents a prefix (multiple objects) or a single object.
     * Heuristic: if key ends with "/" or we can't get it as a single object, it's a prefix.
     */
    private boolean isPrefix(S3Client s3Client, String bucket, String key) {
        try {
            // Try to get as single object
            GetObjectRequest req = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.getObject(req).close();
            return false;  // It's a single object
        } catch (Exception e) {
            return true;  // Treat as prefix
        }
    }
    
    /**
     * Process a single S3 object.
     */
    private void processSingleObject(S3Client s3Client, String bucket, String key, String format,
                                     Consumer<Map<String, Object>> rowConsumer) throws Exception {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(req)) {
            if ("csv".equalsIgnoreCase(format)) {
                parseCSV(response, rowConsumer);
            } else if ("json".equalsIgnoreCase(format)) {
                parseJSON(response, rowConsumer);
            } else if ("jsonl".equalsIgnoreCase(format)) {
                parseJSONL(response, rowConsumer);
            } else {
                throw new IllegalArgumentException("Unsupported format: " + format);
            }
        }
    }
    
    /**
     * Process multiple S3 objects by prefix.
     */
    private void processObjectsByPrefix(S3Client s3Client, String bucket, String prefix, String format,
                                        Consumer<Map<String, Object>> rowConsumer) throws Exception {
        ListObjectsV2Request req = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();
        
        var response = s3Client.listObjectsV2(req);
        
        if (response.contents() != null) {
            for (S3Object obj : response.contents()) {
                String objectKey = obj.key();
                // Skip directories
                if (!objectKey.endsWith("/")) {
                    processSingleObject(s3Client, bucket, objectKey, format, rowConsumer);
                }
            }
        }
    }
    
    /**
     * Parse CSV format from input stream.
     */
    private void parseCSV(InputStream input, Consumer<Map<String, Object>> rowConsumer) throws Exception {
        String encoding = "UTF-8";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, Charset.forName(encoding)));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            
            Map<String, Integer> headers = csvParser.getHeaderMap();
            for (CSVRecord record : csvParser) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String header : headers.keySet()) {
                    row.put(header, record.get(header));
                }
                rowConsumer.accept(row);
            }
        }
    }
    
    /**
     * Parse JSON array format from input stream.
     * Expects: [{"field": "value"}, {"field": "value"}, ...]
     */
    @SuppressWarnings("unchecked")
    private void parseJSON(InputStream input, Consumer<Map<String, Object>> rowConsumer) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(java.util.stream.Collectors.joining());
            Object parsed = objectMapper.readValue(content, Object.class);
            
            if (parsed instanceof List) {
                for (Object item : (List<?>) parsed) {
                    if (item instanceof Map) {
                        rowConsumer.accept((Map<String, Object>) item);
                    }
                }
            } else {
                throw new IllegalArgumentException("JSON must be an array of objects");
            }
        }
    }
    
    /**
     * Parse JSONL (JSON Lines) format from input stream.
     * Expects: {"field": "value"}\n{"field": "value"}\n...
     */
    @SuppressWarnings("unchecked")
    private void parseJSONL(InputStream input, Consumer<Map<String, Object>> rowConsumer) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    Object parsed = objectMapper.readValue(line, Object.class);
                    if (parsed instanceof Map) {
                        rowConsumer.accept((Map<String, Object>) parsed);
                    }
                }
            }
        }
    }
}
