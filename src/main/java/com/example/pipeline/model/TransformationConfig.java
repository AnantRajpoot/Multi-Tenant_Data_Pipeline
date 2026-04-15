package com.example.pipeline.model;

import lombok.Data;
import java.util.Map;

@Data
public class TransformationConfig {
    private String type;
    private Map<String, Object> config;
}
