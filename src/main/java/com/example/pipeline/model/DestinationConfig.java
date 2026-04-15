package com.example.pipeline.model;

import java.io.Serializable;
import java.util.Map;

public class DestinationConfig implements Serializable {
    private String type;
    private Map<String, Object> config;
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }
}
