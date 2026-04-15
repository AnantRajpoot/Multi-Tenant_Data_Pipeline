package com.example.pipeline.model;

import lombok.Data;
import java.util.Map;

@Data
public class SourceConfig {
    private String type;
    private Map<String, Object> config;
    private Map<String, String> schema;
}
