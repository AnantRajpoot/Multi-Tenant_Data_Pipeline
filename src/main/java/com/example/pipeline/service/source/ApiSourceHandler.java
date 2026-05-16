package com.example.pipeline.service.source;

import com.example.pipeline.model.Pipeline;
import com.example.pipeline.model.SourceConfig;
import com.example.pipeline.util.EnvVarResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.function.Consumer;

/**
 * REST API source handler.
 */
@Component
public class ApiSourceHandler implements SourceHandler {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ApiSourceHandler(RestTemplate restTemplate) {
        /**
         * Constructor for ApiSourceHandler.
         * @param restTemplate the RestTemplate to use for HTTP requests
         */
        this.restTemplate = restTemplate;
    }

    @Override
    public String getType() {
        /**
         * Returns the type of source handled by this class.
         * @return the string "api"
         */
        return "api";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void fetchRowsAndProcess(Pipeline pipeline, Consumer<Map<String, Object>> rowConsumer) throws Exception {
        /**
         * Fetches rows from a REST API source and processes each row using the provided consumer.
         * Handles pagination, retries, and JSON extraction.
         *
         * @param pipeline the pipeline configuration containing the source
         * @param rowConsumer a consumer to process each row as a Map
         * @throws Exception if an error occurs during fetching or processing
         */
        SourceConfig src = pipeline.getSource();
        Map<String,Object> rawConfig = src.getConfig();
        Map<String,Object> config = EnvVarResolver.resolveMap(rawConfig);

        // Extract required and optional configuration values
        String url = (String) config.get("url");
        if (url == null) throw new IllegalArgumentException("API source requires 'url' in config");

        String method = ((String) config.getOrDefault("method", "GET")).toUpperCase(Locale.ROOT);
        Map<String, String> headers = (Map<String, String>) config.getOrDefault("headers", Collections.emptyMap());
        Map<String, Object> params = (Map<String, Object>) config.getOrDefault("params", Collections.emptyMap());
        Map<String, Object> pagination = (Map<String, Object>) config.get("pagination");
        String jsonPath = (String) config.getOrDefault("json_path", "$");
        Map<String, Object> retryConfig = (Map<String, Object>) config.getOrDefault("retry", Collections.emptyMap());
        int maxAttempts = ((Number) retryConfig.getOrDefault("max_attempts", 1)).intValue();

        int maxPages = 1;
        if (pagination != null) {
            maxPages = ((Number) pagination.getOrDefault("max_pages", 1)).intValue();
        }

        int pageIndex = 0;
        int pagesFetched = 0;

        // Main pagination loop
        while (true) {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
            if (params != null) {
                for (Map.Entry<String,Object> p : params.entrySet()) {
                    uriBuilder.queryParam(p.getKey(), String.valueOf(p.getValue()));
                }
            }

            // Handle pagination parameters if present
            if (pagination != null) {
                String pType = (String) pagination.getOrDefault("type", "offset");
                if ("offset".equalsIgnoreCase(pType)) {
                    String offsetParam = (String) pagination.getOrDefault("offset_param", "offset");
                    String limitParam = (String) pagination.getOrDefault("limit_param", "limit");
                    int limit = ((Number) pagination.getOrDefault("limit", 1000)).intValue();
                    int offset = pageIndex * limit;
                    uriBuilder.replaceQueryParam(offsetParam, offset);
                    uriBuilder.replaceQueryParam(limitParam, limit);
                } else if ("page".equalsIgnoreCase(pType)) {
                    String pageParam = (String) pagination.getOrDefault("page_param", "page");
                    String sizeParam = (String) pagination.getOrDefault("size_param", "size");
                    int size = ((Number) pagination.getOrDefault("size", 1000)).intValue();
                    int page = pageIndex + 1;
                    uriBuilder.replaceQueryParam(pageParam, page);
                    uriBuilder.replaceQueryParam(sizeParam, size);
                }
            }

            String finalUrl = uriBuilder.build(true).toUriString();

            HttpHeaders httpHeaders = new HttpHeaders();
            headers.forEach(httpHeaders::set);
            HttpEntity<?> httpEntity = new HttpEntity<>(httpHeaders);

            // Retry logic for API call
            ResponseEntity<String> response = null;
            int attempt = 0;
            long backoffMillis = 500L;
            while (attempt < maxAttempts) {
                try {
                    if ("POST".equalsIgnoreCase(method)) {
                        response = restTemplate.exchange(finalUrl, HttpMethod.POST, httpEntity, String.class);
                    } else {
                        response = restTemplate.exchange(finalUrl, HttpMethod.GET, httpEntity, String.class);
                    }
                    break;
                } catch (RestClientException ex) {
                    attempt++;
                    if (attempt >= maxAttempts) throw ex;
                    Thread.sleep(backoffMillis);
                    backoffMillis *= 2;
                }
            }

            if (response == null) break;
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("API returned non-2xx: " + response.getStatusCode());
            }

            String body = response.getBody();
            if (body == null || body.trim().isEmpty()) break;

            // Extract data using JSONPath or fallback to full body
            Object extracted;
            try {
                extracted = JsonPath.parse(body).read(jsonPath);
            } catch (Exception e) {
                extracted = objectMapper.readValue(body, Object.class);
            }

            List<Object> items = new ArrayList<>();
            if (extracted instanceof List) items.addAll((List<?>) extracted);
            else items.add(extracted);

            // Process each item as a row
            for (Object item : items) {
                if (item == null) continue;
                Map<String,Object> row;
                if (item instanceof Map) {
                    row = objectMapper.convertValue(item, Map.class);
                } else {
                    row = new LinkedHashMap<>();
                    row.put("value", item);
                }
                rowConsumer.accept(row);
            }

            pagesFetched++;
            pageIndex++;
            if (pagination == null) break;
            if (pagesFetched >= maxPages) break;
        }
    } 
}

